package com.minimalist.launcher.domain

import com.minimalist.launcher.domain.websites.AppDomains
import com.minimalist.launcher.domain.websites.DomainMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsitesTest {

    @Test
    fun hostFromWhatBrowsersShow() {
        assertEquals("instagram.com", DomainMatcher.host("instagram.com"))
        assertEquals("instagram.com", DomainMatcher.host("https://www.instagram.com/reel/abc?x=1"))
        assertEquals("m.youtube.com", DomainMatcher.host("m.youtube.com/watch?v=123"))
        assertEquals("youtu.be", DomainMatcher.host("HTTPS://YOUTU.BE/xyz"))
        assertEquals("example.com", DomainMatcher.host("http://user@example.com:8080/path"))
    }

    @Test
    fun searchTermsAreNotHosts() {
        assertNull(DomainMatcher.host("how to focus"))
        assertNull(DomainMatcher.host("youtube"))
        assertNull(DomainMatcher.host(""))
        assertNull(DomainMatcher.host(null))
    }

    @Test
    fun subdomainsMatchTheirDomain() {
        val blocked = setOf("youtube.com", "youtu.be")
        assertEquals("youtube.com", DomainMatcher.match("m.youtube.com", blocked))
        assertEquals("youtube.com", DomainMatcher.match("youtube.com", blocked))
        assertEquals("youtu.be", DomainMatcher.match("youtu.be", blocked))
        assertNull(DomainMatcher.match("notyoutube.com", blocked))
        assertNull(DomainMatcher.match("youtube.com.evil.example", blocked))
    }

    @Test
    fun blockingAnAppBlocksItsSites() {
        val domains = AppDomains.domainsFor(setOf("com.google.android.youtube"), setOf("news.example"))
        assertTrue("youtube.com" in domains)
        assertTrue("youtu.be" in domains)
        assertTrue("news.example" in domains)
        assertTrue(AppDomains.domainsFor(emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun customDomainsAreTidied() {
        assertEquals("reddit.com", DomainMatcher.normalizeDomain("https://www.Reddit.com/r/x"))
        assertNull(DomainMatcher.normalizeDomain("reddit"))
    }
}
