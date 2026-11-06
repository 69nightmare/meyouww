package com.meyouww.core.database

import android.content.Context
import android.graphics.Bitmap
import com.meyouww.core.scene.PhotoElement
import com.meyouww.core.scene.Scene
import com.meyouww.core.scene.SceneJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A draft as shown in the Create tab. */
data class Draft(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val aspect: Float,
    val elementCount: Int,
    val thumbnail: File?,
)

/**
 * Drafts: the list lives in the encrypted Room database, each scene and its photos
 * in a private folder (filesDir/drafts/<id>). Writes are atomic, so a crash never
 * leaves a half-written draft.
 */
@Singleton
class DraftStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: dagger.Lazy<MeyouwwDatabase>,
) {
    private val root: File get() = File(context.filesDir, "drafts")

    fun dir(id: String): File = File(root, safe(id))

    fun observe(): Flow<List<Draft>> = flow { emitAll(database.get().drafts().observeAll()) }
        .map { rows -> rows.map { it.toDraft() } }
        .flowOn(Dispatchers.IO)

    /** Creates an empty draft and returns its ID. */
    suspend fun create(scene: Scene, title: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        dir(id).mkdirs()
        writeScene(id, scene)
        val now = System.currentTimeMillis()
        database.get().drafts().upsert(DraftEntity(id, title, now, now, scene.aspect, scene.elements.size))
        id
    }

    suspend fun exists(id: String): Boolean = withContext(Dispatchers.IO) { database.get().drafts().get(safe(id)) != null }

    suspend fun load(id: String): Scene? = withContext(Dispatchers.IO) {
        val file = File(dir(id), SCENE_FILE)
        if (!file.isFile) return@withContext null
        runCatching { SceneJson.decode(file.readText()) }.getOrNull()
    }

    /** Saves the scene and its thumbnail, and removes photos no longer used. */
    suspend fun save(id: String, scene: Scene, thumbnail: Bitmap?) = withContext(Dispatchers.IO) {
        val folder = dir(id)
        folder.mkdirs()
        writeScene(id, scene)
        thumbnail?.let { bmp ->
            atomicWrite(File(folder, THUMB_FILE)) { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
        val used = scene.elements.filterIsInstance<PhotoElement>().map { it.file }.toSet()
        folder.listFiles()?.filter { it.name.startsWith(PHOTO_PREFIX) && it.name !in used }?.forEach { it.delete() }
        val dao = database.get().drafts()
        val existing = dao.get(safe(id))
        val now = System.currentTimeMillis()
        dao.upsert(
            DraftEntity(
                id = safe(id),
                title = existing?.title ?: "",
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                aspect = scene.aspect,
                elementCount = scene.elements.size,
            ),
        )
    }

    suspend fun duplicate(id: String): String? = withContext(Dispatchers.IO) {
        val scene = load(id) ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val from = dir(id)
        val to = dir(newId)
        from.copyRecursively(to, overwrite = true)
        val now = System.currentTimeMillis()
        val title = database.get().drafts().get(safe(id))?.title ?: ""
        database.get().drafts().upsert(DraftEntity(newId, title, now, now, scene.aspect, scene.elements.size))
        newId
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.get().drafts().delete(safe(id))
        dir(id).deleteRecursively()
        Unit
    }

    /** A new file name for an imported photo in this draft. */
    fun newPhotoFile(id: String): File = File(dir(id), "$PHOTO_PREFIX${UUID.randomUUID()}.webp")

    private fun writeScene(id: String, scene: Scene) {
        atomicWrite(File(dir(id), SCENE_FILE)) { out -> out.write(SceneJson.encode(scene).toByteArray()) }
    }

    private fun atomicWrite(target: File, write: (FileOutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            write(out)
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun DraftEntity.toDraft() = Draft(
        id = id,
        title = title,
        updatedAt = updatedAt,
        aspect = aspect,
        elementCount = elementCount,
        thumbnail = File(dir(id), THUMB_FILE).takeIf { it.isFile },
    )

    private companion object {
        const val SCENE_FILE = "scene.json"
        const val THUMB_FILE = "thumb.png"
        const val PHOTO_PREFIX = "photo_"

        /** Draft IDs are UUIDs; anything else is reduced to safe characters. */
        fun safe(id: String): String = id.filter { it.isLetterOrDigit() || it == '-' }.take(64)
    }
}
