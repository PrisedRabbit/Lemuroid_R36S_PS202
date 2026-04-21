package com.swordfish.lemuroid.app.mobile.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsViewModel(
    context: Context,
    directoriesManager: DirectoriesManager
) : ViewModel() {

    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(context, DirectoriesManager(context.applicationContext)) as T
        }
    }

    private val legacyPreferences = SharedPreferencesHelper.getLegacySharedPreferences(context)
    private val currentFolderKey = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)
    private val fallbackFolder = directoriesManager.getInternalRomsDirectory().absolutePath

    val currentFolder = MutableStateFlow(
        legacyPreferences.getString(currentFolderKey, null) ?: fallbackFolder
    )

    val indexingInProgress = PendingOperationsMonitor(context).anyLibraryOperationInProgress()

    val directoryScanInProgress = PendingOperationsMonitor(context).isDirectoryScanInProgress()

    fun refreshCurrentFolder() {
        currentFolder.value = legacyPreferences.getString(currentFolderKey, null) ?: fallbackFolder
    }
}
