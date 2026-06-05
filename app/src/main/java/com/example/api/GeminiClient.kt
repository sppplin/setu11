package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini API Models under Moshi ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

object GeminiClient {
    // Check if the API key is set and not a placeholder
    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "GEMINI_API_KEY" && key != "MY_GEMINI_API_KEY"
    }

    suspend fun generateCounselorResponse(
        history: List<Pair<String, String>>, // sender to message
        userProfileText: String
    ): String {
        if (!isApiKeyConfigured()) {
            return generateMockResponse(history, userProfileText)
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        
        // System instruction context for a personalized Career Mitra (AI Counselor)
        val systemInstructionText = """
            You are "Career Mitra" (करियर मित्र), a friendly and encouraging AI Career Counselor on the "Rozgaar Setu" (रोजगार सेतु) mobile application. 
            Your role is to help blue-collar, green-collar, and grey-collar job seekers in India build resumes, prepare for mock interviews, and acquire skills.
            
            Keep your responses:
            1. Friendly, warm, bilingual (Hindi mixed with English, using simple Words, written in Latin script or Devanagari based on what the user uses), and easy to understand.
            2. Fully localized to Indian blue/grey-collar job contexts (like delivery riders, telecallers, office staff, hospital assistants, retailers, drivers, etc.).
            3. Action-oriented, highlighting 2-3 simple steps the user can take.
            
            Here is the profile details of the user you are helping:
            $userProfileText
            
            Use these details to provide customized assistance. Be extremely supportive. If they ask to update details, guide them to the 'Profile' section of the application.
        """.trimIndent()

        // Build the contents list from history
        val contentsList = history.map { (sender, msg) ->
            val role = if (sender == "user") "user" else "model"
            Content(parts = listOf(Part(text = msg)))
        }

        val request = GeminiRequest(
            contents = contentsList,
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText))),
            generationConfig = GenerationConfig(temperature = 0.7)
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "शमा करें, मैं उत्तर नहीं दे पाया। कृपया पुनः प्रयास करें। (Apologies, I couldn't generate a response. Please try again.)"
        } catch (e: Exception) {
            e.printStackTrace()
            "Network error occurred. Let's talk locally!\n\n${generateMockResponse(history, userProfileText)}"
        }
    }

    private fun generateMockResponse(
        history: List<Pair<String, String>>,
        userProfileText: String
    ): String {
        val lastUserMessage = history.lastOrNull { it.first == "user" }?.second?.lowercase() ?: ""
        
        return when {
            lastUserMessage.contains("resume") || lastUserMessage.contains("बायो") -> {
                """
                    👋  Aapka profile dekh kar maine ek perfect resume summary ready kiya hai!
                    
                    🌟 *Professional Summary*:
                    Hardworking and dedicated individual looking to apply for suitable roles. 
                    - Skilled in communication, quick learning and active teamwork.
                    - Immediate joiner with a high level of responsibility.
                    
                    👉 Aap Profile section me jaakar apni detailed info (Education, Experience, Skills) update kar sakte hain, aur use share kar sakte hain!
                """.trimIndent()
            }
            lastUserMessage.contains("interview") || lastUserMessage.contains("तैयारी") -> {
                """
                    🎯 *Mock Interview Practice!*
                    Chaliye, aapka feedback aur practice start karte hain. General interview questions:
                    
                    1. "Apne baare me batayein aur aap is job me kyun kaam karna chahte hain?"
                    2. "Aap emergency ya busy situation me work kaise handles karenge?"
                    
                    💡 *Tip:* confidence ke sath bolen aur hamesha time par pahunchen.
                    Aap reply likhiye, mai aapka offline assessment karunga!
                """.trimIndent()
            }
            else -> {
                """
                    Namaste! Mai aapka *Career Mitra* (करियर मित्र) hoon. 😊
                    
                    Aap mujhse:
                    1. Aapki choice ke jobs ke liye *Skills* pooch sakte hain.
                    2. Mini *Mock Interview* practice kar sakte hain.
                    3. Resume build karne ke tips le sakte hain.
                    
                    👉 Note: Please configure your *GEMINI_API_KEY* in the AI Studio Secrets panel to unlock actual live AI responses!
                """.trimIndent()
            }
        }
    }
}
