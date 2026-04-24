package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SavesManager(private val directoriesManager: DirectoriesManager) {

    suspend fun getSaveRAM(game: Game): ByteArray? = withContext(Dispatchers.IO) {
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val saveFile = getSaveFile(game, getSaveRAMFileName(game))
            if (saveFile.exists() && saveFile.length() > 0) {
                return@runCatchingWithRetry saveFile.readBytes()
            }

            val legacySaveFile = getLegacySaveFile(getSaveRAMFileName(game))
            if (legacySaveFile.exists() && legacySaveFile.length() > 0) {
                return@runCatchingWithRetry legacySaveFile.readBytes()
            }

            null
        }.getOrNull()
    }

    suspend fun setSaveRAM(game: Game, data: ByteArray): Unit = withContext(Dispatchers.IO) {
        val result = runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            if (data.isEmpty())
                return@runCatchingWithRetry

            val saveFile = getSaveFile(game, getSaveRAMFileName(game))
            saveFile.writeBytes(data)
        }
        result.getOrNull()
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo = withContext(Dispatchers.IO) {
        val saveFile = getSaveFile(game, getSaveRAMFileName(game))
        val fileExists = saveFile.exists() && saveFile.length() > 0
        if (fileExists) {
            SaveInfo(true, saveFile.lastModified())
        } else {
            val legacySaveFile = getLegacySaveFile(getSaveRAMFileName(game))
            SaveInfo(legacySaveFile.exists() && legacySaveFile.length() > 0, legacySaveFile.lastModified())
        }
    }

    private suspend fun getSaveFile(game: Game, fileName: String): File = withContext(Dispatchers.IO) {
        val savesDirectory = directoriesManager.getGameSavesDirectory(game)
        File(savesDirectory, fileName)
    }

    private fun getLegacySaveFile(fileName: String): File {
        val savesDirectory = directoriesManager.getSavesDirectory()
        return File(savesDirectory, fileName)
    }

    /** This name should make it compatible with RetroArch so that users can freely sync saves across the two application. */
    private fun getSaveRAMFileName(game: Game) = "${game.fileName.substringBeforeLast(".")}.srm"

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
