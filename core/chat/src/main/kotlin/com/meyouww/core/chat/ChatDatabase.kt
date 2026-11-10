package com.meyouww.core.chat

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meyouww.core.database.DatabaseKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

// ---------------------------------------------------------------- Signal Protocol state

/** Small values: identity key pair, registration ID, prekey counters. */
@Entity(tableName = "crypto_kv")
class KvRow(@PrimaryKey val key: String, val value: ByteArray)

@Entity(tableName = "prekeys")
class PreKeyRow(@PrimaryKey val id: Int, val record: ByteArray)

@Entity(tableName = "signed_prekeys")
class SignedPreKeyRow(@PrimaryKey val id: Int, val record: ByteArray)

@Entity(tableName = "kyber_prekeys")
class KyberPreKeyRow(@PrimaryKey val id: Int, val record: ByteArray, val lastResort: Boolean)

/** Address = "<user id>.<signal device id>". */
@Entity(tableName = "sessions", indices = [Index("name")])
class SessionRow(@PrimaryKey val address: String, val name: String, val deviceId: Int, val record: ByteArray)

@Entity(tableName = "identities")
class IdentityRow(@PrimaryKey val address: String, val name: String, val identityKey: ByteArray, val verified: Boolean, val firstSeen: Long)

/** Which server device a peer's Signal device number belongs to. */
@Entity(tableName = "peer_devices", primaryKeys = ["peerId", "signalId"])
class PeerDeviceRow(val peerId: String, val signalId: Int, val deviceId: String)

// ---------------------------------------------------------------- Chat

@Entity(tableName = "conversations", indices = [Index(value = ["peerId"], unique = true)])
data class ConversationRow(
    @PrimaryKey val id: String,
    val peerId: String,
    val peerName: String,
    /** Preview of the newest message: its text, or null for stickers and deleted messages. */
    val lastText: String?,
    /** text, sticker, deleted, system, or null when the chat is empty. */
    val lastKind: String?,
    val lastFromMe: Boolean,
    val lastAt: Long,
    val unread: Int,
    val muted: Boolean,
    /** A group chat: [id] and [peerId] are the group's ID and [peerName] its name. */
    @ColumnInfo(defaultValue = "0") val isGroup: Boolean = false,
)

/** Who is in a group right now (from the server's list_my_groups). */
@Entity(tableName = "group_members", primaryKeys = ["groupId", "userId"])
data class GroupMemberRow(
    val groupId: String,
    val userId: String,
    val name: String,
    val username: String?,
    val avatar: String?,
    /** admin or member */
    val role: String,
)

/**
 * A decrypted message, kept only in this encrypted database.
 * [status]: sending, sent, delivered, read, failed (mine) or received (theirs).
 */
@Entity(tableName = "messages", indices = [Index("conversationId")])
data class MessageRow(
    @PrimaryKey val id: String,
    val conversationId: String,
    val fromMe: Boolean,
    val senderId: String,
    /** text, sticker, file, system */
    val kind: String,
    val text: String?,
    val sticker: String?,
    val replyTo: String?,
    val status: String,
    val seq: Long?,
    val sentAt: Long,
    val editedAt: Long?,
    val deleted: Boolean,
    /** JSON object: emoji -> list of user IDs. */
    val reactions: String,
    /** Kept after it has been seen. Everything else disappears once seen and the chat is left. */
    @ColumnInfo(defaultValue = "0") val saved: Boolean = false,
    /** For files: [StoredAttachment] as JSON (key, checksum, and where the plain file is on this phone). */
    @ColumnInfo(defaultValue = "NULL") val attachment: String? = null,
    /** For polls and reminders: [PollState] / [ReminderState] as JSON. */
    @ColumnInfo(defaultValue = "NULL") val extra: String? = null,
    /** Mine, in a group: each member's furthest receipt, as JSON (user ID -> delivered / read). */
    @ColumnInfo(defaultValue = "NULL") val receipts: String? = null,
)

/** Encrypted-and-queued work: survives restarts, retried by WorkManager. */
@Entity(tableName = "outbox")
class OutboxRow(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val peerId: String,
    val payload: String,
    val createdAt: Long,
    val attempts: Int,
)

@Dao
interface CryptoDao {
    @Query("SELECT value FROM crypto_kv WHERE `key` = :key")
    fun kv(key: String): ByteArray?

    @Upsert
    fun putKv(row: KvRow)

    @Query("SELECT record FROM prekeys WHERE id = :id")
    fun preKey(id: Int): ByteArray?

    @Upsert
    fun putPreKey(row: PreKeyRow)

    @Query("DELETE FROM prekeys WHERE id = :id")
    fun deletePreKey(id: Int)

    @Query("SELECT record FROM signed_prekeys WHERE id = :id")
    fun signedPreKey(id: Int): ByteArray?

    @Query("SELECT record FROM signed_prekeys")
    fun signedPreKeys(): List<ByteArray>

    @Upsert
    fun putSignedPreKey(row: SignedPreKeyRow)

    @Query("DELETE FROM signed_prekeys WHERE id = :id")
    fun deleteSignedPreKey(id: Int)

