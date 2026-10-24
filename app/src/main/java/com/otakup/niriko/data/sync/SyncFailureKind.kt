package com.otakup.niriko.data.sync

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 同步失败种类（A-D5）。
 *
 * 改造前 WebDAV 的 401 / 5xx / 超时一律被折成 null 或 false，调用方无法区分
 * 「远端没有数据」和「登录失效」——用户只看到「点了没反应」。这里把失败原因定型，
 * 由 SyncManager 翻译成人话，UI 按种类分组展示（设置页 Snackbar）。
 *
 * 纯 Kotlin，不依赖 Android / OkHttp，便于 JVM 单测。
 */
enum class SyncFailureKind {
    /** 401 / 403：账号密码错误，或账号没有该目录的读写权限。 */
    AUTH,

    /** 404：远端资源不存在。下载侧要把它当「远端无数据」而不是失败。 */
    NOT_FOUND,

    /** 其余 4xx（409 / 412 / 423 …）。 */
    HTTP_4XX,

    /** 5xx：服务端临时故障。 */
    HTTP_5XX,

    /** 连接 / 读取超时。 */
    TIMEOUT,

    /** 断网、DNS 失败、连接被拒。 */
    NETWORK,

    /** 拿到响应但内容不是可解析的同步 JSON。 */
    PARSE,

    /** 条件写入冲突（412 或 HEAD 比对发现远端已变）。**不能**降级成普通 4xx。 */
    CONFLICT,

    /** 其他（含证书、TLS、未知异常）。 */
    UNKNOWN,
}

/**
 * WebDAV 操作结果。
 *
 * 与「null = 什么都没拿到」不同：这里把「远端确实没有文件」（[NotFound]）和
 * 「失败了，且知道为什么」（[Failure]）分开，避免 401 被当成「远端为空」而提示成功。
 */
sealed interface WebDavResult<out T> {
    data class Ok<T>(val value: T) : WebDavResult<T>

    /** 404：文件/目录不存在。语义上不是错误。 */
    data object NotFound : WebDavResult<Nothing>

    data class Failure(
        val kind: SyncFailureKind,
        /** HTTP 状态码；纯网络异常时为 null。 */
        val code: Int? = null,
        /** 仅用于日志排查，**不**直接展示给用户。 */
        val detail: String? = null,
    ) : WebDavResult<Nothing>
}

/** 状态码 / 异常 → 失败种类的纯映射。 */
object SyncFailureClassifier {

    /** 2xx 返回 null（成功）；其余按语义分组。 */
    fun classify(code: Int): SyncFailureKind? = when {
        code in 200..299 -> null
        code == 401 || code == 403 -> SyncFailureKind.AUTH
        code == 404 -> SyncFailureKind.NOT_FOUND
        code in 400..499 -> SyncFailureKind.HTTP_4XX
        code in 500..599 -> SyncFailureKind.HTTP_5XX
        else -> SyncFailureKind.UNKNOWN
    }

    fun classifyException(t: Throwable): SyncFailureKind = when (t) {
        // 顺序有意义：SocketTimeoutException / UnknownHostException 都是 IOException 的子类
        is SocketTimeoutException -> SyncFailureKind.TIMEOUT
        is InterruptedIOException -> SyncFailureKind.TIMEOUT
        is UnknownHostException -> SyncFailureKind.NETWORK
        is IOException -> SyncFailureKind.NETWORK
        else -> SyncFailureKind.UNKNOWN
    }
}

/**
 * 各 WebDAV 方法的成功码白名单。
 *
 * 抽成纯函数是为了单测「MKCOL 405 = 目录已存在」这类特例——
 * 旧实现把它写死在 OkHttp 回调里，只能连服务器才能验证。
 */
object WebDavStatusPolicy {

    /** MKCOL：2xx 或 405（目录已存在）。 */
    fun mkcolOk(code: Int): Boolean = code in 200..299 || code == 405

    /** PUT：只有 2xx；412 由调用方单独当冲突处理。 */
    fun putOk(code: Int): Boolean = code in 200..299

    /** DELETE：2xx 或 404（本来就不存在，幂等）。 */
    fun deleteOk(code: Int): Boolean = code in 200..299 || code == 404

    /** GET / HEAD：只有 2xx；404 由调用方映射成 [WebDavResult.NotFound]。 */
    fun readOk(code: Int): Boolean = code in 200..299
}

/** 失败种类的用户文案。**不**输出 null、异常类名或堆栈片段。 */
object SyncFailureMessages {

    fun forKind(kind: SyncFailureKind, code: Int? = null): String = when (kind) {
        SyncFailureKind.AUTH ->
            if (code == 403) "没有访问权限（403），请确认账号可读写该目录"
            else "登录已失效（401），请检查用户名与密码"
        SyncFailureKind.NOT_FOUND -> "远程文件不存在" + suffix(code)
        SyncFailureKind.HTTP_4XX -> "服务器拒绝了请求" + suffix(code)
        SyncFailureKind.HTTP_5XX -> "服务器错误" + suffix(code) + "，请稍后重试"
        SyncFailureKind.TIMEOUT -> "连接超时（30 秒）"
        SyncFailureKind.NETWORK -> "网络不可达，请检查服务器地址"
        SyncFailureKind.PARSE -> "同步数据格式错误，请检查远端 sync.json"
        SyncFailureKind.CONFLICT -> "云端在你上次同步后已更新"
        SyncFailureKind.UNKNOWN -> "同步失败" + suffix(code) + "，请稍后重试"
    }

    fun forFailure(failure: WebDavResult.Failure): String = forKind(failure.kind, failure.code)

    /** 创建远程目录失败（MKCOL）。 */
    fun forMkcol(failure: WebDavResult.Failure): String =
        "无法创建远程目录" + suffix(failure.code) + "：" + forKind(failure.kind, failure.code)

    /** 状态码后缀；null 时返回空串（绝不拼接 "null"）。 */
    fun suffix(code: Int?): String = if (code == null) "" else "（$code）"
}