package com.remotemagic.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "devices")
data class SavedDevice(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val manufacturer: String,
    val mac: String,
    /** Encrypted with an Android Keystore key. */
    val secret: String,
    val certPin: String,
    val favorite: Boolean,
    val lastConnected: Long
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,
    val time: Long,
    val success: Boolean,
    val detail: String
)

@Entity(tableName = "shortcuts")
data class Shortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** App id or deep link, meaningful only for [protocol]. */
    val target: String,
    val protocol: String
)

@Dao
interface RemoteDao {
    @Query("SELECT * FROM devices ORDER BY favorite DESC, lastConnected DESC")
    fun devices(): Flow<List<SavedDevice>>
    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun device(id: String): SavedDevice?
    @Query("SELECT * FROM devices ORDER BY lastConnected DESC LIMIT 1")
    suspend fun lastDevice(): SavedDevice?
    @Upsert suspend fun save(device: SavedDevice)
    @Query("UPDATE devices SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)
    @Query("UPDATE devices SET mac = :mac WHERE id = :id")
    suspend fun setMac(id: String, mac: String)
    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM devices") suspend fun deleteDevices()

    @Query("SELECT * FROM history ORDER BY time DESC LIMIT 100")
    fun history(): Flow<List<HistoryEntry>>
    @Upsert suspend fun addHistory(entry: HistoryEntry)
    @Query("DELETE FROM history") suspend fun clearHistory()

    @Query("SELECT * FROM shortcuts ORDER BY label")
    fun shortcuts(): Flow<List<Shortcut>>
    @Upsert suspend fun saveShortcut(shortcut: Shortcut)
    @Query("DELETE FROM shortcuts WHERE id = :id") suspend fun deleteShortcut(id: Long)
    @Query("DELETE FROM shortcuts") suspend fun deleteShortcuts()
}

@Database(entities = [SavedDevice::class, HistoryEntry::class, Shortcut::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): RemoteDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "remote_magic.db").build()
    }
}
