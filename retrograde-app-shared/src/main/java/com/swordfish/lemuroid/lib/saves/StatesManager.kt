package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.readBytesUncompressed
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.writeBytesCompressed
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

// TODO Since states are core related we should not put them in the same folder. This break previous versions states
// so I decided to manage a transition phase reading also the old directory. We should safely remove it in a few weeks.

class StatesManager(private val directoriesManager: DirectoriesManager) {

    suspend fun getSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int
    ): SaveState? = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        getSaveState(game, getSlotSaveFileName(game, index), coreID.coreName)
    }

    suspend fun setSlotSave(
        game: Game,
        saveState: SaveState,
        coreID: CoreID,
        index: Int
    ) = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        setSaveState(game, getSlotSaveFileName(game, index), coreID.coreName, saveState)
    }

    suspend fun getAutoSaveInfo(
        game: Game,
        coreID: CoreID
    ): SaveInfo = withContext(Dispatchers.IO) {
        val autoSaveFile = getStateFileOrDeprecated(game, getAutoSaveFileName(game), coreID.coreName)
        SaveInfo(autoSaveFile.exists() && autoSaveFile.length() > 0, autoSaveFile.lastModified())
    }

    suspend fun getAutoSave(game: Game, coreID: CoreID) = withContext(Dispatchers.IO) {
        getSaveState(game, getAutoSaveFileName(game), coreID.coreName)
    }

    suspend fun setAutoSave(
        game: Game,
        coreID: CoreID,
        saveState: SaveState
    ) = withContext(Dispatchers.IO) {
        setSaveState(game, getAutoSaveFileName(game), coreID.coreName, saveState)
    }

    suspend fun getSavedSlotsInfo(
        game: Game,
        coreID: CoreID
    ): List<SaveInfo> = withContext(Dispatchers.IO) {
        (0 until MAX_STATES)
            .map { getStateFileOrDeprecated(game, getSlotSaveFileName(game, it), coreID.coreName) }
            .map { SaveInfo(it.exists(), it.lastModified()) }
            .toList()
    }

    private suspend fun getSaveState(
        game: Game,
        fileName: String,
        coreName: String
    ): SaveState? {
        return runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val saveFile = getStateFileOrDeprecated(game, fileName, coreName)
            if (saveFile.exists()) {
                val byteArray = saveFile.readBytesUncompressed()
                val metadataFile = File(saveFile.parentFile, "$fileName.metadata")
                val stateMetadata = runCatching {
                    Json.Default.decodeFromString(
                        SaveState.Metadata.serializer(),
                        metadataFile.readText()
                    )
                }
                SaveState(byteArray, stateMetadata.getOrNull() ?: SaveState.Metadata())
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun setSaveState(
        game: Game,
        fileName: String,
        coreName: String,
        saveState: SaveState
    ) {
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            writeStateToDisk(game, fileName, coreName, saveState.state)
            writeMetadataToDisk(game, fileName, coreName, saveState.metadata)
        }
    }

    private fun writeMetadataToDisk(
        game: Game,
        fileName: String,
        coreName: String,
        metadata: SaveState.Metadata
    ) {
        val metadataFile = getMetadataStateFile(game, fileName, coreName)
        metadataFile.writeText(Json.encodeToString(SaveState.Metadata.serializer(), metadata))
    }

    private fun writeStateToDisk(
        game: Game,
        fileName: String,
        coreName: String,
        stateArray: ByteArray
    ) {
        val saveFile = getWritableStateFile(game, fileName, coreName)
        saveFile.writeBytesCompressed(stateArray)
    }

    private fun getStateFileOrDeprecated(game: Game, fileName: String, coreName: String): File {
        val stateFile = getWritableStateFile(game, fileName, coreName)
        val romRelativeStateFile = getRomRelativeStateFile(game, fileName, coreName)
        val legacyStateFile = getLegacyStateFile(fileName, coreName)
        val deprecatedStateFile = getDeprecatedStateFile(fileName)
        return if (stateFile.exists()) {
            stateFile
        } else if (romRelativeStateFile?.exists() == true) {
            romRelativeStateFile
        } else if (legacyStateFile.exists()) {
            legacyStateFile
        } else if (deprecatedStateFile.exists()) {
            deprecatedStateFile
        } else {
            stateFile
        }
    }

    private fun getWritableStateFile(game: Game, fileName: String, coreName: String): File {
        val statesDirectories = File(getWritableGameStatesDirectory(game), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getRomRelativeStateFile(game: Game, fileName: String, coreName: String): File? {
        val romRelativeStatesDirectory = getRomRelativeGameStatesDirectory(game) ?: return null
        val statesDirectories = File(romRelativeStatesDirectory, coreName)
        return File(statesDirectories, fileName)
    }

    private fun getMetadataStateFile(
        game: Game,
        stateFileName: String,
        coreName: String
    ): File {
        val statesDirectories = File(getWritableGameStatesDirectory(game), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, "$stateFileName.metadata")
    }

    @Deprecated("Using this folder collisions might happen across different systems.")
    private fun getDeprecatedStateFile(fileName: String): File {
        val statesDirectories = directoriesManager.getInternalStatesDirectory()
        return File(statesDirectories, fileName)
    }

    private fun getLegacyStateFile(fileName: String, coreName: String): File {
        val statesDirectories = File(directoriesManager.getStatesDirectory(), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getWritableGameStatesDirectory(game: Game): File {
        return File(directoriesManager.getWritableGameSavesDirectory(game), "states").apply {
            mkdirs()
        }
    }

    private fun getRomRelativeGameStatesDirectory(game: Game): File? {
        val romRelativeSavesDirectory = directoriesManager.getRomRelativeGameSavesDirectory(game) ?: return null
        return File(romRelativeSavesDirectory, "states")
    }

    private fun getAutoSaveFileName(game: Game) = "${game.fileName}.state"
    private fun getSlotSaveFileName(game: Game, index: Int) = "${game.fileName}.slot${index + 1}"

    companion object {
        const val MAX_STATES = 4
        private const val FILE_ACCESS_RETRIES = 3
    }
}
