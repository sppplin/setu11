package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Single profile constraint
    val name: String = "",
    val phone: String = "",
    val location: String = "",
    val preferredRole: String = "Delivery Partner",
    val skills: String = "Good communication", // Comma-separated or default
    val experienceYears: Int = 0,
    val educationLevel: String = "12th Pass",
    val resumeBio: String = "",
    val userRoleType: String = "Worker", // "Worker" (Majdoor), "Contractor" (Thekedar), or "Employer" (Kaam Dene Waala)
    val businessName: String = "",       // Shop / Company Name for Employers
    val offeredWage: Int = 500,          // Custom wage offer for Employers
    val aadhaarNumber: String = "",
    val isAadhaarVerified: Boolean = false,
    val profilePicUrl: String = "",
    val upiId: String = "",
    val withdrawnAmount: Int = 0,
    val userLatitude: Double = 19.1154,
    val userLongitude: Double = 72.8681
)

@Entity(tableName = "job_applications")
data class JobApplication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val jobId: String,
    val title: String,
    val company: String,
    val salary: String,
    val location: String,
    val status: String = "Submitted", // "Submitted", "Under Review", "Interview Scheduled", "Shortlisted"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_chat_messages")
data class AiChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "user" or "ai"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Room DAOs ---

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)
}

@Dao
interface JobApplicationDao {
    @Query("SELECT * FROM job_applications ORDER BY timestamp DESC")
    fun getAllApplicationsFlow(): Flow<List<JobApplication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(application: JobApplication)

    @Query("SELECT EXISTS(SELECT 1 FROM job_applications WHERE jobId = :jobId)")
    fun isAppliedFlow(jobId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM job_applications WHERE jobId = :jobId)")
    suspend fun isAppliedDirect(jobId: String): Boolean

    @Query("UPDATE job_applications SET status = :status WHERE jobId = :jobId")
    suspend fun updateStatus(jobId: String, status: String)
}

@Dao
interface AiChatMessageDao {
    @Query("SELECT * FROM ai_chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<AiChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessage)

    @Query("DELETE FROM ai_chat_messages")
    suspend fun clearChatHistory()
}

// --- App Database ---

@Database(entities = [UserProfile::class, JobApplication::class, AiChatMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun jobApplicationDao(): JobApplicationDao
    abstract fun aiChatMessageDao(): AiChatMessageDao
}

// --- Database Provider ---

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "rozgaar_setu_db"
            )
            .fallbackToDestructiveMigration()
            .build()
            instance = db
            db
        }
    }
}
