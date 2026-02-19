package io.github.c1921.comfyui_assistant.data.repository

import android.net.Uri
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileBackedInputImageSelectionStoreTest {
    @Test
    fun `persistSelection copies file and loadSelections restores it`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val store = FileBackedInputImageSelectionStore(context)
        store.clearSelection(GenerationMode.IMAGE)
        store.clearSelection(GenerationMode.VIDEO)
        clearPersistedDirectory(context.filesDir)

        val sourceFile = File(context.cacheDir, "source_image.png").apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        }
        val result = store.persistSelection(
            mode = GenerationMode.IMAGE,
            sourceUri = Uri.fromFile(sourceFile),
            displayName = "picked.png",
        )

        assertTrue(result.isSuccess)
        val persistedSelection = result.getOrNull()
        assertNotNull(persistedSelection)
        val persistedFile = File(requireNotNull(persistedSelection).uri.path.orEmpty())
        assertTrue(persistedFile.exists())
        assertFalse(persistedFile.absolutePath == sourceFile.absolutePath)
        assertTrue(sourceFile.readBytes().contentEquals(persistedFile.readBytes()))

        val loaded = store.loadSelections()
        assertEquals(persistedSelection, loaded.imageMode)
        assertNull(loaded.videoMode)
    }

    @Test
    fun `clearSelection deletes persisted file and metadata`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val store = FileBackedInputImageSelectionStore(context)
        store.clearSelection(GenerationMode.IMAGE)
        clearPersistedDirectory(context.filesDir)

        val sourceFile = File(context.cacheDir, "to_clear_image.jpg").apply {
            writeBytes(byteArrayOf(0x11, 0x22, 0x33))
        }
        val persistedSelection = store.persistSelection(
            mode = GenerationMode.IMAGE,
            sourceUri = Uri.fromFile(sourceFile),
            displayName = "to_clear.jpg",
        ).getOrThrow()

        val persistedFile = File(persistedSelection.uri.path.orEmpty())
        assertTrue(persistedFile.exists())

        store.clearSelection(GenerationMode.IMAGE)

        assertFalse(persistedFile.exists())
        val loaded = store.loadSelections()
        assertNull(loaded.imageMode)
    }

    @Test
    fun `loadSelections clears invalid metadata when persisted file is missing`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val store = FileBackedInputImageSelectionStore(context)
        store.clearSelection(GenerationMode.IMAGE)
        clearPersistedDirectory(context.filesDir)

        val sourceFile = File(context.cacheDir, "missing_after_save.webp").apply {
            writeBytes(byteArrayOf(0x41, 0x42, 0x43))
        }
        val persistedSelection = store.persistSelection(
            mode = GenerationMode.IMAGE,
            sourceUri = Uri.fromFile(sourceFile),
            displayName = "missing.webp",
        ).getOrThrow()
        val persistedFile = File(persistedSelection.uri.path.orEmpty())
        assertTrue(persistedFile.delete())

        val loaded = store.loadSelections()
        assertNull(loaded.imageMode)

        val prefs = context.getSharedPreferences("input_image_selection_store", android.content.Context.MODE_PRIVATE)
        assertFalse(prefs.contains("image_mode_path"))
        assertFalse(prefs.contains("image_mode_display_name"))
    }

    private fun clearPersistedDirectory(filesDir: File) {
        val dir = File(filesDir, "persisted_input_images")
        if (!dir.exists()) return
        dir.listFiles().orEmpty().forEach { file -> file.delete() }
        dir.delete()
    }
}
