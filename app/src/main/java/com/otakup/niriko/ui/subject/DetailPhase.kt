package com.otakup.niriko.ui.subject

// 详情页三个阶段。与 SubjectDetailScreen 内 when 的分支顺序严格一致：
// Loading > Error > Ready —— error 与 subject 可以同时为真（有旧内容 + 刷新失败），那时必须
// 停在错误分支之外、不能把正文换成错误页。抽成纯函数是为了让 JVM 单测钉住这个优先级，
// 以后有人加过渡/改分支顺序时会立刻被测出来。
enum class DetailPhase { Loading, Error, Ready }

fun detailPhaseOf(isLoading: Boolean, hasError: Boolean, hasSubject: Boolean): DetailPhase = when {
    isLoading -> DetailPhase.Loading

    hasError -> DetailPhase.Error

    hasSubject -> DetailPhase.Ready

    // 三者皆假时原 when 不渲染任何内容；归到 Loading 只是给状态机一个确定值，渲染仍走原 when。
    else -> DetailPhase.Loading
}