    @Query("SELECT * FROM kyber_prekeys WHERE id = :id")
    fun kyberPreKey(id: Int): KyberPreKeyRow?

    @Query("SELECT record FROM kyber_prekeys")
    fun kyberPreKeys(): List<ByteArray>

    @Upsert
    fun putKyberPreKey(row: KyberPreKeyRow)

    @Query("DELETE FROM kyber_prekeys WHERE id = :id")
    fun deleteKyberPreKey(id: Int)

    @Query("SELECT record FROM sessions WHERE address = :address")
    fun session(address: String): ByteArray?

    @Query("SELECT deviceId FROM sessions WHERE name = :name")
    fun sessionDevices(name: String): List<Int>

    @Upsert
    fun putSession(row: SessionRow)

    @Query("DELETE FROM sessions WHERE address = :address")
    fun deleteSession(address: String)

    @Query("DELETE FROM sessions WHERE name = :name")
    fun deleteSessions(name: String)

    @Query("SELECT * FROM identities WHERE address = :address")
    fun identity(address: String): IdentityRow?

    @Query("SELECT * FROM identities WHERE name = :name")
    fun identitiesFor(name: String): List<IdentityRow>

    @Upsert
    fun putIdentity(row: IdentityRow)

    @Query("UPDATE identities SET verified = :verified WHERE name = :name")
    fun setVerified(name: String, verified: Boolean)

    @Query("SELECT * FROM peer_devices WHERE peerId = :peerId")
    fun peerDevices(peerId: String): List<PeerDeviceRow>

    @Upsert
    fun putPeerDevice(row: PeerDeviceRow)

    @Query("DELETE FROM peer_devices WHERE peerId = :peerId")
    fun deletePeerDevices(peerId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM conversations ORDER BY lastAt DESC")
    fun observeConversations(): Flow<List<ConversationRow>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: String): ConversationRow?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun conversationBlocking(id: String): ConversationRow?

    @Query("SELECT * FROM conversations WHERE peerId = :peerId")
    suspend fun conversationWith(peerId: String): ConversationRow?

    @Upsert
    suspend fun putConversation(row: ConversationRow)

    @Query(
        "UPDATE conversations SET lastText = :text, lastKind = :kind, lastFromMe = :fromMe, lastAt = :at, " +
            "unread = unread + :addUnread WHERE id = :id",
    )
    suspend fun touch(id: String, text: String?, kind: String, fromMe: Boolean, at: Long, addUnread: Int)

    @Query("UPDATE conversations SET peerName = :name WHERE peerId = :peerId")
    suspend fun renamePeer(peerId: String, name: String)

    @Query("SELECT COALESCE(SUM(unread), 0) FROM conversations WHERE muted = 0")
    fun observeUnreadTotal(): Flow<Int>

    @Query("UPDATE conversations SET unread = 0 WHERE id = :id")
    suspend fun markConversationRead(id: String)

