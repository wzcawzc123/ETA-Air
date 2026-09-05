package io.github.mangi.eta.agent.model

import java.io.IOException

/**
 * 模型接口返回非 2xx 状态时的类型化异常。
 *
 * 保留与历史一致的可读文案（"模型接口返回 HTTP <code>：<body>"），同时额外携带
 * [statusCode]，便于上层区分"瞬时服务端错误（5xx，可安全重试）"与
 * "客户端/配置错误（4xx，不可重试）"。
 */
internal class ProviderHttpException(
    val statusCode: Int,
    body: String,
) : RuntimeException("模型接口返回 HTTP $statusCode：$body")

/**
 * 瞬时错误判定：仅在服务端/网络层可安全重试的错误上返回 true。
 *
 * 覆盖所有 Provider 与传输层：
 * - [IOException] 及其全部子类：流被重置（stream was reset）、读超时、连接失败、
 *   域名解析失败、TLS 握手/重置等。Java 网络异常都继承自 IOException，因此一次
 *   [IOException] 检查即可覆盖，无需逐一列举。
 * - [ProviderHttpException] 且状态码为 5xx：模型服务端/上游瞬时故障。
 *
 * 明确不重试：4xx（配置/鉴权/参数错误）、content_filter/拒答、空 content、逻辑异常。
 */
internal object AgentTransientError {

    fun isTransient(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is IOException) return true
        if (throwable is ProviderHttpException) return throwable.statusCode in 500..599
        return false
    }
}
