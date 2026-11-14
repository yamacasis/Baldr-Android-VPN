package com.example.data

import com.example.model.VpnProfile
import com.example.model.VpnSubscription
import kotlinx.coroutines.flow.Flow

class ProfileRepository(
    private val dao: ProfileDao,
    private val subscriptionDao: SubscriptionDao? = null
) {
    val profiles: Flow<List<VpnProfile>> = dao.observeProfiles()
    val subscriptions: Flow<List<VpnSubscription>>? = subscriptionDao?.observeSubscriptions()

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

    suspend fun getSubscriptionsOnce(): List<VpnSubscription> =
        subscriptionDao?.getAllOnce().orEmpty()

    suspend fun saveSubscription(subscription: VpnSubscription): Long {
        val subDao = subscriptionDao ?: return 0L
        return if (subscription.id == 0L) {
            subDao.insert(subscription)
        } else {
            subDao.update(subscription)
            subscription.id
        }
    }

    suspend fun replaceSubscriptionProfiles(subscriptionId: Long, newProfiles: List<VpnProfile>): List<Long> {
        dao.deleteBySubscriptionId(subscriptionId)
        val insertedIds = mutableListOf<Long>()
        for (p in newProfiles) {
            val id = dao.insert(p.copy(id = 0L, subscriptionId = subscriptionId))
            insertedIds += id
        }
        return insertedIds
    }

    suspend fun deleteSubscription(subscriptionId: Long) {
        dao.deleteBySubscriptionId(subscriptionId)
        subscriptionDao?.deleteById(subscriptionId)
    }
}
