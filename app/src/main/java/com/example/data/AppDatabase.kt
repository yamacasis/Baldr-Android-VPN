package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.LoadBalanceStrategy
import com.example.model.TransportType
import com.example.model.VpnProfile
import com.example.model.VpnProtocol
import kotlinx.coroutines.flow.Flow

class VpnTypeConverters {
    @TypeConverter
    fun fromProtocol(value: VpnProtocol): String = value.name

    @TypeConverter
    fun toProtocol(value: String): VpnProtocol =
        VpnProtocol.entries.firstOrNull { it.name == value } ?: VpnProtocol.VLESS

    @TypeConverter
    fun fromTransport(value: TransportType): String = value.name

    @TypeConverter
    fun toTransport(value: String): TransportType =
        TransportType.entries.firstOrNull { it.name == value } ?: TransportType.TCP

    @TypeConverter
    fun fromLoadBalanceStrategy(value: LoadBalanceStrategy): String = value.name

    @TypeConverter
    fun toLoadBalanceStrategy(value: String): LoadBalanceStrategy =
        LoadBalanceStrategy.entries.firstOrNull { it.name == value } ?: LoadBalanceStrategy.ROUND_ROBIN
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM vpn_profiles ORDER BY createdAt DESC")
    fun observeProfiles(): Flow<List<VpnProfile>>

    @Query("SELECT * FROM vpn_profiles")
    suspend fun getAllOnce(): List<VpnProfile>

    @Query("SELECT * FROM vpn_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VpnProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: VpnProfile): Long

    @Update
    suspend fun update(profile: VpnProfile)

    @Query("UPDATE vpn_profiles SET latencyMs = :latencyMs WHERE id = :id")
    suspend fun updateLatency(id: Long, latencyMs: Int?)

    @Query("UPDATE vpn_profiles SET latencyMs = :latencyMs, lastSpeedBps = :speedBps WHERE id = :id")
    suspend fun updateBenchmark(id: Long, latencyMs: Int?, speedBps: Long?)

    @Query("UPDATE vpn_profiles SET lastCountryCode = :countryCode WHERE id = :id")
    suspend fun updateCountryCode(id: Long, countryCode: String)

    @Query("DELETE FROM vpn_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [VpnProfile::class], version = 3, exportSchema = false)
@TypeConverters(VpnTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vpn_profiles ADD COLUMN lastSpeedBps INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vpn_profiles ADD COLUMN memberProfileIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vpn_profiles ADD COLUMN loadBalanceStrategy TEXT NOT NULL DEFAULT 'ROUND_ROBIN'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netshield_profiles.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
