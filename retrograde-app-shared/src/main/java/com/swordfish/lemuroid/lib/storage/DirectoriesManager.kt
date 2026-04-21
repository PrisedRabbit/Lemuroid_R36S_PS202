package com.swordfish.lemuroid.lib.storage

import android.content.Context
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

    fun getInternalRomsDirectory(): File = File(externalAppDir(), "roms").apply {
        mkdirs()
    }
}
