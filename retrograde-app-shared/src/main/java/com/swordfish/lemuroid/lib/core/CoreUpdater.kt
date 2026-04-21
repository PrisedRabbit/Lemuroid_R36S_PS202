package com.swordfish.lemuroid.lib.core

import android.content.Context
import com.swordfish.lemuroid.lib.library.CoreID

interface CoreUpdater {

    suspend fun downloadCores(context: Context, coreIDs: List<CoreID>)
}
