package com.minimalist.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchTest {

    private fun app(label: String, key: String = "pkg.${label.lowercase().replace(" ", "")}/.Main") =
        LaunchableApp(key = key, packageName = key.substringBefore('/'), label = label, userSerial = 0)

    private val apps = listOf(
        app("Calculator"),
        app("Calendar"),
        app("Google Maps"),
        app("Phone"),
        app("Photos"),
        app("Café Finder"),
    )

    @Test
    fun emptyQueryFindsNothing() {
        assertTrue(AppSearch.search("", apps, emptySet()).isEmpty())
        assertTrue(AppSearch.search("   ", apps, emptySet()).isEmpty())
    }

    @Test
    fun hiddenAppsNeverAppear() {
        val hidden = setOf(apps.first { it.label == "Phone" }.key)
        val labels = AppSearch.search("ph", apps, hidden).map { it.label }
        assertEquals(listOf("Photos"), labels)
    }

    @Test
    fun prefixMatchesRankAboveWordAndSubstringMatches() {
        val labels = AppSearch.search("ma", apps, emptySet()).map { it.label }
        assertEquals(listOf("Google Maps"), labels)

        val ca = AppSearch.search("ca", apps, emptySet()).map { it.label }
        assertEquals(listOf("Café Finder", "Calculator", "Calendar"), ca)
    }

    @Test
    fun matchesIgnoreCaseAndAccents() {
        assertEquals(listOf("Café Finder"), AppSearch.search("CAFE", apps, emptySet()).map { it.label })
    }

    @Test
    fun scoreOrdering() {
        assertEquals(0, AppSearch.score("google maps", "goo"))
        assertEquals(1, AppSearch.score("google maps", "map"))
        assertEquals(2, AppSearch.score("google maps", "gm"))
        assertEquals(3, AppSearch.score("google maps", "ogle"))
        assertNull(AppSearch.score("google maps", "xyz"))
    }
}
