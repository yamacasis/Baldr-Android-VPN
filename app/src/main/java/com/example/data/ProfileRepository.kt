package com.example.data

import com.example.model.VpnProfile
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: ProfileDao) {
    val profiles: Flow<List<VpnProfile>> = dao.observeProfiles()

    suspend fun getAllOnce(): List<VpnProfile> = dao.getAllOnce()

    suspend fun getById(id: Long): VpnProfile? = dao.getById(id)

    suspend fun save(profile: VpnProfile): Long {
        return if (profile.id == 0L) {
            dao.insert(profile)
        } else {
            dao.update(profile)
            profile.id
        }
    }

    suspend fun updateLatency(id: Long, latencyMs: Int?) {
        dao.updateLatency(id, latencyMs)
    }

    suspend fun updateBenchmark(id: Long, latencyMs: Int?, speedBps: Long?) {
        dao.updateBenchmark(id, latencyMs, speedBps)
    }

    suspend fun updateCountryCode(id: Long, countryCode: String) {
        dao.updateCountryCode(id, countryCode)
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
