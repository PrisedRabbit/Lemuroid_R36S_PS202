package com.swordfish.lemuroid.metadata.libretrodb

import com.swordfish.lemuroid.common.kotlin.filterNullable
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import android.net.Uri
import java.io.File
import java.util.Locale
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDBManager
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDatabase
import com.swordfish.lemuroid.metadata.libretrodb.db.entity.LibretroRom
import timber.log.Timber

class LibretroDBMetadataProvider(private val ovgdbManager: LibretroDBManager) :
    GameMetadataProvider {

    private val sortedSystemIds: List<String> by lazy {
        SystemID.values()
            .map { it.dbname }
            .sortedByDescending { it.length }
    }

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        val db = ovgdbManager.dbInstance

        Timber.d("Looking metadata for file: $storageFile")

        val metadata = runCatching {
                findByCRC(storageFile, db)
                ?: findBySerial(storageFile, db)
                ?: findByFilename(db, storageFile)
                ?: findByPathAndFilename(db, storageFile)
                ?: findByUniqueExtension(storageFile)
                ?: findByKnownSystem(storageFile)
                ?: findByPathAndSupportedExtension(storageFile)
        }.getOrElse {
            Timber.e("Error in retrieving $storageFile metadata: $it... Skipping.")
            null
        }

        metadata?.let { Timber.d("Metadata retrieved for item: $it") }

        return metadata
    }

    private fun convertToGameMetadata(rom: LibretroRom, storageFile: StorageFile): GameMetadata {
        return GameMetadata(
            name = rom.name,
            romName = rom.romName,
            thumbnail = findLocalCoverUrl(storageFile, rom.romName, rom.name),
            system = rom.system,
            developer = rom.developer
        )
    }

    private suspend fun findByFilename(db: LibretroDatabase, file: StorageFile): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { extractGameSystem(it).scanOptions.scanByFilename }
            ?.let { convertToGameMetadata(it, file) }
    }

    private suspend fun findByPathAndFilename(
        db: LibretroDatabase,
        file: StorageFile
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { extractGameSystem(it).scanOptions.scanByPathAndFilename }
            .filterNullable { parentContainsSystem(file.path, extractGameSystem(it).id.dbname) }
            ?.let { convertToGameMetadata(it, file) }
    }

    private fun findByPathAndSupportedExtension(file: StorageFile): GameMetadata? {
        val system = sortedSystemIds
            .filter { parentContainsSystem(file.path, it) }
            .map { GameSystem.findById(it) }
            .filter { it.scanOptions.scanByPathAndSupportedExtensions }
            .firstOrNull { it.supportedExtensions.contains(file.extension) }

        return system?.let {
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = findLocalCoverUrl(file, file.name, file.extensionlessName),
                system = it.id.dbname,
                developer = null
            )
        }
    }

    private fun parentContainsSystem(parent: String?, dbname: String): Boolean {
        return parent?.toLowerCase(Locale.getDefault())?.contains(dbname) == true
    }

    private suspend fun findByCRC(file: StorageFile, db: LibretroDatabase): GameMetadata? {
        if (file.crc == null || file.crc == "0") return null
        return file.crc?.let { crc32 -> db.gameDao().findByCRC(crc32) }
            ?.let { convertToGameMetadata(it, file) }
    }

    private suspend fun findBySerial(file: StorageFile, db: LibretroDatabase): GameMetadata? {
        if (file.serial == null) return null
        return db.gameDao().findBySerial(file.serial!!)
            ?.let { convertToGameMetadata(it, file) }
    }

    private fun findByKnownSystem(file: StorageFile): GameMetadata? {
        if (file.systemID == null) return null

        return GameMetadata(
            name = file.extensionlessName,
            romName = file.name,
            thumbnail = findLocalCoverUrl(file, file.name, file.extensionlessName),
            system = file.systemID!!.dbname,
            developer = null,
        )
    }

    private fun findByUniqueExtension(file: StorageFile): GameMetadata? {
        val system = GameSystem.findByUniqueFileExtension(file.extension)

        if (system?.scanOptions?.scanByUniqueExtension == false) {
            return null
        }

        val result = system?.let {
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = findLocalCoverUrl(file, file.name, file.extensionlessName),
                system = it.id.dbname,
                developer = null
            )
        }

        return result
    }

    private fun extractGameSystem(rom: LibretroRom): GameSystem {
        return GameSystem.findById(rom.system!!)
    }

    private fun findLocalCoverUrl(storageFile: StorageFile, vararg candidates: String?): String? {
        val path = storageFile.path ?: storageFile.uri.path ?: return null
        val romFile = File(path)
        val imagesDir = romFile.parentFile?.resolve("images") ?: return null
        if (!imagesDir.isDirectory) return null

        val coverFiles = coverIndexFor(imagesDir)
        val normalizedCandidates = candidates
            .filterNotNull()
            .flatMap(::coverNameVariants)
            .map(::normalizeCoverName)
            .filter { it.isNotBlank() }
            .distinct()

        for (candidate in normalizedCandidates) {
            coverFiles[candidate]?.let { return Uri.fromFile(it).toString() }
        }

        return null
    }

    private fun coverIndexFor(imagesDir: File): Map<String, File> {
        val key = imagesDir.absolutePath
        return coverIndexCache.getOrPut(key) {
            imagesDir.listFiles()
                ?.filter { it.isFile && supportedCoverExtensions.contains(it.extension.toLowerCase(Locale.US)) }
                ?.associateBy { normalizeCoverName(it.nameWithoutExtension) }
                .orEmpty()
        }
    }

    private fun coverNameVariants(value: String): List<String> {
        val strippedTags = value
            .replace(Regex("\\s*\\(.*?\\)"), "")
            .replace(Regex("\\s*\\[.*?\\]"), "")
            .trim()

        return listOf(value, strippedTags, value.substringBeforeLast(".", value))
    }

    private fun normalizeCoverName(value: String): String {
        return value
            .toLowerCase(Locale.US)
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("[^a-z0-9]+"), "")
    }

    private val coverIndexCache = mutableMapOf<String, Map<String, File>>()

    private val supportedCoverExtensions = setOf("png", "jpg", "jpeg")
}
