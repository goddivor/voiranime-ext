package eu.kanade.tachiyomi.animeextension.fr.voiranime

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.filemoonextractor.FilemoonExtractor
import eu.kanade.tachiyomi.lib.streamtapeextractor.StreamTapeExtractor
import eu.kanade.tachiyomi.lib.vidmolyextractor.VidMolyExtractor
import eu.kanade.tachiyomi.lib.vkextractor.VkExtractor
import eu.kanade.tachiyomi.lib.voeextractor.VoeExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class VoirAnime : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "VoirAnime"
    override val baseUrl = "https://v6.voiranime.com"
    override val lang = "fr"
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", baseUrl)

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    companion object {
        const val PREFIX_SEARCH = "slug:"

        private const val PREF_THUMBNAIL_QUALITY = "thumbnail_quality"
        private const val DEFAULT_THUMBNAIL_QUALITY = "193x278"
    }

    // ============================== Settings ===============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_THUMBNAIL_QUALITY
            title = "Qualité des thumbnails"
            entries = arrayOf(
                "110x150 (Très petite)",
                "125x180 (Petite)",
                "175x238 (Moyenne basse)",
                "193x278 (Par défaut)",
                "350x476 (Moyenne haute)",
                "460x630 (Grande)",
                "Originale",
            )
            entryValues = arrayOf(
                "110x150",
                "125x180",
                "175x238",
                "193x278",
                "350x476",
                "460x630",
                "original",
            )
            setDefaultValue(DEFAULT_THUMBNAIL_QUALITY)
            summary = "Choisissez la qualité des images d'aperçu. Des images de meilleure qualité consommeront plus de données.\n\nActuel: %s"
        }.also { screen.addPreference(it) }
    }

    private fun transformThumbnailUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url

        val quality = preferences.getString(PREF_THUMBNAIL_QUALITY, DEFAULT_THUMBNAIL_QUALITY)!!

        // Si la qualité est celle par défaut, retourner l'URL telle quelle
        if (quality == DEFAULT_THUMBNAIL_QUALITY) return url

        // Pattern pour matcher les dimensions dans l'URL (ex: -193x278.jpg)
        val dimensionPattern = """-\d+x\d+\.jpg""".toRegex()

        return if (quality == "original") {
            // Supprimer les dimensions pour obtenir l'image originale
            url.replace(dimensionPattern, ".jpg")
        } else {
            // Remplacer les dimensions par celles choisies
            url.replace(dimensionPattern, "-$quality.jpg")
        }
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=trending", headers)

    override fun popularAnimeSelector(): String = "div.row.c-tabs-item__content"

    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val anchor = element.selectFirst("div.post-title h3 a")!!
        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text()
        thumbnail_url = element.selectFirst("div.tab-thumb img")?.let {
            it.attr("abs:src").ifEmpty { it.attr("abs:data-src") }
        }?.let { transformThumbnailUrl(it) }
    }

    override fun popularAnimeNextPageSelector(): String = "a.nextpostslink"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=latest", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()

        // Handle slug search from deep links
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SEARCH)
            return GET("$baseUrl/anime/$slug/", headers)
        }

        url.addQueryParameter("s", query)
        url.addQueryParameter("post_type", "wp-manga")

        // Check if TypeFilter is selected to avoid conflict with LanguageFilter
        var typeFilterUsed = false

        filters.forEach { filter ->
            when (filter) {
                is VoirAnimeFilters.OrderByFilter -> {
                    filter.toQuery()?.let { url.addQueryParameter("m_orderby", it) }
                }
                is VoirAnimeFilters.TypeFilter -> {
                    filter.toQuery()?.let {
                        // Replace spaces with + for "TV SHORT" -> "TV+SHORT"
                        url.addQueryParameter("type", it.replace(" ", "+"))
                        typeFilterUsed = true
                    }
                }
                is VoirAnimeFilters.LanguageFilter -> {
                    filter.toQuery()?.let { language ->
                        // If no type filter, add empty type parameter
                        if (!typeFilterUsed) {
                            url.addQueryParameter("type", "")
                        }
                        url.addQueryParameter("language", language)
                    }
                }
                is VoirAnimeFilters.YearFilter -> {
                    val year = filter.state.trim()
                    if (year.isNotEmpty()) {
                        url.addQueryParameter("release", year)
                    }
                }
                is VoirAnimeFilters.StatusFilter -> {
                    filter.toQuery().forEach { url.addQueryParameter("status[]", it) }
                }
                is VoirAnimeFilters.GenreFilter -> {
                    filter.toQuery().forEach { url.addQueryParameter("genre[]", it) }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================ Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst(".post-title h1")?.text().orEmpty()

        description = document.selectFirst("div.description-summary div.summary__content")?.text()
            ?: document.selectFirst("div.summary__content")?.text()
            ?: ""

        genre = document.select("div.genres-content a").joinToString { it.text() }

        author = document.select("div.author-content a").joinToString { it.text() }

        status = document.select("div.post-status div.post-content_item:contains(Statut) div.summary-content")
            .firstOrNull()?.text().let { statusText ->
                when {
                    statusText?.contains("En cours", ignoreCase = true) == true -> SAnime.ONGOING
                    statusText?.contains("Terminé", ignoreCase = true) == true -> SAnime.COMPLETED
                    else -> SAnime.UNKNOWN
                }
            }

        thumbnail_url = document.selectFirst("div.summary_image img")?.let {
            it.attr("abs:src").ifEmpty { it.attr("abs:data-src") }
        }?.let { transformThumbnailUrl(it) }
    }

    // ============================== Episodes ===============================

    override fun episodeListSelector(): String = "li.wp-manga-chapter"

    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val anchor = element.selectFirst("a")!!
        setUrlWithoutDomain(anchor.attr("href"))

        // Extract episode number from the anchor text
        // Format is usually like "Anime Name - 21 VF - 21" or "Solo Leveling 2 - 13 VOSTFR - 13"
        val rawName = anchor.text().trim()

        // Find the LAST number in the text (to avoid grabbing "2" from "Solo Leveling 2")
        val numberMatch = Regex("""\d+""").findAll(rawName).lastOrNull()
        episode_number = numberMatch?.value?.toFloatOrNull() ?: 0f

        // Format name as "Episode X"
        name = if (episode_number > 0) {
            "Episode ${episode_number.toInt()}"
        } else {
            rawName // Fallback to original name if no number found
        }

        // Try to get the date if available
        val dateText = element.selectFirst("span.chapter-release-date")?.text()
        date_upload = parseDate(dateText)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        // Return episodes in descending order (latest first: 21, 20, 19... 1)
        return super.episodeListParse(response)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        return try {
            when {
                // Handle relative dates: "2 days ago", "3 hours ago", etc.
                "ago" in dateStr -> {
                    val number = dateStr.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val currentTime = System.currentTimeMillis()

                    when {
                        "second" in dateStr -> currentTime - (number * 1000L)
                        "minute" in dateStr -> currentTime - (number * 60 * 1000L)
                        "hour" in dateStr -> currentTime - (number * 60 * 60 * 1000L)
                        "day" in dateStr -> currentTime - (number * 24 * 60 * 60 * 1000L)
                        "week" in dateStr -> currentTime - (number * 7 * 24 * 60 * 60 * 1000L)
                        "month" in dateStr -> currentTime - (number * 30 * 24 * 60 * 60 * 1000L)
                        "year" in dateStr -> currentTime - (number * 365 * 24 * 60 * 60 * 1000L)
                        else -> 0L
                    }
                }
                // Handle absolute dates: "December 14, 2025"
                else -> {
                    val format = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.ENGLISH)
                    format.parse(dateStr.trim())?.time ?: 0L
                }
            }
        } catch (e: Exception) {
            0L
        }
    }

    // ============================== Videos =================================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        // Initialize extractors for the 5 different players
        val vidmolyExtractor = VidMolyExtractor(client, headers)
        val filemoonExtractor = FilemoonExtractor(client)
        val voeExtractor = VoeExtractor(client, headers)
        val streamtapeExtractor = StreamTapeExtractor(client)
        val vkExtractor = VkExtractor(client, headers)

        // Extract video sources from JavaScript object 'thisChapterSources'
        val scriptContent = document.select("script:containsData(thisChapterSources)").firstOrNull()?.data()

        if (scriptContent != null) {
            // Extract iframe URLs from the thisChapterSources JavaScript object
            extractIframeUrls(scriptContent).forEach { (playerName, iframeUrl) ->
                try {
                    val extractedVideos = when {
                        // LECTEUR myTV - Vidmoly
                        iframeUrl.contains("vidmoly") -> {
                            vidmolyExtractor.videosFromUrl(iframeUrl, "$playerName: ")
                        }
                        // LECTEUR MOON - Filemoon (f16px)
                        iframeUrl.contains("f16px") || iframeUrl.contains("filemoon") -> {
                            filemoonExtractor.videosFromUrl(iframeUrl, "$playerName: ", headers)
                        }
                        // LECTEUR VOE
                        iframeUrl.contains("voe.sx") || iframeUrl.contains("voe") -> {
                            voeExtractor.videosFromUrl(iframeUrl, "$playerName: ")
                        }
                        // LECTEUR Stape - StreamTape
                        iframeUrl.contains("streamtape") -> {
                            streamtapeExtractor.videosFromUrl(iframeUrl, "$playerName: ")
                        }
                        // LECTEUR FHD1 - VK/Mail.ru
                        iframeUrl.contains("vk.com") || iframeUrl.contains("vkvideo") || iframeUrl.contains("mail.ru") -> {
                            vkExtractor.videosFromUrl(iframeUrl, "$playerName: ")
                        }
                        else -> {
                            // Unknown player, skip it
                            emptyList()
                        }
                    }
                    videos.addAll(extractedVideos)
                } catch (e: Exception) {
                    // Log error but don't add non-playable iframe URLs
                    // The extractor failed, so skip this source
                }
            }
        }

        return videos
    }

    /**
     * Extracts iframe URLs from the thisChapterSources JavaScript object
     * Format: var thisChapterSources = {"LECTEUR myTV":"<iframe src=\"url\" ...>", ...}
     */
    private fun extractIframeUrls(scriptContent: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()

        // Regex to match: "PLAYER_NAME":"<iframe src=\"URL\" ...>"
        val regex = """"(LECTEUR [^"]+)":"<iframe src=\\"([^"]+)\\\"""".toRegex()

        regex.findAll(scriptContent).forEach { matchResult ->
            val playerName = matchResult.groupValues[1]
            val iframeUrl = matchResult.groupValues[2].replace("\\/", "/")
            results.add(Pair(playerName, iframeUrl))
        }

        return results
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException("Not used")

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // =============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = VoirAnimeFilters.getFilterList()
}
