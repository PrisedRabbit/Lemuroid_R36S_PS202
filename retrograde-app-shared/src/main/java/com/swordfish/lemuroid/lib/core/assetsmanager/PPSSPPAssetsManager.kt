package com.swordfish.lemuroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PPSSPPAssetsManager : CoreID.AssetsManager {

    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {
        getAssetsDirectory(directoriesManager).deleteRecursively()
    }

    override suspend fun retrieveAssetsIfNeeded(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences
    ) {
        val directoryExists = getAssetsDirectory(directoriesManager).exists()
        val currentVersion = sharedPreferences.getString(PPSSPP_ASSETS_VERSION_KEY, "none")

        if (directoryExists && currentVersion == PPSSPP_ASSETS_VERSION) {
            return
        }

        getAssetsDirectory(directoriesManager).deleteRecursively()
        sharedPreferences.edit()
            .remove(PPSSPP_ASSETS_VERSION_KEY)
            .commit()
    }

    private suspend fun updatedRequested(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences
    ): Boolean = withContext(Dispatchers.IO) {
        val directoryExists = getAssetsDirectory(directoriesManager).exists()

        val currentVersion = sharedPreferences.getString(PPSSPP_ASSETS_VERSION_KEY, "none")
        val hasCurrentVersion = currentVersion == PPSSPP_ASSETS_VERSION

        !directoryExists || !hasCurrentVersion
    }

    private suspend fun getAssetsDirectory(directoriesManager: DirectoriesManager): File {
        return withContext(Dispatchers.IO) {
            File(directoriesManager.getSystemDirectory(), PPSSPP_ASSETS_FOLDER_NAME)
        }
    }

    companion object {
        const val PPSSPP_ASSETS_VERSION = "1.15"
        const val PPSSPP_ASSETS_VERSION_KEY = "ppsspp_assets_version_key"
        const val PPSSPP_ASSETS_FOLDER_NAME = "PPSSPP"
    }
}
