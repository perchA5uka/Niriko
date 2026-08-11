package com.otakup.niriko

import android.content.Context

/** 从任意 Context 取得 [NirikoApplication]。 */
val Context.nirikoApp: NirikoApplication
    get() = applicationContext as NirikoApplication
