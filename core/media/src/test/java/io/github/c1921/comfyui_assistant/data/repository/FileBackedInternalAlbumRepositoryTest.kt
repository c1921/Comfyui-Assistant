package io.github.c1921.comfyui_assistant.data.repository

import android.content.Context
import io.github.c1921.comfyui_assistant.data.decoder.DuckDecodeFailureReason
import io.github.c1921.comfyui_assistant.data.decoder.DuckMediaDecodeOutcome
import io.github.c1921.comfyui_assistant.domain.AlbumDecodeOutcomeCode
import io.github.c1921.comfyui_assistant.domain.AlbumMediaKey
import io.github.c1921.comfyui_assistant.domain.GenerationMode
import io.github.c1921.comfyui_assistant.domain.GenerationRequestSnapshot
import io.github.c1921.comfyui_assistant.domain.GenerationState
import io.github.c1921.comfyui_assistant.domain.GeneratedOutput
import io.github.c1921.comfyui_assistant.domain.ImagePresetSnapshot
import io.github.c1921.comfyui_assistant.domain.OutputMediaKind
import io.github.c1921.comfyui_assistant.domain.RequestNodeField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileBackedInternalAlbumRepositoryTest {
    @Test
    fun `archiveGeneration saves image output and writes index and task detail`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val imageUrl = "https://example.com/output_1.png"
        val httpClient = fakeHttpClient(
            urlToBody = mapOf(imageUrl to byteArrayOf(0x01, 0x02, 0x03)),
        )
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = httpClient,
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        val successState = GenerationState.Success(
            taskId = "task-image-1",
            results = listOf(
                GeneratedOutput(
                    fileUrl = imageUrl,
                    fileType = "png",
                    nodeId = "9",
                ),
            ),
            promptTipsNodeErrors = null,
        )

        val saveResult = repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = successState,
            decodePassword = "",
        ).getOrThrow()

        assertEquals("task-image-1", saveResult.taskId)
        assertEquals(1, saveResult.successCount)
        assertEquals(0, saveResult.failedCount)
        assertTrue(repository.hasTask("task-image-1"))

        val detail = repository.loadTaskDetail("task-image-1").getOrThrow()
        assertEquals(1, detail.mediaItems.size)
        assertEquals(OutputMediaKind.IMAGE, detail.mediaItems.first().savedMediaKind)
        assertEquals(AlbumDecodeOutcomeCode.FALLBACK_NOT_CARRIER_IMAGE, detail.mediaItems.first().decodeOutcomeCode)

        val localFile = File(context.filesDir, "internal_album/${detail.mediaItems.first().localRelativePath}")
        assertTrue(localFile.exists())
        assertEquals(3L, localFile.length())

        val summaries = repository.observeTaskSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals("task-image-1", summaries.first().taskId)
    }

    @Test
    fun `archiveGeneration stores decoded video payload when decode returns video`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val imageUrl = "https://example.com/carrier.png"
        val httpClient = fakeHttpClient(
            urlToBody = mapOf(imageUrl to byteArrayOf(0x11, 0x22, 0x33)),
        )
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = httpClient,
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.DecodedVideo(
                    videoBytes = byteArrayOf(0x55, 0x66),
                    extension = "mp4",
                )
            },
        )
        val successState = GenerationState.Success(
            taskId = "task-decoded-video-1",
            results = listOf(
                GeneratedOutput(
                    fileUrl = imageUrl,
                    fileType = "png",
                    nodeId = "11",
                ),
            ),
            promptTipsNodeErrors = null,
        )

        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = successState,
            decodePassword = "pwd",
        ).getOrThrow()

        val detail = repository.loadTaskDetail("task-decoded-video-1").getOrThrow()
        val media = detail.mediaItems.first()
        assertEquals(OutputMediaKind.VIDEO, media.savedMediaKind)
        assertEquals("mp4", media.extension)
        assertEquals(AlbumDecodeOutcomeCode.DECODED_VIDEO, media.decodeOutcomeCode)
        assertTrue(media.decodedFromDuck)
    }

    @Test
    fun `archiveGeneration allows partial success and records failures`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val firstUrl = "https://example.com/success.png"
        val secondUrl = "https://example.com/fail.png"
        val httpClient = fakeHttpClient(
            urlToBody = mapOf(firstUrl to byteArrayOf(0x01, 0x02)),
        )
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = httpClient,
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        val successState = GenerationState.Success(
            taskId = "task-partial-1",
            results = listOf(
                GeneratedOutput(
                    fileUrl = firstUrl,
                    fileType = "png",
                    nodeId = "1",
                ),
                GeneratedOutput(
                    fileUrl = secondUrl,
                    fileType = "png",
                    nodeId = "2",
                ),
            ),
            promptTipsNodeErrors = null,
        )

        val result = repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = successState,
            decodePassword = "",
        ).getOrThrow()

        assertEquals(1, result.successCount)
        assertEquals(1, result.failedCount)
        assertEquals(1, result.failures.size)
        assertEquals(2, result.failures.first().index)

        val detail = repository.loadTaskDetail("task-partial-1").getOrThrow()
        assertEquals(1, detail.savedCount)
        assertEquals(1, detail.failedCount)
        assertEquals(1, detail.failures.size)
    }

    @Test
    fun `archiveGeneration is idempotent for duplicated taskId`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val firstUrl = "https://example.com/success_once.png"
        val firstRepository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(
                urlToBody = mapOf(firstUrl to byteArrayOf(0x01, 0x02)),
            ),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        val stateFirst = GenerationState.Success(
            taskId = "task-idempotent-1",
            results = listOf(
                GeneratedOutput(
                    fileUrl = firstUrl,
                    fileType = "png",
                    nodeId = "1",
                ),
            ),
            promptTipsNodeErrors = null,
        )

        val firstResult = firstRepository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = stateFirst,
            decodePassword = "",
        ).getOrThrow()
        assertEquals(1, firstResult.successCount)

        val secondRepository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(urlToBody = emptyMap()),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        val secondState = GenerationState.Success(
            taskId = "task-idempotent-1",
            results = listOf(
                GeneratedOutput(
                    fileUrl = "https://example.com/should_not_be_downloaded.png",
                    fileType = "png",
                    nodeId = "2",
                ),
            ),
            promptTipsNodeErrors = null,
        )
        val secondResult = secondRepository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = secondState,
            decodePassword = "",
        ).getOrThrow()

        assertEquals(1, secondResult.successCount)
        assertEquals(0, secondResult.failedCount)
        val summaries = secondRepository.observeTaskSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals("task-idempotent-1", summaries.first().taskId)
    }

    @Test
    fun `observeMediaSummaries returns flattened media list in descending creation order`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val firstUrl = "https://example.com/first.png"
        val secondUrl = "https://example.com/second.png"
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(
                urlToBody = mapOf(
                    firstUrl to byteArrayOf(0x01, 0x02),
                    secondUrl to byteArrayOf(0x03, 0x04),
                ),
            ),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )

        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-media-list-1",
                results = listOf(GeneratedOutput(fileUrl = firstUrl, fileType = "png", nodeId = "1")),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()

        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-media-list-2",
                results = listOf(GeneratedOutput(fileUrl = secondUrl, fileType = "png", nodeId = "2")),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()

        val mediaSummaries = repository.observeMediaSummaries().first()
        assertEquals(2, mediaSummaries.size)
        assertEquals(
            setOf(
                AlbumMediaKey(taskId = "task-media-list-1", index = 1),
                AlbumMediaKey(taskId = "task-media-list-2", index = 1),
            ),
            mediaSummaries.map { it.key }.toSet(),
        )
        assertTrue(
            mediaSummaries.zipWithNext().all { (left, right) ->
                left.createdAtEpochMs >= right.createdAtEpochMs
            },
        )
    }

    @Test
    fun `findFirstImageKey prefers first image and findFirstMediaKey falls back to any media`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val videoUrl = "https://example.com/task_mix_1.mp4"
        val imageUrl = "https://example.com/task_mix_2.png"
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(
                urlToBody = mapOf(
                    videoUrl to byteArrayOf(0x01),
                    imageUrl to byteArrayOf(0x02, 0x03),
                ),
            ),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )

        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-key-mixed-1",
                results = listOf(
                    GeneratedOutput(fileUrl = videoUrl, fileType = "mp4", nodeId = "1"),
                    GeneratedOutput(fileUrl = imageUrl, fileType = "png", nodeId = "2"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()

        val firstImage = repository.findFirstImageKey("task-key-mixed-1").getOrThrow()
        val firstMedia = repository.findFirstMediaKey("task-key-mixed-1").getOrThrow()
        assertEquals(AlbumMediaKey(taskId = "task-key-mixed-1", index = 2), firstImage)
        assertEquals(AlbumMediaKey(taskId = "task-key-mixed-1", index = 1), firstMedia)
    }

    @Test
    fun `findFirstImageKey returns null for video only task while first media still resolves`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val videoUrl = "https://example.com/video_only.mp4"
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(
                urlToBody = mapOf(videoUrl to byteArrayOf(0x11, 0x12)),
            ),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )

        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-video-only-1",
                results = listOf(
                    GeneratedOutput(fileUrl = videoUrl, fileType = "mp4", nodeId = "9"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()

        val firstImage = repository.findFirstImageKey("task-video-only-1").getOrThrow()
        val firstMedia = repository.findFirstMediaKey("task-video-only-1").getOrThrow()
        assertEquals(null, firstImage)
        assertEquals(AlbumMediaKey(taskId = "task-video-only-1", index = 1), firstMedia)
    }

    @Test
    fun `index schema v1 is migrated to v2 with media index rebuilt`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val imageUrl = "https://example.com/migration.png"
        val bootstrapRepository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(
                urlToBody = mapOf(imageUrl to byteArrayOf(0x21, 0x22)),
            ),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        bootstrapRepository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-migration-1",
                results = listOf(
                    GeneratedOutput(fileUrl = imageUrl, fileType = "png", nodeId = "1"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()

        val indexFile = File(context.filesDir, "internal_album/index.json")
        val tasksOnlyIndex = JSONObject(indexFile.readText())
            .optJSONArray("tasks")
            ?: throw IllegalStateException("tasks index missing in bootstrap data")
        val legacyIndex = JSONObject().apply {
            put("schemaVersion", 1)
            put("tasks", tasksOnlyIndex)
        }
        indexFile.writeText(legacyIndex.toString())

        val migratedRepository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(urlToBody = emptyMap()),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        val migratedMedia = migratedRepository.observeMediaSummaries().first()
        assertEquals(1, migratedMedia.size)
        assertEquals(AlbumMediaKey(taskId = "task-migration-1", index = 1), migratedMedia.first().key)

        val migratedIndex = JSONObject(indexFile.readText())
        assertEquals(2, migratedIndex.optInt("schemaVersion"))
        assertNotNull(migratedIndex.optJSONArray("tasks"))
        assertNotNull(migratedIndex.optJSONArray("media"))
        assertEquals(1, migratedIndex.optJSONArray("tasks")?.length())
        assertEquals(1, migratedIndex.optJSONArray("media")?.length())
    }

    @Test
    fun `archiveGeneration keeps tasks and media arrays consistent in index`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val okUrl = "https://example.com/index_ok.png"
        val failUrl = "https://example.com/index_fail.png"
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(
                urlToBody = mapOf(okUrl to byteArrayOf(0x31, 0x32, 0x33)),
            ),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )

        val result = repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-index-consistency-1",
                results = listOf(
                    GeneratedOutput(fileUrl = okUrl, fileType = "png", nodeId = "1"),
                    GeneratedOutput(fileUrl = failUrl, fileType = "png", nodeId = "2"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()
        assertEquals(1, result.successCount)
        assertEquals(1, result.failedCount)

        val detail = repository.loadTaskDetail("task-index-consistency-1").getOrThrow()
        val indexFile = File(context.filesDir, "internal_album/index.json")
        val indexJson = JSONObject(indexFile.readText())
        val taskItems = indexJson.optJSONArray("tasks")
        val mediaItems = indexJson.optJSONArray("media")
        assertEquals(1, taskItems?.length())
        assertEquals(detail.savedCount, mediaItems?.length())
    }

    @Test
    fun `deleteMedia removes single media and rewrites task and index`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val firstUrl = "https://example.com/delete_single_1.png"
        val secondUrl = "https://example.com/delete_single_2.png"
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(
                urlToBody = mapOf(
                    firstUrl to byteArrayOf(0x41, 0x42),
                    secondUrl to byteArrayOf(0x43, 0x44),
                ),
            ),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-delete-single-1",
                results = listOf(
                    GeneratedOutput(fileUrl = firstUrl, fileType = "png", nodeId = "1"),
                    GeneratedOutput(fileUrl = secondUrl, fileType = "png", nodeId = "2"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()
        val beforeDetail = repository.loadTaskDetail("task-delete-single-1").getOrThrow()
        val deletedMedia = beforeDetail.mediaItems.first { it.index == 1 }
        val deletedFile = File(context.filesDir, "internal_album/${deletedMedia.localRelativePath}")
        assertTrue(deletedFile.exists())

        val deleteResult = repository.deleteMedia(
            keys = setOf(AlbumMediaKey(taskId = "task-delete-single-1", index = 1)),
        ).getOrThrow()

        assertEquals(1, deleteResult.deletedCount)
        assertEquals(0, deleteResult.missingCount)
        val afterDetail = repository.loadTaskDetail("task-delete-single-1").getOrThrow()
        assertEquals(1, afterDetail.savedCount)
        assertEquals(1, afterDetail.totalOutputs)
        assertEquals(listOf(2), afterDetail.mediaItems.map { it.index })
        assertFalse(deletedFile.exists())

        val indexFile = File(context.filesDir, "internal_album/index.json")
        val indexJson = JSONObject(indexFile.readText())
        assertEquals(1, indexJson.optJSONArray("tasks")?.length())
        assertEquals(1, indexJson.optJSONArray("media")?.length())
    }

    @Test
    fun `deleteMedia removes task directory and index entry when last media is deleted`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val onlyUrl = "https://example.com/delete_last_only.png"
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(urlToBody = mapOf(onlyUrl to byteArrayOf(0x51, 0x52))),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-delete-last-1",
                results = listOf(
                    GeneratedOutput(fileUrl = onlyUrl, fileType = "png", nodeId = "1"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()

        val result = repository.deleteMedia(
            keys = setOf(AlbumMediaKey(taskId = "task-delete-last-1", index = 1)),
        ).getOrThrow()
        assertEquals(1, result.deletedCount)
        assertFalse(repository.hasTask("task-delete-last-1"))
        val taskDirectory = File(context.filesDir, "internal_album/tasks/task-delete-last-1")
        assertFalse(taskDirectory.exists())

        val indexJson = JSONObject(File(context.filesDir, "internal_album/index.json").readText())
        assertEquals(0, indexJson.optJSONArray("tasks")?.length())
        assertEquals(0, indexJson.optJSONArray("media")?.length())
    }

    @Test
    fun `deleteMedia batch across tasks keeps media flow and tasks consistent`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val a1 = "https://example.com/delete_batch_a1.png"
        val a2 = "https://example.com/delete_batch_a2.png"
        val b1 = "https://example.com/delete_batch_b1.png"
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(
                urlToBody = mapOf(
                    a1 to byteArrayOf(0x61),
                    a2 to byteArrayOf(0x62),
                    b1 to byteArrayOf(0x63),
                ),
            ),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-delete-batch-a",
                results = listOf(
                    GeneratedOutput(fileUrl = a1, fileType = "png", nodeId = "1"),
                    GeneratedOutput(fileUrl = a2, fileType = "png", nodeId = "2"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()
        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-delete-batch-b",
                results = listOf(
                    GeneratedOutput(fileUrl = b1, fileType = "png", nodeId = "1"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()

        val result = repository.deleteMedia(
            keys = setOf(
                AlbumMediaKey(taskId = "task-delete-batch-a", index = 1),
                AlbumMediaKey(taskId = "task-delete-batch-b", index = 1),
            ),
        ).getOrThrow()
        assertEquals(2, result.deletedCount)

        val mediaSummaries = repository.observeMediaSummaries().first()
        assertEquals(setOf(AlbumMediaKey(taskId = "task-delete-batch-a", index = 2)), mediaSummaries.map { it.key }.toSet())
        assertTrue(repository.hasTask("task-delete-batch-a"))
        assertFalse(repository.hasTask("task-delete-batch-b"))
    }

    @Test
    fun `deleteMedia reports missing keys without corrupting existing data`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        clearAlbumDirectory(context)
        val existingUrl = "https://example.com/delete_missing_existing.png"
        val repository = FileBackedInternalAlbumRepository(
            context = context,
            httpClient = fakeHttpClient(urlToBody = mapOf(existingUrl to byteArrayOf(0x71, 0x72))),
            decodeMediaIfCarrierImage = { _, _ ->
                DuckMediaDecodeOutcome.Fallback(DuckDecodeFailureReason.NotCarrierImage)
            },
        )
        repository.archiveGeneration(
            requestSnapshot = imageRequestSnapshot(),
            successState = GenerationState.Success(
                taskId = "task-delete-missing-1",
                results = listOf(
                    GeneratedOutput(fileUrl = existingUrl, fileType = "png", nodeId = "1"),
                ),
                promptTipsNodeErrors = null,
            ),
            decodePassword = "",
        ).getOrThrow()

        val result = repository.deleteMedia(
            keys = setOf(
                AlbumMediaKey(taskId = "task-delete-missing-1", index = 1),
                AlbumMediaKey(taskId = "task-delete-missing-1", index = 99),
                AlbumMediaKey(taskId = "task-delete-missing-unknown", index = 1),
            ),
        ).getOrThrow()

        assertEquals(3, result.requestedCount)
        assertEquals(1, result.deletedCount)
        assertEquals(2, result.missingCount)
        assertFalse(repository.hasTask("task-delete-missing-1"))
        assertEquals(0, repository.observeMediaSummaries().first().size)
        val indexJson = JSONObject(File(context.filesDir, "internal_album/index.json").readText())
        assertEquals(0, indexJson.optJSONArray("tasks")?.length())
        assertEquals(0, indexJson.optJSONArray("media")?.length())
    }

    private fun imageRequestSnapshot(): GenerationRequestSnapshot {
        return GenerationRequestSnapshot(
            requestSentAtEpochMs = 123L,
            generationMode = GenerationMode.IMAGE,
            workflowId = "workflow-1",
            prompt = "prompt",
            negative = "negative",
            imagePreset = ImagePresetSnapshot(
                id = "1:1",
                width = 1024,
                height = 1024,
            ),
            videoLengthFrames = null,
            uploadedImageFileName = null,
            nodeInfoList = listOf(
                RequestNodeField(
                    nodeId = "6",
                    fieldName = "text",
                    fieldValue = "prompt",
                ),
            ),
        )
    }

    private fun fakeHttpClient(
        urlToBody: Map<String, ByteArray>,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val body = urlToBody[url]
                if (body == null) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body(ByteArray(0).toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/octet-stream".toMediaType()))
                        .build()
                }
            }
            .build()
    }

    private fun clearAlbumDirectory(context: Context) {
        File(context.filesDir, "internal_album").deleteRecursively()
    }
}
