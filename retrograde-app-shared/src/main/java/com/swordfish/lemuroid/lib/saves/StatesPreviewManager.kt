package com.swordfish.lemuroid.lib.saves

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatesPreviewManager(private val directoriesManager: DirectoriesManager) {

    suspend fun getPreviewForSlot(
        game: Game,
        coreID: CoreID,
        index: Int,
        size: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val screenshotName = getSlotScreenshotName(game, index)
        val file = getPreviewFile(game, screenshotName, coreID.coreName)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap != null) {
            return@withContext ThumbnailUtils.extractThumbnail(bitmap, size, size)
        }

        val romRelativeFile = getRomRelativePreviewFile(game, screenshotName, coreID.coreName)
        val romRelativeBitmap = romRelativeFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
        if (romRelativeBitmap != null) {
            return@withContext ThumbnailUtils.extractThumbnail(romRelativeBitmap, size, size)
        }

        val legacyFile = getLegacyPreviewFile(screenshotName, coreID.coreName)
        val legacyBitmap = BitmapFactory.decodeFile(legacyFile.absolutePath)
        if (legacyBitmap != null) {
            return@withContext ThumbnailUtils.extractThumbnail(legacyBitmap, size, size)
        }

        null
    }

    suspend fun setPreviewForSlot(
        game: Game,
        bitmap: Bitmap,
        coreID: CoreID,
        index: Int
    ) = withContext(Dispatchers.IO) {
        val screenshotName = getSlotScreenshotName(game, index)
        val file = getWritablePreviewFile(game, screenshotName, coreID.coreName)
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
    }

    private fun getPreviewFile(game: Game, fileName: String, coreName: String): File {
        val statesDirectories = File(getWritableGameStatesPreviewDirectory(game), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getWritablePreviewFile(game: Game, fileName: String, coreName: String): File {
        val statesDirectories = File(getWritableGameStatesPreviewDirectory(game), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getRomRelativePreviewFile(game: Game, fileName: String, coreName: String): File? {
        val romRelativePreviewDirectory = getRomRelativeGameStatesPreviewDirectory(game) ?: return null
        val statesDirectories = File(romRelativePreviewDirectory, coreName)
        return File(statesDirectories, fileName)
    }

    private fun getLegacyPreviewFile(fileName: String, coreName: String): File {
        val statesDirectories = File(directoriesManager.getStatesPreviewDirectory(), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getWritableGameStatesPreviewDirectory(game: Game): File {
        return File(directoriesManager.getWritableGameSavesDirectory(game), "state-previews").apply {
            mkdirs()
        }
    }

    private fun getRomRelativeGameStatesPreviewDirectory(game: Game): File? {
        val romRelativeSavesDirectory = directoriesManager.getRomRelativeGameSavesDirectory(game) ?: return null
        return File(romRelativeSavesDirectory, "state-previews")
    }

    private fun getSlotScreenshotName(
        game: Game,
        index: Int
    ) = "${game.fileName}.slot${index + 1}.jpg"

    companion object {
        val PREVIEW_SIZE_DP = 96f
    }
}
