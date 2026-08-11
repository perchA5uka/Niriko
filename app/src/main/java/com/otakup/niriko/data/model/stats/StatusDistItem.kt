package com.otakup.niriko.data.model.stats

import com.otakup.niriko.data.model.WatchStatus

/** 状态分布条目。 */
data class StatusDistItem(val status: WatchStatus, val count: Int, val percentage: Float)
