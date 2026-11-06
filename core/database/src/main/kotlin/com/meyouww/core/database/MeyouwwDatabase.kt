package com.meyouww.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val aspect: Float,
    val elementCount: Int,
)

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun get(id: String): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)
}

/** Every remote wallpaper command this phone has handled. Never processed twice. */
@Entity(tableName = "processed_commands")
data class ProcessedCommandEntity(
    @PrimaryKey val commandId: String,
    val senderId: String,
    val sequence: Long,
    /** applied, applied_unreported, declined, failed, cancelled, awaiting */
    val outcome: String,
    val processedAt: Long,
)

/** The last wallpapers meyouww put on this phone, for Undo. */
@Entity(tableName = "applied_wallpapers")
data class AppliedWallpaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandId: String,
    val target: String,
    val file: String,
    val senderName: String,
    val appliedAt: Long,
)

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM processed_commands WHERE commandId = :id")
    suspend fun processed(id: String): ProcessedCommandEntity?

    @Query("SELECT MAX(sequence) FROM processed_commands WHERE senderId = :senderId")
    suspend fun lastSequence(senderId: String): Long?

    @Upsert
    suspend fun markProcessed(entity: ProcessedCommandEntity)

    @Query("INSERT INTO applied_wallpapers (commandId, target, file, senderName, appliedAt) VALUES (:commandId, :target, :file, :senderName, :appliedAt)")
    suspend fun addApplied(commandId: String, target: String, file: String, senderName: String, appliedAt: Long)

    /** Newest first; includes "both" for either target. */
    @Query("SELECT * FROM applied_wallpapers WHERE target = :target OR target = 'both' ORDER BY appliedAt DESC, id DESC")
    suspend fun history(target: String): List<AppliedWallpaperEntity>

    @Query("SELECT * FROM applied_wallpapers ORDER BY appliedAt DESC, id DESC")
    suspend fun allApplied(): List<AppliedWallpaperEntity>

    @Query("DELETE FROM applied_wallpapers WHERE id = :id")
    suspend fun deleteApplied(id: Long)
}

@Database(
    entities = [DraftEntity::class, ProcessedCommandEntity::class, AppliedWallpaperEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class MeyouwwDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao
    abstract fun wallpapers(): WallpaperDao

    companion object {
        const val NAME = "meyouww.db"

        /** v0.4 → v0.5: wallpaper tables. Drafts are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `processed_commands` (`commandId` TEXT NOT NULL, `senderId` TEXT NOT NULL, " +
                        "`sequence` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `processedAt` INTEGER NOT NULL, PRIMARY KEY(`commandId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `applied_wallpapers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`commandId` TEXT NOT NULL, `target` TEXT NOT NULL, `file` TEXT NOT NULL, `senderName` TEXT NOT NULL, " +
                        "`appliedAt` INTEGER NOT NULL)",
                )
            }
        }
    }
}

/**
 * The SQLCipher passphrase: 32 random bytes, stored only encrypted with a Tink key
 * that lives in Android Keystore. Nothing is backed up, so if the key is ever lost
 * (for example after a Keystore reset) the local database is recreated empty
 * rather than left unreadable.
 */
@Singleton
class DatabaseKey @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val keyFile: File get() = File(context.noBackupFilesDir, KEY_FILE)

    fun passphrase(): ByteArray {
        val aead = aead()
        keyFile.takeIf { it.isFile }?.let { file ->
            runCatching { return aead.decrypt(file.readBytes(), ASSOCIATED_DATA) }
        }
        // New or unreadable key: the old database (if any) cannot be opened any more.
        context.deleteDatabase(MeyouwwDatabase.NAME)
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyFile.writeBytes(aead.encrypt(key, ASSOCIATED_DATA))
        return key
    }

    private fun aead(): Aead {
        AeadConfig.register()
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private companion object {
        const val KEY_FILE = "meyouww_db.key"
        const val KEYSET_NAME = "meyouww_db_keyset"
        const val KEYSET_PREFS = "meyouww_db_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://meyouww_db_master_key"
        val ASSOCIATED_DATA = "meyouww-db-v1".toByteArray()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, key: DatabaseKey): MeyouwwDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, MeyouwwDatabase::class.java, MeyouwwDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(key.passphrase()))
            .addMigrations(MeyouwwDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun wallpaperDao(db: MeyouwwDatabase): WallpaperDao = db.wallpapers()
}
