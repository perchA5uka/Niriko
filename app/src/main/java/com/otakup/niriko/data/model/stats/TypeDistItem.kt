package com.otakup.niriko.data.model.stats

import com.otakup.niriko.data.model.SubjectType

/** 类型分布条目。 */
data class TypeDistItem(val type: SubjectType, val count: Int, val percentage: Float)
