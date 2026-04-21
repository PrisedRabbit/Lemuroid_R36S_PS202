package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.swordfish.lemuroid.app.LemuroidApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

object LibraryIndexScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inProgress = MutableLiveData(false)
    private var currentJob: Job? = null

    fun scheduleLibrarySync(applicationContext: Context) {
        cancelLibrarySync(applicationContext)

        currentJob = scope.launch {
            inProgress.postValue(true)
            try {
                val app = applicationContext.applicationContext as LemuroidApplication
                app.appComponent.lemuroidLibrary().indexLibrary()
            } catch (e: CancellationException) {
                Timber.i("Library indexing cancelled")
            } catch (e: Throwable) {
                Timber.e(e, "Library indexing failed")
            } finally {
                inProgress.postValue(false)
            }
        }
    }

    fun cancelLibrarySync(applicationContext: Context) {
        currentJob?.cancel()
        currentJob = null
        inProgress.postValue(false)
    }

    fun isSyncInProgress(): LiveData<Boolean> = inProgress
}
