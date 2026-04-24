package com.swordfish.lemuroid.lib.storage

import android.net.Uri
import android.content.Context
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.io.File

class DirectoriesManager(private val appContext: Context) {

    private fun externalAppDir(): File = appContext.getExternalFilesDir(null) ?: appContext.filesDir

    @Deprecated("Use the external states directory")
    fun getInternalStatesDirectory(): File = File(appContext.filesDir, "states").apply {
        mkdirs()
    }

    fun getCoresDirectory(): File = File(appContext.filesDir, "cores").apply {
        mkdirs()
    }

    fun getSystemDirectory(): File = File(appContext.filesDir, "system").apply {
        mkdirs()
    }

    fun getStatesDirectory(): File = File(externalAppDir(), "states").apply {
        mkdirs()
    }

    fun getStatesPreviewDirectory(): File = File(externalAppDir(), "state-previews").apply {
        mkdirs()
    }

    fun getSavesDirectory(): File = File(externalAppDir(), "saves").apply {
        mkdirs()
    }

    fun getGameSavesDirectory(game: Game): File {
        val romDirectory = getRomDirectory(game) ?: return getSavesDirectory()
        return File(romDirectory, "saves").apply {
            mkdirs()
        }
    }

    fun getInternalRomsDirectory(): File = File(externalAppDir(), "roms").apply {
        mkdirs()
    }

    private fun getRomDirectory(game: Game): File? {
        val romPath = Uri.parse(game.fileUri).path ?: return null
        return File(romPath).parentFile
    }

    fun getRomRelativeGameSavesDirectory(game: Game): File? {
        val romDirectory = getRomDirectory(game) ?: return null
        return File(romDirectory, "saves")
    }

    fun getWritableGameSavesDirectory(game: Game): File {
        val romRelativeDirectory = getRomRelativeGameSavesDirectory(game)
        if (romRelativeDirectory != null && ensureDirectoryWritable(romRelativeDirectory)) {
            return romRelativeDirectory
        }

        return getSavesDirectory()
    }

    private fun ensureDirectoryWritable(directory: File): Boolean {
        if (directory.exists()) {
            return directory.isDirectory && directory.canWrite()
        }

        val parentDirectory = directory.parentFile ?: return false
        if (!parentDirectory.exists() || !parentDirectory.canWrite()) {
            return false
        }

        return directory.mkdirs() && directory.canWrite()
    }
}
