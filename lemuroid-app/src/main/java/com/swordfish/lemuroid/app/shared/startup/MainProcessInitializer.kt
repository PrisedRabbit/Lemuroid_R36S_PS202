package com.swordfish.lemuroid.app.shared.startup

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.startup.Initializer
import androidx.work.WorkManagerInitializer
import com.swordfish.lemuroid.app.LemuroidApplication
import com.swordfish.lemuroid.lib.storage.StorageProviderRegistry
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class MainProcessInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Timber.i("Requested initialization of main process tasks")
        context.getSharedPreferences(StorageProviderRegistry.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("access_framework", false)
            .apply()

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove("external_folder")
            .apply()

        val app = context.applicationContext as LemuroidApplication
        runBlocking {
            val database = app.appComponent.retrogradeDatabase()
            database.dataFileDao().deleteByUriPrefix("content:%")
            database.gameDao().deleteByUriPrefix("content:%")
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(WorkManagerInitializer::class.java, DebugInitializer::class.java)
    }
}
