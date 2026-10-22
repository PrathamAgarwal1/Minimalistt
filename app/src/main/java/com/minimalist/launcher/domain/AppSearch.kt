package com.minimalist.launcher.domain

import java.text.Normalizer

/** Search is the only way to reach an app that isn't a favourite, so an empty query finds nothing. */
object AppSearch {

    private val diacritics = Regex("\\p{Mn}+")
    private val wordSplit = Regex("[\\s\\-_.:()]+")

    fun search(query: String, apps: List<LaunchableApp>, hiddenKeys: Set<String>): List<LaunchableApp> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return apps
            .asSequence()
            .filter { it.key !in hiddenKeys }
            .mapNotNull { app -> score(normalize(app.label), q)?.let { app to it } }
            .sortedWith(compareBy<Pair<LaunchableApp, Int>> { it.second }.thenBy { it.first.label.lowercase() })
            .map { it.first }
            .toList()
    }

    /** Lower is better: 0 label prefix, 1 word prefix, 2 initials, 3 anywhere. Null means no match. */
    internal fun score(label: String, query: String): Int? {
        if (label.startsWith(query)) return 0
        val words = label.split(wordSplit).filter { it.isNotEmpty() }
        if (words.any { it.startsWith(query) }) return 1
        if (query.length >= 2 && words.joinToString("") { it.take(1) }.startsWith(query)) return 2
        if (label.contains(query)) return 3
        return null
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(diacritics, "").lowercase().trim()
}
