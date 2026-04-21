package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import androidx.lifecycle.LiveData

class PendingOperationsMonitor(private val appContext: Context) {

    fun anyOperationInProgress(): LiveData<Boolean> {
        return LibraryIndexScheduler.isSyncInProgress()
    }

    fun anyLibraryOperationInProgress(): LiveData<Boolean> {
        return LibraryIndexScheduler.isSyncInProgress()
    }

    fun isDirectoryScanInProgress(): LiveData<Boolean> {
        return LibraryIndexScheduler.isSyncInProgress()
    }
}
