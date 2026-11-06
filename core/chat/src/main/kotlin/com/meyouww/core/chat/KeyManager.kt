package com.meyouww.core.chat

import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.data.prefs.AppPreferences
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This phone's Signal identity. Private keys never leave the encrypted chat
 * database; only public keys (and their signatures) are published.
 */
@Singleton
class KeyManager @Inject constructor(
    private val db: ChatDatabase,
    private val store: SignalStore,
    private val supabase: SupabaseProvider,
    private val prefs: AppPreferences,
) {
    private val mutex = Mutex()

    /**
     * Creates keys on first use, republishes if the server lost them, rotates the
     * signed prekey monthly and tops up one-time keys.
     */
    suspend fun ensure(): Outcome<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            outcomeOf {
                val uid = supabase.client().auth.currentUserOrNull()?.id ?: error("signed out")
                bindToAccount(uid)
                val installation = prefs.installationId()
                val created = if (!store.hasIdentity()) {
                    createIdentity()
                    true
                } else {
                    false
                }
                val rotated = !created && rotateSignedPreKeyIfOld()
                val counts = rpc("prekey_counts", buildJsonObject { put("p_installation_id", installation) })
                val serverIdentity = counts["identity_key"]?.jsonPrimitive?.content
                val localIdentity = b64(store.identityKeyPair.publicKey.serialize())
                val ok = counts["status"]?.jsonPrimitive?.content == "ok"
                when {
                    created || rotated || !ok || serverIdentity != localIdentity -> publish(installation, full = true)
                    (counts["prekeys"]?.jsonPrimitive?.int ?: 0) < LOW_WATER ||
                        (counts["kyber_prekeys"]?.jsonPrimitive?.int ?: 0) < LOW_WATER -> publish(installation, full = false)
                }
            }
        }
    }

    /**
     * Keys and chats belong to one account. If someone else signs in on this phone, the
     * old account's keys and messages are wiped before anything is created.
     */
    private fun bindToAccount(uid: String) {
        val owner = db.crypto().kv(K_OWNER)?.toString(Charsets.UTF_8)
        if (owner == uid) return
        if (owner != null) {
            db.clearAllTables()
            store.forgetMemory()
        }
        db.crypto().putKv(KvRow(K_OWNER, uid.toByteArray(Charsets.UTF_8)))
    }

    /** The account this phone's chat keys belong to, if any. */
    fun ownerId(): String? = db.crypto().kv(K_OWNER)?.toString(Charsets.UTF_8)

    private fun rotateSignedPreKeyIfOld(): Boolean {
        val currentId = store.counter(SignalStore.K_SIGNED_ID)
        val current = runCatching { store.loadSignedPreKey(currentId) }.getOrNull()
        val now = System.currentTimeMillis()
        if (current != null && now - current.timestamp < ROTATE_AFTER_MS) return false
        val identity = store.identityKeyPair
        val nextId = if (currentId >= MAX_ID) 1 else currentId + 1
        val pair = ECKeyPair.generate()
        val signature = identity.privateKey.calculateSignature(pair.publicKey.serialize())
        store.storeSignedPreKey(nextId, SignedPreKeyRecord(nextId, now, pair, signature))
        store.setCounter(SignalStore.K_SIGNED_ID, nextId)
        // Old ones stay a while for messages already on their way, then go.
        store.loadSignedPreKeys()
            .filter { it.id != nextId && now - it.timestamp > 2 * ROTATE_AFTER_MS }
            .forEach { store.removeSignedPreKey(it.id) }
        return true
    }

    private fun createIdentity() {
        val identity = IdentityKeyPair.generate()
        store.setLocalIdentity(identity, KeyHelper.generateRegistrationId(false))
        val now = System.currentTimeMillis()

        val signedId = SecureRandom().nextInt(MAX_ID - 1) + 1
        val signed = ECKeyPair.generate()
        val signature = identity.privateKey.calculateSignature(signed.publicKey.serialize())
        store.storeSignedPreKey(signedId, SignedPreKeyRecord(signedId, now, signed, signature))
        store.setCounter(SignalStore.K_SIGNED_ID, signedId)

        val kyber = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSig = identity.privateKey.calculateSignature(kyber.publicKey.serialize())
        store.storeLastResortKyberPreKey(LAST_RESORT_ID, KyberPreKeyRecord(LAST_RESORT_ID, now, kyber, kyberSig))
        store.setCounter(SignalStore.K_LAST_RESORT_ID, LAST_RESORT_ID)
    }

    private suspend fun publish(installation: String, full: Boolean) {
        val identity = store.identityKeyPair
        val keys = buildJsonObject {
            if (full) {
                val signedId = store.counter(SignalStore.K_SIGNED_ID)
                val signed = store.loadSignedPreKey(signedId)
                val lastResort = store.loadKyberPreKey(LAST_RESORT_ID)
                put("registration_id", store.localRegistrationId)
                put("identity_key", b64(identity.publicKey.serialize()))
                put("signed_prekey_id", signedId)
                put("signed_prekey", b64(signed.keyPair.publicKey.serialize()))
                put("signed_prekey_sig", b64(signed.signature))
                put("kyber_last_resort_id", LAST_RESORT_ID)
                put("kyber_last_resort", b64(lastResort.keyPair.publicKey.serialize()))
                put("kyber_last_resort_sig", b64(lastResort.signature))
            }
            put("prekeys", newPreKeys(BATCH))
            put("kyber_prekeys", newKyberPreKeys(identity, BATCH))
        }
        val reply = rpc("publish_keys", buildJsonObject {
            put("p_installation_id", installation)
            put("p_keys", keys)
        })
        check(reply["status"]?.jsonPrimitive?.content == "ok") { "publish failed: ${reply["status"]}" }
    }

    private fun newPreKeys(n: Int): JsonArray {
        var next = store.counter(SignalStore.K_NEXT_PREKEY)
        val out = buildJsonArray {
            repeat(n) {
                val id = next
                next = if (next >= MAX_ID) 1 else next + 1
                val pair = ECKeyPair.generate()
                store.storePreKey(id, PreKeyRecord(id, pair))
                add(buildJsonObject {
                    put("id", id)
                    put("key", b64(pair.publicKey.serialize()))
                })
            }
        }
        store.setCounter(SignalStore.K_NEXT_PREKEY, next)
        return out
    }

    private fun newKyberPreKeys(identity: IdentityKeyPair, n: Int): JsonArray {
        var next = store.counter(SignalStore.K_NEXT_KYBER)
        val now = System.currentTimeMillis()
        val out = buildJsonArray {
            repeat(n) {
                val id = next
                next = if (next >= LAST_RESORT_ID - 1) 1 else next + 1
                val pair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
                val sig = identity.privateKey.calculateSignature(pair.publicKey.serialize())
                store.storeKyberPreKey(id, KyberPreKeyRecord(id, now, pair, sig))
                add(buildJsonObject {
                    put("id", id)
                    put("key", b64(pair.publicKey.serialize()))
                    put("sig", b64(sig))
                })
            }
        }
        store.setCounter(SignalStore.K_NEXT_KYBER, next)
        return out
    }

    private suspend fun rpc(fn: String, params: JsonObject): JsonObject = supabase.call(fn, params).jsonObject

    companion object {
        private const val BATCH = 50
        private const val LOW_WATER = 15
        private const val MAX_ID = 0xFFFFFF
        private const val ROTATE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
        private const val K_OWNER = "owner"
        const val LAST_RESORT_ID = 0xFFFFF0

        fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}
