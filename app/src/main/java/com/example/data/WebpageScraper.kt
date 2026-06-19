package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebpageMetadata(
    val title: String,
    val summary: String,
    val imageUrl: String,
    val category: String? = null,
    val tags: String? = null
)

object WebpageScraper {
    private const val TAG = "WebpageScraper"

    // Groq OpenAI-compatible endpoint
    private const val GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val GROQ_MODEL = "openai/gpt-oss-20b"

    fun extractUrlFromText(text: String): String? {
        val urlRegex = """(https?://[^\s$.?#].[^\s]*)""".toRegex(RegexOption.IGNORE_CASE)
        return urlRegex.find(text)?.value
    }

    suspend fun extractMetadata(
        url: String,
        context: android.content.Context? = null
    ): WebpageMetadata = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            return@withContext WebpageMetadata("", "", "")
        }

        // 1) Local fallback first
        val rawHtml = fetchHtmlContent(trimmedUrl)
        val fallbackMeta = parseOpenGraphMetadata(rawHtml, trimmedUrl)

        // 2) Read Custom API key (User-entered Groq/Gemini key from App Settings)
        val prefs = context?.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val customKey = prefs?.getString("custom_groq_api_key", "")?.trim()?.ifEmpty {
            prefs?.getString("custom_gemini_api_key", "")?.trim()
        }

        val apiKey = customKey.orEmpty()

        if (apiKey.isEmpty()) {
            Log.e(TAG, "No custom AI API key found.")
            throw Exception(
                "AI API key is not configured. Please go to Settings (gear icon at top right) and enter your own API key to enable AI curation."
            )
        }

        try {
            val customInstructions = prefs?.getString("custom_ai_analysis_code", "")?.trim()

            val systemInstruction = if (!customInstructions.isNullOrEmpty()) {
                customInstructions
            } else {
                """
                    You are an advanced webpage metadata extractor with deep domain knowledge of game platforms (e.g., Steam, Epic, Google Play, App Store), movie/show databases (e.g., IMDb, Rotten Tomatoes, Letterboxd, Netflix), book directories (e.g., Goodreads, Google Books), news articles, e-commerce, and general content.
                    
                    Your task is to analyze the shared web link and any local suggestions to compile the most comprehensive, polished, and structured metadata.
                    
                    Determine the type of content of the link and build a beautifully formatted `content` block along with an appropriate `category` and pipe-separated `tags`.
                    
                    Here are the content types and formatting rules:
                    
                    1. GAME (PC, Console, or Mobile):
                       - Category Suggestion: "Gaming"
                       - Tags Suggestion: Create 2-4 highly relevant tags (e.g., RPG|Steam|Multiplayer|Adventure|Indie|Tactical).
                       - Content Format:
                         🎮 **Game**: [Game Title]
                         📝 **Description**: [Actionable 2-3 sentence overview]
                         🏷️ **Genre**: [e.g. Action RPG, Strategy]
                         📦 **Size**: [Estimated download size, e.g. 50 GB, 120 MB, or "Unknown"]
                         ⭐ **Rating**: [e.g. 9/10 (Steam), 4.6/5 stars, or "Pending"]
                         👥 **Developer/Publisher**: [Developer Name]
                         📅 **Release Date**: [e.g. Nov 2024, or "Unknown"]
                    
                    2. MOVIE / TV SHOW:
                       - Category Suggestion: "Movies & TV"
                       - Tags Suggestion: Create 2-4 highly relevant tags (e.g., Sci-Fi|Drama|Netflix|IMDb|Thriller|Action).
                       - Content Format:
                         🍿 **Title**: [Movie/Show Title]
                         🎬 **Director**: [Director Name]
                         👥 **Cast**: [Top 3-4 cast members]
                         ⭐ **Rating & Metadata**: [e.g. PG-13, IMDb/Rotten Tomatoes rating, runtime]
                         🎥 **Storyline**: [Brief premium card-ready description]
                         📅 **Release Year**: [Year]
                    
                    3. BOOK:
                       - Category Suggestion: "Books"
                       - Tags Suggestion: Create 2-4 highly relevant tags (e.g., Fantasy|Sci-Fi|Non-Fiction|Self-Help|Bestseller).
                       - Content Format:
                         📚 **Title**: [Book Title]
                         ✍️ **Author**: [Author Name]
                         📖 **Synopsis**: [Polished summary of the plot or main thesis]
                         🏷️ **Genre**: [Genre]
                         ⭐ **Rating**: [e.g. 4.2/5 on Goodreads]
                         📄 **Pages**: [Page count if known, or "Unknown"]
                    
                    4. NEWS / BLOGS / ARTICLES:
                       - Category Suggestion: "News"
                       - Tags Suggestion: Create 2-4 relevant tags (e.g., Tech|Science|Finance|AI|Editorial).
                       - Content Format:
                         📰 **Title**: [Headline]
                         📌 **Overview**: [1-2 sentence core message of the article]
                         🔍 **Key Insights**:
                         - [Key takeaway 1]
                         - [Key takeaway 2]
                         🏷️ **Metadata**: [Author, Publisher, publication date if known]
                    
                    5. GENERAL / OTHER LINKS (e.g., social posts, portfolios, general websites):
                       - Category Suggestion: "Reference"
                       - Tags Suggestion: Create 2-4 general informative tags (e.g., Social|Portfolio|Reference|Guide).
                       - Content Format:
                         📌 **Overview**: [Detailed summary in simple words]
                         🔍 **Key Info/Data**: [Any relevant details discovered about the page]
                    
                    Rules:
                    - Return ONLY a valid JSON object.
                    - Do not include markdown code fences (```json or ```).
                    - Content must use Unicode emojis, bolding (**), and standard bullet points where specified or applicable, but NO HTML tags.
                    - Keep the summary informative and useful.
                    - **CRITICAL**: The "title" field MUST be fully generated by you (the AI), completely replacing any raw browser title with a highly specific, clean, and professional name of the actual subject (e.g., exact game title, movie name, book title, or elegant short headline). Do NOT include generic headers, suffixes, or raw site names like "Steam Community ::", "IMDb -", "Goodreads |", or "Netflix - Watch". Keep the title extremely crisp, specific, and to the point (ideally under 6 words).
                    
                    Output format:
                    {
                      "title": "Title or Name",
                      "content": "Fully formatted content block",
                      "imageUrl": "Main image/poster URL",
                      "category": "Recommended Category (singular, capitalize first letter, no prefix)",
                      "tags": "pipe-separated-list-of-tags (e.g. Steam|RPG|Sci-Fi)"
                    }
                """.trimIndent()
            }

            val titleText = fallbackMeta.title.ifEmpty { "HTML parsed title: (not available)" }
            val descText = fallbackMeta.summary.ifEmpty { "HTML parsed description: (not available)" }
            val imgText = fallbackMeta.imageUrl.ifEmpty { "HTML parsed image: (not available)" }

            val prompt = """
                Link: ${trimmedUrl}

                Local Scraper suggestions:
                Title: ${titleText}
                Description/Snippet: ${descText}
                Image: ${imgText}

                Extract the webpage metadata and return ONLY JSON with:
                {
                  "title": "...",
                  "content": "...",
                  "imageUrl": "...",
                  "category": "...",
                  "tags": "..."
                }

                If image/poster is not available, set imageUrl to empty.
                If some info is not available, try to infer it using your vast pre-trained world knowledge.
            """.trimIndent()

            val client = OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()

            val requestJson = JSONObject().apply {
                put("model", GROQ_MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.2)
                put("response_format", JSONObject().apply {
                    put("type", "json_object")
                })
            }

            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(GROQ_CHAT_URL)
                .addHeader("Authorization", "Bearer ${apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Groq API Call failed: ${response.code} ${errorBody}")
                    return@withContext fallbackMeta
                }

                val responseStr = response.body?.string() ?: ""
                Log.d(TAG, "Groq Response: ${responseStr}")

                val jsonResponse = JSONObject(responseStr)
                val choices = jsonResponse.optJSONArray("choices")

                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.optJSONObject("message")
                    val contentText = messageObj?.optString("content", "")?.trim().orEmpty()

                    if (contentText.isNotEmpty()) {
                        val cleanText = stripCodeFences(contentText)
                        try {
                            val extractedJson = JSONObject(cleanText)

                            val title = extractedJson.optString("title", fallbackMeta.title)
                            val contentSummary = extractedJson.optString(
                                "content",
                                extractedJson.optString("summary", fallbackMeta.summary)
                            )
                            val imageUrl = extractedJson.optString("imageUrl", fallbackMeta.imageUrl)
                            val category = extractedJson.optString("category", null)
                            val tags = extractedJson.optString("tags", null)

                            return@withContext WebpageMetadata(
                                title = title.ifEmpty { fallbackMeta.title },
                                summary = contentSummary.ifEmpty { fallbackMeta.summary },
                                imageUrl = imageUrl.ifEmpty { fallbackMeta.imageUrl },
                                category = if (category.isNullOrEmpty()) null else category,
                                tags = if (tags.isNullOrEmpty()) null else tags
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse Groq JSON: ${e.message}")
                            return@withContext fallbackMeta
                        }
                    }
                }

                return@withContext fallbackMeta
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error in Groq extraction: ${e.message}")
            return@withContext fallbackMeta
        }
    }

    private fun stripCodeFences(text: String): String {
        var clean = text.trim()

        if (clean.startsWith("```json", ignoreCase = true)) {
            clean = clean.removePrefix("```json").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").trim()
        }

        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }

        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start >= 0 && end > start) {
            clean = clean.substring(start, end + 1)
        }

        return clean
    }

    private suspend fun fetchHtmlContent(url: String): String = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string() ?: ""
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun parseOpenGraphMetadata(html: String, url: String): WebpageMetadata {
        if (html.isEmpty()) {
            val guessedTitle = url.substringAfter("://").substringBefore("/").replace("www.", "")
            val uppercaseTitle = guessedTitle.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            return WebpageMetadata(uppercaseTitle, "Web Link shared: $url", "")
        }

        var title: String? = null
        var description: String? = null
        var imageUrl: String? = null

        val metaTagRegex = """<meta\s+([^>]+)>""".toRegex(RegexOption.IGNORE_CASE)
        val propertyRegex = """\bproperty\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val nameRegex = """\bname\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
        val contentAttrRegex = """\bcontent\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)

        metaTagRegex.findAll(html).forEach { match ->
            val metaContent = match.groupValues[1]
            val propVal = propertyRegex.find(metaContent)?.groupValues?.get(1)
                ?: nameRegex.find(metaContent)?.groupValues?.get(1)
            val contentVal = contentAttrRegex.find(metaContent)?.groupValues?.get(1)

            if (propVal != null && contentVal != null) {
                val cleanProp = propVal.lowercase().trim()
                val cleanContent = contentVal.trim()
                    .replace("&amp;", "&")
                    .replace("&#39;", "'")
                    .replace("&quot;", "\"")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")

                when (cleanProp) {
                    "og:title" -> if (title == null) title = cleanContent
                    "og:description" -> if (description == null) description = cleanContent
                    "og:image" -> if (imageUrl == null) imageUrl = cleanContent
                    "description" -> if (description == null) description = cleanContent
                }
            }
        }

        if (title == null) {
            val fallbackTitleRegex = """<title>([^<]+)</title>""".toRegex(RegexOption.IGNORE_CASE)
            title = fallbackTitleRegex.find(html)?.groupValues?.get(1)?.trim()
                ?.replace("&amp;", "&")
                ?.replace("&#39;", "'")
                ?.replace("&quot;", "\"")
        }

        val finalTitle = title ?: ""
        val finalDesc = description ?: ""
        val finalImg = imageUrl ?: ""

        val processedTitle = if (finalTitle.isEmpty()) {
            val host = url.substringAfter("://").substringBefore("/").replace("www.", "")
            host.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else finalTitle

        val processedDesc = if (finalDesc.isEmpty()) {
            "Shared webpage bookmark: $url"
        } else finalDesc

        return WebpageMetadata(processedTitle, processedDesc, finalImg)
    }
}