    @Query("UPDATE conversations SET muted = :muted WHERE id = :id")
    suspend fun setMuted(id: String, muted: Boolean)

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId " +
            "ORDER BY CASE WHEN seq IS NULL THEN 1 ELSE 0 END, seq, sentAt",
    )
    fun observeMessages(conversationId: String): Flow<List<MessageRow>>

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND fromMe = 0 AND kind != 'system' " +
            "ORDER BY sentAt DESC LIMIT :limit",
    )
    suspend fun recentIncoming(conversationId: String, limit: Int): List<MessageRow>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: String): MessageRow?

    @Upsert
    suspend fun putMessage(row: MessageRow)

    @Query("UPDATE messages SET status = :status, seq = COALESCE(:seq, seq) WHERE id = :id")
    suspend fun setStatus(id: String, status: String, seq: Long?)

    /** Receipts only move forward: sent → delivered → read. */
    @Query(
        "UPDATE messages SET status = :status WHERE conversationId = :conversationId AND id IN (:ids) AND fromMe = 1 AND " +
            "(status = 'sent' OR status = 'sending' OR (status = 'delivered' AND :status = 'read'))",
    )
    suspend fun advanceStatus(conversationId: String, ids: List<String>, status: String)

    @Query("UPDATE messages SET status = :status WHERE id IN (SELECT messageId FROM outbox WHERE peerId = :peerId) AND fromMe = 1")
    suspend fun setOutboxStatusForPeer(peerId: String, status: String)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE messageId = :id")
    suspend fun bumpAttempts(id: String)

    @Query("SELECT id FROM messages WHERE conversationId = :conversationId AND fromMe = 0 AND status = 'received'")
    suspend fun unreadIncomingIds(conversationId: String): List<String>

    @Query("UPDATE messages SET status = 'seen' WHERE id IN (:ids)")
    suspend fun markSeen(ids: List<String>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    @Query("UPDATE messages SET saved = :saved WHERE id = :id")
    suspend fun setSaved(id: String, saved: Boolean)

    /**
     * Snapchat-style: removes what has been seen and isn't saved. Theirs once I've seen
     * them; mine once read, once delivered a day ago, or once a week old; deleted
     * tombstones too. Unsent, failed and saved messages stay.
     */
    @Query(
        "DELETE FROM messages WHERE conversationId = :conversationId AND saved = 0 AND kind != 'system' AND (" +
            "deleted = 1 OR (fromMe = 0 AND status = 'seen') OR (fromMe = 1 AND (status = 'read' OR " +
            "(status = 'delivered' AND sentAt < :deliveredBefore) OR (status = 'sent' AND sentAt < :sentBefore))))",
    )
    suspend fun purgeSeen(conversationId: String, deliveredBefore: Long, sentBefore: Long): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND kind != 'system' ORDER BY sentAt DESC LIMIT 1")
    suspend fun newest(conversationId: String): MessageRow?

    @Query("UPDATE conversations SET lastText = :text, lastKind = :kind, lastFromMe = :fromMe WHERE id = :id")
    suspend fun setPreview(id: String, text: String?, kind: String?, fromMe: Boolean)

    @Query("SELECT id FROM conversations")
    suspend fun conversationIds(): List<String>

    @Query("SELECT * FROM messages WHERE kind = 'text' AND deleted = 0 AND text LIKE '%' || :query || '%' ORDER BY sentAt DESC LIMIT 100")
    suspend fun search(query: String): List<MessageRow>

    /** Files from the other person that are still to be fetched (or were). */
    @Query("SELECT * FROM messages WHERE kind = 'file' AND fromMe = 0 AND deleted = 0")
    suspend fun incomingFiles(): List<MessageRow>

    /** Every file message's attachment, to tell which local files are still needed. */
    @Query("SELECT attachment FROM messages WHERE kind = 'file' AND deleted = 0 AND attachment IS NOT NULL")
    suspend fun liveAttachments(): List<String>

    @Query("UPDATE messages SET attachment = :attachment WHERE id = :id")
    suspend fun setAttachment(id: String, attachment: String?)

    @Query("UPDATE messages SET extra = :extra WHERE id = :id")
    suspend fun setExtra(id: String, extra: String?)

    @Query("UPDATE messages SET receipts = :receipts, status = :status WHERE id = :id")
    suspend fun setReceipts(id: String, receipts: String?, status: String)

    /** Who a group message is going to (each at "sent" until their receipt comes). */
    @Query("UPDATE messages SET receipts = :receipts WHERE id = :id")
    suspend fun setAudience(id: String, receipts: String)

    /** Reminders (mine and theirs); the caller skips those already in the calendar. */
    @Query("SELECT * FROM messages WHERE kind = 'reminder' AND deleted = 0")
    suspend fun reminders(): List<MessageRow>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY role, name")
    suspend fun groupMembers(groupId: String): List<GroupMemberRow>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY role, name")
    fun observeGroupMembers(groupId: String): Flow<List<GroupMemberRow>>

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearGroupMembers(groupId: String)

    @Upsert
    suspend fun putGroupMembers(rows: List<GroupMemberRow>)

    @Query("SELECT * FROM conversations WHERE isGroup = 1")
    suspend fun groupConversations(): List<ConversationRow>

    @Query("UPDATE conversations SET peerName = :title WHERE id = :id")
    suspend fun setTitle(id: String, title: String)

    @Upsert
    suspend fun putOutbox(row: OutboxRow)

    @Query("SELECT * FROM outbox WHERE messageId = :id")
    suspend fun outbox(id: String): OutboxRow?

    @Query("SELECT * FROM outbox ORDER BY createdAt")
    suspend fun allOutbox(): List<OutboxRow>

    @Query("DELETE FROM outbox WHERE messageId = :id")
    suspend fun deleteOutbox(id: String)
}

@Database(
    entities = [
        KvRow::class, PreKeyRow::class, SignedPreKeyRow::class, KyberPreKeyRow::class, SessionRow::class,
        IdentityRow::class, PeerDeviceRow::class, ConversationRow::class, MessageRow::class, OutboxRow::class,
        GroupMemberRow::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun crypto(): CryptoDao
    abstract fun messages(): MessageDao

    companion object {
        /** v2: saved messages (everything else disappears after it has been seen). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN saved INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: photos, videos and PDFs (their keys and local files). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachment TEXT DEFAULT NULL")
            }
        }

        /** v4: group chats (members, polls, reminders, per-member receipts). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN isGroup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN extra TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN receipts TEXT DEFAULT NULL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `group_members` (`groupId` TEXT NOT NULL, `userId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `username` TEXT, `avatar` TEXT, `role` TEXT NOT NULL, PRIMARY KEY(`groupId`, `userId`))",
                )
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ChatDatabaseModule {
    @Provides
    @Singleton
    fun chatDatabase(@ApplicationContext context: Context, key: DatabaseKey): ChatDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, ChatDatabase::class.java, DatabaseKey.CHAT_DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(key.passphrase()))
            .addMigrations(ChatDatabase.MIGRATION_1_2, ChatDatabase.MIGRATION_2_3, ChatDatabase.MIGRATION_3_4)
            .build()
    }

    @Provides
    fun cryptoDao(db: ChatDatabase): CryptoDao = db.crypto()

    @Provides
    fun messageDao(db: ChatDatabase): MessageDao = db.messages()
}
