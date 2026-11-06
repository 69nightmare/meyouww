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

@Database(entities = [DraftEntity::class], version = 1, exportSchema = false)
abstract class MeyouwwDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao

    companion object {
        const val NAME = "meyouww.db"
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
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}
