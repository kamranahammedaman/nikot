package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    val thinkingLevel: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val thinkingConfig: ThinkingConfig? = null,
    val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GemPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GemContent(
    val parts: List<GemPart>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GemContent>,
    val systemInstruction: GemContent? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GemCandidate(
    val content: GemContent
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GemCandidate>?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val apiService: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }

    suspend fun generatePharmacySuggestions(
        systemPrompt: String,
        userPrompt: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "এপিআই কী (API Key) কনফিগার করা নেই। অনুগ্রহ করে AI Studio-এর Secrets প্যানেল থেকে GEMINI_API_KEY নির্ধারণ করুন।"
        }

        val request = GeminiRequest(
            contents = listOf(
                GemContent(parts = listOf(GemPart(text = userPrompt)))
            ),
            systemInstruction = GemContent(parts = listOf(GemPart(text = systemPrompt))),
            generationConfig = GenerationConfig(
                thinkingConfig = ThinkingConfig(thinkingLevel = "HIGH"),
                temperature = 0.5
            )
        )

        return try {
            val response = apiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "কোনো প্রতিক্রিয়া পাওয়া যায়নি।"
        } catch (e: Exception) {
            "ত্রুটি ঘটেছে: ${e.localizedMessage ?: "Gemini API সংযোগে ব্যর্থ হয়েছে।"}"
        }
    }
}
