package com.meyouww.core.chat

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * libsignal's state, persisted in the SQLCipher chat database. libsignal calls
 * these methods synchronously, so callers must already be off the main thread.
 *
 * Trust: first use is trusted; a changed identity is accepted but recorded, and the
 * chat shows "Riya's security code changed" (see [ChatRepository]).
 */
@Singleton
class SignalStore @Inject constructor(
    private val dao: CryptoDao,
) : SignalProtocolStore {

    /** Addresses whose identity changed since they were first seen (read by the repository). */
    val changedIdentities: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val senderKeys = ConcurrentHashMap<String, SenderKeyRecord>()

    fun hasIdentity(): Boolean = dao.kv(K_IDENTITY) != null

    /** After the database was wiped (another account signed in). */
    fun forgetMemory() {
        changedIdentities.clear()
        senderKeys.clear()
    }

    fun setLocalIdentity(pair: IdentityKeyPair, registrationId: Int) {
        dao.putKv(KvRow(K_IDENTITY, pair.serialize()))
        dao.putKv(KvRow(K_REGISTRATION, int(registrationId)))
    }

    fun counter(name: String): Int = dao.kv(name)?.let { ByteBuffer.wrap(it).int } ?: 1

    fun setCounter(name: String, value: Int) = dao.putKv(KvRow(name, int(value)))

    // ------------------------------------------------------------ IdentityKeyStore

    override fun getIdentityKeyPair(): IdentityKeyPair =
        IdentityKeyPair(dao.kv(K_IDENTITY) ?: error("no local identity"))

    override fun getLocalRegistrationId(): Int = dao.kv(K_REGISTRATION)?.let { ByteBuffer.wrap(it).int } ?: error("no registration id")

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        val key = address.key()
        val existing = dao.identity(key)
        if (existing != null && existing.identityKey.contentEquals(identityKey.serialize())) {
            return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        }
        dao.putIdentity(IdentityRow(key, address.name, identityKey.serialize(), verified = false, firstSeen = System.currentTimeMillis()))
        if (existing == null) return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        changedIdentities += address.name
        return IdentityKeyStore.IdentityChange.REPLACED_EXISTING
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean = true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        dao.identity(address.key())?.let { IdentityKey(it.identityKey) }

    // ------------------------------------------------------------ PreKeyStore

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        PreKeyRecord(dao.preKey(preKeyId) ?: throw InvalidKeyIdException("no prekey $preKeyId"))

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) = dao.putPreKey(PreKeyRow(preKeyId, record.serialize()))

    override fun containsPreKey(preKeyId: Int): Boolean = dao.preKey(preKeyId) != null

    override fun removePreKey(preKeyId: Int) = dao.deletePreKey(preKeyId)

    // ------------------------------------------------------------ SignedPreKeyStore

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        SignedPreKeyRecord(dao.signedPreKey(signedPreKeyId) ?: throw InvalidKeyIdException("no signed prekey $signedPreKeyId"))

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = dao.signedPreKeys().map { SignedPreKeyRecord(it) }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) =
        dao.putSignedPreKey(SignedPreKeyRow(signedPreKeyId, record.serialize()))

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = dao.signedPreKey(signedPreKeyId) != null

    override fun removeSignedPreKey(signedPreKeyId: Int) = dao.deleteSignedPreKey(signedPreKeyId)

    // ------------------------------------------------------------ KyberPreKeyStore

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        KyberPreKeyRecord(dao.kyberPreKey(kyberPreKeyId)?.record ?: throw InvalidKeyIdException("no kyber prekey $kyberPreKeyId"))

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = dao.kyberPreKeys().map { KyberPreKeyRecord(it) }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) =
        dao.putKyberPreKey(KyberPreKeyRow(kyberPreKeyId, record.serialize(), lastResort = false))

    fun storeLastResortKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) =
        dao.putKyberPreKey(KyberPreKeyRow(kyberPreKeyId, record.serialize(), lastResort = true))

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = dao.kyberPreKey(kyberPreKeyId) != null

    /** One-time Kyber keys are deleted after use; the last-resort key stays. */
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        val row = dao.kyberPreKey(kyberPreKeyId) ?: return
        if (!row.lastResort) dao.deleteKyberPreKey(kyberPreKeyId)
    }

    // ------------------------------------------------------------ SessionStore

    override fun loadSession(address: SignalProtocolAddress): SessionRecord =
        dao.session(address.key())?.let { SessionRecord(it) } ?: SessionRecord()

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> =
        addresses.map { a -> dao.session(a.key())?.let { SessionRecord(it) } ?: throw NoSessionException("no session for $a") }

    override fun getSubDeviceSessions(name: String): List<Int> = dao.sessionDevices(name).filter { it != 1 }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) =
        dao.putSession(SessionRow(address.key(), address.name, address.deviceId, record.serialize()))

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        dao.session(address.key())?.let { SessionRecord(it).hasSenderChain() } ?: false

    override fun deleteSession(address: SignalProtocolAddress) = dao.deleteSession(address.key())

    override fun deleteAllSessions(name: String) = dao.deleteSessions(name)

    // ------------------------------------------------------------ SenderKeyStore (group chats; unused for now)

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        senderKeys["${sender.key()}/$distributionId"] = record
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? =
        senderKeys["${sender.key()}/$distributionId"]

    companion object {
        const val K_IDENTITY = "identity"
        const val K_REGISTRATION = "registration_id"
        const val K_NEXT_PREKEY = "next_prekey"
        const val K_NEXT_KYBER = "next_kyber"
        const val K_SIGNED_ID = "signed_prekey_id"
        const val K_LAST_RESORT_ID = "kyber_last_resort_id"

        fun SignalProtocolAddress.key(): String = "$name.$deviceId"

        private fun int(v: Int): ByteArray = ByteBuffer.allocate(4).putInt(v).array()
    }
}
