package com.jnetai.assistant.usage

import com.google.gson.Gson
import com.jnetai.assistant.data.db.UsageDao
import com.jnetai.assistant.data.model.ActivityRecord
import com.jnetai.assistant.data.db.ActivityDao
import com.jnetai.assistant.data.model.UsageRecord
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.flow.Flow

data class UsageStats(
    val todayTokens: Long = 0,
    val todayRequests: Long = 0,
    val totalTokens: Long = 0
)

/**
 * Token/cost accounting. Records provider-reported usage, or estimates when the
 * provider does not report usage (estimates are always labelled as estimates,
 * never claimed as exact). Enforces hard daily/monthly limits per profile.
 */
class UsageManager(
    private val usageDao: UsageDao,
    private val activityDao: ActivityDao
) {
    private val gson = Gson()

    fun dayStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun monthStart(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    suspend fun record(
        profileId: Long, model: String,
        promptTokens: Long, completionTokens: Long,
        category: String
    ) {
        try {
            usageDao.insert(
                UsageRecord(
                    profileId = profileId, model = model,
                    promptTokens = promptTokens, completionTokens = completionTokens,
                    category = category, createdAt = System.currentTimeMillis()
                )
            )
        } catch (t: Throwable) {
            Err.e(Err.DB_ERROR, "Failed to record usage", t)
        }
    }

    suspend fun stats(): UsageStats {
        val today = usageDao.totalSince(dayStart())
        val requests = usageDao.countSince(dayStart())
        val total = try { usageDao.totalAll() } catch (_: Throwable) { 0L }
        return UsageStats(todayTokens = today, todayRequests = requests, totalTokens = total)
    }

    /** Daily limit exceeded for this profile? */
    suspend fun dailyExceeded(profileId: Long, dailyLimit: Long): Boolean {
        if (dailyLimit <= 0) return false
        return usageDao.totalForProfileSince(profileId, dayStart()) >= dailyLimit
    }

    /** Monthly limit exceeded for this profile? */
    suspend fun monthlyExceeded(profileId: Long, monthlyLimit: Long): Boolean {
        if (monthlyLimit <= 0) return false
        return usageDao.totalForProfileSince(profileId, monthStart()) >= monthlyLimit
    }

    suspend fun logActivity(type: String, summary: String, detail: String = "", tokens: Long = 0) {
        try {
            activityDao.insert(
                ActivityRecord(type = type, summary = summary, detail = detail, tokens = tokens)
            )
        } catch (t: Throwable) {
            Err.e(Err.DB_ERROR, "Failed to log activity", t)
        }
    }

    fun recentUsage(limit: Int = 100): Flow<List<UsageRecord>> = usageDao.getRecent(limit)
    fun recentActivity(limit: Int = 200): Flow<List<ActivityRecord>> = activityDao.getRecent(limit)
    suspend fun clearActivity() = activityDao.clear()
}