package io.github.mangi.eta.agent.model

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.LongBuffer

/**
 * 记忆向量嵌入（第 3 层）：用本地 ONNX 的 bge-small-zh-v1.5 把文本转成 512 维向量。
 * 全部"尽力而为"：模型缺失/加载失败/推理异常 都返回 available=false，调用方优雅退回关键词/字符逻辑，不影响记忆主链路。
 *
 * 模型与词表随 APK 打包在 assets/embedding/ 下（构建时固化，无需运行时联网）。
 */
internal class OnnxMemoryEmbedder private constructor(
    private val session: OrtSession,
    private val tokenizer: BertWordPieceTokenizer,
) {

    private val env = OrtEnvironment.getEnvironment()

    companion object {
        const val ASSET_DIR = "embedding"
        const val ASSET_MODEL = "embedding/model_quantized.onnx"
        const val ASSET_VOCAB = "embedding/vocab.txt"
        const val MAX_SEQ = 512

        @Volatile private var instance: OnnxMemoryEmbedder? = null
        @Volatile private var loadFailed = false

        suspend fun get(context: Context): OnnxMemoryEmbedder? {
            instance?.let { return it }
            if (loadFailed) return null
            return withContext(Dispatchers.IO) {
                runCatching {
                    val vocab = context.assets.open(ASSET_VOCAB).bufferedReader(Charsets.UTF_8).readLines()
                    val tokenizer = BertWordPieceTokenizer(vocab)
                    val modelBytes = context.assets.open(ASSET_MODEL).use { it.readBytes() }
                    val opts = OrtSession.SessionOptions()
                    val session = OrtEnvironment.getEnvironment().createSession(modelBytes, opts)
                    OnnxMemoryEmbedder(session, tokenizer).also { instance = it }
                }.getOrElse {
                    loadFailed = true
                    null
                }
            }
        }
    }

    /** 文本 → 512 维 L2 归一化向量（CLS 池化）。阻塞调用，应放在 IO 线程。session 为长驻单例，不关闭。 */
    fun embed(text: String): FloatArray? = runCatching {
        val t = tokenizer.encode(text.take(512))
        val shape = longArrayOf(1, MAX_SEQ.toLong())
        // 手动转 LongArray，避免个别 Kotlin/Gradle 组合下 IntArray.toLongArray() 扩展不可见。
        fun intsToLong(src: IntArray): LongArray = LongArray(src.size) { src[it].toLong() }
        val idTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(intsToLong(t.inputIds)), shape)
        val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(intsToLong(t.attentionMask)), shape)
        val typeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(intsToLong(t.tokenTypeIds)), shape)
        val inputs = mapOf(
            "input_ids" to idTensor,
            "attention_mask" to maskTensor,
            "token_type_ids" to typeTensor,
        )
        try {
            val outputs = session.run(inputs)
            // 用 Java Map.get + OnnxTensor.getFloatBuffer() 显式方法，绕开 Kotlin getValue 扩展 /
            // 合成属性(ftfloatBuffer) 在 onnxruntime 1.19.2 + Kotlin 2.4.10 下的互操作解析问题。
            val hidden = outputs.get("last_hidden_state") as OnnxTensor
            val fb = hidden.getFloatBuffer()
            val seqFlat = FloatArray(fb.remaining())
            fb.get(seqFlat)
            // CLS 池化：batch0 的 seq index0 → 前 512 个 float
            val cls = FloatArray(512)
            System.arraycopy(seqFlat, 0, cls, 0, 512)
            VectorMath.normalize(cls)
        } finally {
            idTensor.close()
            maskTensor.close()
            typeTensor.close()
        }
    }.getOrNull()

    /** 批量嵌入：包含 [texts] 原顺序，失败项为 null。 */
    fun embedAll(texts: List<String>): List<FloatArray?> = texts.map { embed(it) }
}
