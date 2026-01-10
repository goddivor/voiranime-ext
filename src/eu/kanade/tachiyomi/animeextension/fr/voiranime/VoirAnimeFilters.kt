package eu.kanade.tachiyomi.animeextension.fr.voiranime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object VoirAnimeFilters {

    /* ==============================
       ORDER BY
       ============================== */

    class OrderByFilter : AnimeFilter.Select<String>(
        "Trier par",
        arrayOf(
            "Pertinence",
            "Popularité",
            "Derniers ajouts",
            "Alphabet",
            "Note",
            "Vues",
            "Nouveauté",
        ),
        0,
    ) {
        fun toQuery(): String? = when (state) {
            1 -> "trending"
            2 -> "latest"
            3 -> "alphabet"
            4 -> "rating"
            5 -> "views"
            6 -> "new-manga"
            else -> null
        }
    }

    /* ==============================
       TYPE/FORMAT
       ============================== */

    class TypeFilter : AnimeFilter.Select<String>(
        "Format",
        arrayOf("Tous", "TV", "Movie", "TV Short", "OVA", "ONA", "Special"),
        0,
    ) {
        fun toQuery(): String? = when (state) {
            1 -> "TV"
            2 -> "MOVIE"
            3 -> "TV SHORT"
            4 -> "OVA"
            5 -> "ONA"
            6 -> "SPECIAL"
            else -> null
        }
    }

    /* ==============================
       LANGUAGE
       ============================== */

    class LanguageFilter : AnimeFilter.Select<String>(
        "Langue",
        arrayOf("Tous", "VF", "VOSTFR"),
        0,
    ) {
        fun toQuery(): String? = when (state) {
            1 -> "vf" // VF
            2 -> "vostfr" // VOSTFR
            else -> null
        }
    }

    /* ==============================
       YEAR
       ============================== */

    class YearFilter : AnimeFilter.Text("Année de sortie")

    /* ==============================
       STATUS
       ============================== */

    class Status(name: String) : AnimeFilter.CheckBox(name)

    class StatusFilter : AnimeFilter.Group<Status>(
        "Statut",
        listOf(
            Status("Terminé"),
            Status("En cours"),
            Status("Annulé"),
            Status("En pause"),
        ),
    ) {
        fun toQuery(): List<String> {
            val values = mutableListOf<String>()
            if (state[0].state) values.add("end")
            if (state[1].state) values.add("on-going")
            if (state[2].state) values.add("canceled")
            if (state[3].state) values.add("on-hold")
            return values
        }
    }

    /* ==============================
       GENRES
       ============================== */

    class Genre(name: String) : AnimeFilter.CheckBox(name)

    class GenreFilter : AnimeFilter.Group<Genre>(
        "Genres",
        listOf(
            Genre("Action"),
            Genre("Adventure"),
            Genre("Cartoon"),
            Genre("Chinese"),
            Genre("Comedy"),
            Genre("Drama"),
            Genre("Ecchi"),
            Genre("Fantasy"),
            Genre("Horror"),
            Genre("Mahou Shoujo"),
            Genre("Mecha"),
            Genre("Music"),
            Genre("Mystery"),
            Genre("Psychological"),
            Genre("R+"),
            Genre("Romance"),
            Genre("Sci-Fi"),
            Genre("Slice of Life"),
            Genre("Sports"),
            Genre("Supernatural"),
            Genre("Thriller"),
        ),
    ) {
        private val genreMap = mapOf(
            "Action" to "action",
            "Adventure" to "adventure",
            "Cartoon" to "cartoon",
            "Chinese" to "chinese",
            "Comedy" to "comedy",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Horror" to "horror",
            "Mahou Shoujo" to "mahou-shoujo",
            "Mecha" to "mecha",
            "Music" to "music",
            "Mystery" to "mystery",
            "Psychological" to "psychological",
            "R+" to "r",
            "Romance" to "romance",
            "Sci-Fi" to "sci-fi",
            "Slice of Life" to "slice-of-life",
            "Sports" to "sports",
            "Supernatural" to "supernatural",
            "Thriller" to "thriller",
        )

        fun toQuery(): List<String> =
            state.filter { it.state }
                .mapNotNull { genreMap[it.name] }
    }

    /* ==============================
       FILTER LIST
       ============================== */

    fun getFilterList(): AnimeFilterList = AnimeFilterList(
        OrderByFilter(),
        TypeFilter(),
        LanguageFilter(),
        YearFilter(),
        StatusFilter(),
        GenreFilter(),
    )
}
