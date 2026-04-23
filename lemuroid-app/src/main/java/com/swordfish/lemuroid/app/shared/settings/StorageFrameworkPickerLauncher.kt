package com.swordfish.lemuroid.app.shared.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.io.File

class StorageFrameworkPickerLauncher : RetrogradeActivity() {

    private lateinit var titleView: TextView
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var chooseStorageButton: Button
    private lateinit var selectButton: Button
    private lateinit var cancelButton: Button

    private var storageRoots: List<File> = emptyList()
    private var currentDirectory: File? = null
    private var selectedDirectory: File? = null
    private var showingRoots = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_storage_picker)

        titleView = findViewById(R.id.storage_picker_title)
        listView = findViewById(R.id.storage_picker_list)
        emptyView = findViewById(R.id.storage_picker_empty)
        chooseStorageButton = findViewById(R.id.storage_picker_choose_storage)
        selectButton = findViewById(R.id.storage_picker_select)
        cancelButton = findViewById(R.id.storage_picker_cancel)

        listView.emptyView = emptyView
        listView.setOnItemClickListener { _, _, position, _ ->
            handleItemClick(position)
        }
        listView.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                handleItemSelected(position)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                selectedDirectory = currentDirectory
                updateButtons()
            }
        })
        listView.setOnKeyListener { _, keyCode, event -> handleListKeyEvent(keyCode, event) }

        chooseStorageButton.setOnClickListener { showStorageRoots() }
        selectButton.setOnClickListener {
            selectedDirectory?.let { saveSelectedFolder(it) }
        }
        cancelButton.setOnClickListener { finish() }
        chooseStorageButton.setOnKeyListener { _, keyCode, event -> handleActionKeyEvent(keyCode, event) }
        selectButton.setOnKeyListener { _, keyCode, event -> handleActionKeyEvent(keyCode, event) }
        cancelButton.setOnKeyListener { _, keyCode, event -> handleActionKeyEvent(keyCode, event) }

        storageRoots = getStorageRoots()
        if (storageRoots.isEmpty()) {
            titleView.text = getString(R.string.dialog_saf_not_found, getDefaultDirectory())
            emptyView.visibility = View.VISIBLE
            listView.visibility = View.GONE
            chooseStorageButton.visibility = View.GONE
            selectButton.visibility = View.GONE
            cancelButton.requestFocus()
            return
        }

        showStorageRoots()
    }

    private fun showStorageRoots() {
        showingRoots = true
        currentDirectory = null
        selectedDirectory = storageRoots.firstOrNull()

        titleView.setText(R.string.tv_folder_storage_title)
        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_activated_1,
            storageRoots.mapIndexed { index, root ->
                val title = if (index == 0) {
                    getString(R.string.tv_folder_storage_primary)
                } else {
                    getString(R.string.tv_folder_storage_secondary, index)
                }
                "$title\n${root.absolutePath}"
            }
        )
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(0, true)
        listView.requestFocus()
        listView.setSelection(0)
        emptyView.visibility = View.GONE
        listView.visibility = View.VISIBLE
        updateButtons()
    }

    private fun showDirectory(directory: File) {
        showingRoots = false
        currentDirectory = directory
        selectedDirectory = directory

        val childDirectories = buildDirectoryEntries(directory)
        titleView.text = directory.absolutePath

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_activated_1,
            childDirectories.map { entry ->
                if (entry == ParentEntry) ".." else entry.file.name
            }
        )
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        if (childDirectories.isNotEmpty()) {
            listView.setSelection(0)
        }
        listView.requestFocus()
        emptyView.visibility = if (childDirectories.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setText(R.string.storage_picker_empty_directory)
        listView.visibility = View.VISIBLE
        updateButtons()
    }

    private fun buildDirectoryEntries(directory: File): List<DirectoryEntry> {
        val entries = mutableListOf<DirectoryEntry>()
        val root = storageRoots.firstOrNull { directory.absolutePath.startsWith(it.absolutePath) }

        if (root != null && directory != root) {
            entries += ParentEntry
        }

        entries += directory.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.canRead() }
            ?.sortedBy { it.name.lowercase() }
            ?.map { DirectoryEntry(it) }
            .orEmpty()

        return entries
    }

    private fun handleItemClick(position: Int) {
        if (showingRoots) {
            storageRoots.getOrNull(position)?.let { root ->
                val currentFolder = getCurrentFolder()
                val startFolder = currentFolder?.takeIf {
                    it.exists() && it.absolutePath.startsWith(root.absolutePath)
                } ?: root
                showDirectory(startFolder)
            }
            return
        }

        val directory = currentDirectory ?: return
        val entries = buildDirectoryEntries(directory)
        when (val entry = entries.getOrNull(position)) {
            ParentEntry -> showDirectory(directory.parentFile ?: directory)
            is DirectoryEntry -> showDirectory(entry.file)
            null -> Unit
        }
    }

    private fun handleItemSelected(position: Int) {
        selectedDirectory = if (showingRoots) {
            storageRoots.getOrNull(position)
        } else {
            currentDirectory ?: return
        }
        updateButtons()
    }

    private fun updateButtons() {
        chooseStorageButton.visibility = if (showingRoots) View.GONE else View.VISIBLE
        selectButton.isEnabled = selectedDirectory != null
        val firstActionButton = if (chooseStorageButton.visibility == View.VISIBLE) {
            chooseStorageButton
        } else {
            selectButton
        }

        listView.nextFocusDownId = firstActionButton.id
        chooseStorageButton.nextFocusUpId = listView.id
        selectButton.nextFocusUpId = listView.id
        cancelButton.nextFocusUpId = listView.id
    }

    private fun handleListKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
            return false
        }

        val selectedPosition = listView.selectedItemPosition
        val lastPosition = (listView.adapter?.count ?: 0) - 1
        if (lastPosition < 0 || selectedPosition >= lastPosition) {
            if (chooseStorageButton.visibility == View.VISIBLE) {
                chooseStorageButton.requestFocus()
            } else {
                selectButton.requestFocus()
            }
            return true
        }

        return false
    }

    private fun handleActionKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        if (keyCode != KeyEvent.KEYCODE_DPAD_UP) {
            return false
        }

        if (listView.visibility != View.VISIBLE) {
            return false
        }

        val selectedPosition = when (val checkedPosition = listView.checkedItemPosition) {
            AdapterView.INVALID_POSITION -> 0
            else -> checkedPosition
        }
        listView.requestFocus()
        listView.setSelection(selectedPosition)
        if (selectedPosition < (listView.adapter?.count ?: 0)) {
            listView.setItemChecked(selectedPosition, true)
        }
        return true
    }

    private fun saveSelectedFolder(directory: File) {
        val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
        val legacyPreferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)
        val safPreferenceKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)

        sharedPreferences.edit().apply {
            putString(legacyPreferenceKey, directory.absolutePath)
            remove(safPreferenceKey)
            apply()
        }

        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
        finish()
    }

    private fun getCurrentFolder(): File? {
        val prefString = getString(com.swordfish.lemuroid.lib.R.string.pref_key_legacy_external_folder)
        val preferenceManager = SharedPreferencesHelper.getLegacySharedPreferences(this)
        return preferenceManager.getString(prefString, null)?.let(::File)
    }

    private fun getDefaultDirectory(): String = File(getExternalFilesDir(null) ?: filesDir, "roms").absolutePath

    private fun getStorageRoots(): List<File> {
        val externalRoots = mutableListOf<File>()
        externalRoots += Environment.getExternalStorageDirectory()
        externalRoots += getExternalFilesDirs(null).mapNotNull { it?.let(::extractStorageRoot) }
        externalRoots += discoverMountedRoots()

        return externalRoots
            .map(::normalizeStorageRoot)
            .distinctBy { it.absolutePath }
            .filter { it.exists() && it.isDirectory && it.canRead() }
            .filterNot(::isIgnoredStorageRoot)
    }

    private fun extractStorageRoot(appSpecificDir: File): File {
        val path = appSpecificDir.absolutePath
        val marker = "${File.separator}Android${File.separator}"
        return File(path.substringBefore(marker, path))
    }

    private fun discoverMountedRoots(): List<File> {
        val containerDirectories = listOf(
            File("/storage"),
            File("/mnt"),
            File("/mnt/media_rw"),
            File("/Removable"),
        ).filter { it.exists() && it.isDirectory && it.canRead() }

        val mountedRoots = mutableSetOf<File>()
        mountedRoots += readMountPoints(File("/proc/mounts"))
        mountedRoots += readMountPoints(File("/system/etc/vold.fstab"))
        mountedRoots += readMountPoints(File("/fstab"))
        mountedRoots += containerDirectories.flatMap { container ->
            container.listFiles()
                ?.filter { it.isDirectory && it.canRead() && looksLikeStorageMount(it) }
                .orEmpty()
        }

        return mountedRoots
            .filter { it.exists() && it.isDirectory && it.canRead() }
    }

    private fun readMountPoints(file: File): List<File> {
        if (!file.exists() || !file.canRead()) {
            return emptyList()
        }

        return runCatching {
            file.readLines()
                .mapNotNull { line ->
                    line.trim()
                        .split(Regex("\\s+"))
                        .asSequence()
                        .map(::File)
                        .firstOrNull(::looksLikeStorageMount)
                }
        }.getOrDefault(emptyList())
    }

    private fun looksLikeStorageMount(file: File): Boolean {
        val path = file.path.lowercase()
        if (!path.startsWith("/")) {
            return false
        }

        return path.startsWith("/storage/") ||
            path.startsWith("/mnt/") ||
            path.startsWith("/removable/") ||
            path.startsWith("/sdcard") ||
            listOf("usb", "udisk", "usbotg", "extsd", "external", "mmc").any { it in path }
    }

    private fun normalizeStorageRoot(file: File): File {
        val path = file.canonicalPath
        return when {
            path.endsWith("/.") -> File(path.removeSuffix("/."))
            else -> File(path)
        }
    }

    private fun isIgnoredStorageRoot(file: File): Boolean {
        val path = file.absolutePath
        val name = file.name.lowercase()

        if (path == "/" || path == "/storage" || path == "/mnt" || path == "/mnt/media_rw") {
            return true
        }

        return name in setOf(
            "self",
            "emulated",
            "asec",
            "obb",
            "secure",
            "shell",
            "legacy",
        )
    }

    companion object {
        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, StorageFrameworkPickerLauncher::class.java))
        }
    }

    private open class DirectoryEntry(val file: File)
    private object ParentEntry : DirectoryEntry(File(".."))
}
