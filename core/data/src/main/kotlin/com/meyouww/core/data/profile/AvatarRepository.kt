package com.meyouww.core.data.profile

import android.content.Context
import android.util.LruCache
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.common.rpcBody
import com.meyouww.core.data.common.statusOf
import com.meyouww.core.data.di.SupabaseProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile pictures. They live in a private bucket that only the person and their
 * friends can read, so they're downloaded with the signed-in session and cached
 * on this phone (memory + app cache folder, which Android may clear).
 */
@Singleton
class AvatarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseProvider,
    private val profiles: ProfileRepository,
) {
    private val memory = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private val misses = mutableSetOf<String>()
    private val lock = Mutex()
    private val dir: File get() = File(context.cacheDir, "avatars").apply { mkdirs() }

    /** Uploads a prepared square JPEG and makes it my picture; the old one is deleted. */
    suspend fun upload(jpeg: ByteArray): Outcome<String> = outcomeOf {
        val client = supabase.client()
        val uid = client.auth.currentUserOrNull()?.id ?: error("signed out")
        val old = profiles.profile.value?.avatarPath
        val path = "$uid/${UUID.randomUUID()}.jpg"
        client.storage.from(BUCKET).upload(path, jpeg) {
            upsert = false
            contentType = ContentType.Image.JPEG
        }
        val status = statusOf(supabase.rpcBody("set_avatar", buildJsonObject { put("p_path", path) }))
        check(status == "ok") { "set_avatar: $status" }
        remember(path, jpeg)
        if (old != null && old != path) runCatching { client.storage.from(BUCKET).delete(old) }
        profiles.refresh()
        path
    }

    suspend fun remove(): Outcome<Unit> = outcomeOf {
        val old = profiles.profile.value?.avatarPath
        supabase.rpcBody("set_avatar", buildJsonObject { put("p_path", JsonNull) })
        if (old != null) runCatching { supabase.client().storage.from(BUCKET).delete(old) }
        profiles.refresh()
        Unit
    }

    /** The picture's bytes, or null if it can't be read (not a friend, offline, deleted). */
    suspend fun bytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        memory.get(path)?.let { return@withContext it }
        val file = File(dir, key(path))
        if (file.exists()) {
            return@withContext file.readBytes().also { memory.put(path, it) }
        }
        lock.withLock {
            memory.get(path)?.let { return@withLock it }
            if (path in misses) return@withLock null
            val fetched = runCatching { supabase.client().storage.from(BUCKET).downloadAuthenticated(path) }.getOrNull()
            if (fetched == null || fetched.size > MAX_BYTES) {
                misses += path
                null
            } else {
                remember(path, fetched)
                fetched
            }
        }
    }

    /** Forget failures (after a new friendship, or coming back online). */
    fun retryMisses() = synchronized(misses) { misses.clear() }

    fun clearCache() {
        memory.evictAll()
        misses.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun remember(path: String, bytes: ByteArray) {
        memory.put(path, bytes)
        runCatching { File(dir, key(path)).writeBytes(bytes) }
    }

    private fun key(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
        val hex = "0123456789abcdef"
        return buildString(digest.size * 2) {
            digest.forEach { b ->
                append(hex[(b.toInt() shr 4) and 0xf])
                append(hex[b.toInt() and 0xf])
            }
        }
    }

    private companion object {
        const val BUCKET = "avatars"
        const val MAX_BYTES = 512 * 1024
    }
}
