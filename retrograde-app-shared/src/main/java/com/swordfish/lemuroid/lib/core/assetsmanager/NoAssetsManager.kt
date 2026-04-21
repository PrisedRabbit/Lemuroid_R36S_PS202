package com.swordfish.lemuroid.lib.core.assetsmanager

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.storage.DirectoriesManager

class NoAssetsManager : CoreID.AssetsManager {

    override suspend fun clearAssets(directoriesManager: DirectoriesManager) {}

    override suspend fun retrieveAssetsIfNeeded(
        directoriesManager: DirectoriesManager,
        sharedPreferences: SharedPreferences
    ) {
    }
}
