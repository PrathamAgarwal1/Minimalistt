package com.minimalist.launcher.domain.websites

/** Sites that belong to apps, so blocking the app blocks its website too. */
object AppDomains {

    val MAP: Map<String, Set<String>> = mapOf(
        "com.instagram.android" to setOf("instagram.com"),
        "com.instagram.barcelona" to setOf("threads.net", "threads.com"),
        "com.google.android.youtube" to setOf("youtube.com", "youtu.be", "youtube-nocookie.com"),
        "com.google.android.apps.youtube.music" to setOf("music.youtube.com"),
        "com.facebook.katana" to setOf("facebook.com", "fb.com", "fb.watch"),
        "com.facebook.lite" to setOf("facebook.com", "fb.com"),
        "com.facebook.orca" to setOf("messenger.com"),
        "com.zhiliaoapp.musically" to setOf("tiktok.com"),
        "com.ss.android.ugc.trill" to setOf("tiktok.com"),
        "com.snapchat.android" to setOf("snapchat.com"),
        "com.twitter.android" to setOf("x.com", "twitter.com", "t.co"),
        "com.reddit.frontpage" to setOf("reddit.com", "redd.it"),
        "com.pinterest" to setOf("pinterest.com", "pin.it"),
        "com.linkedin.android" to setOf("linkedin.com"),
        "com.netflix.mediaclient" to setOf("netflix.com"),
        "com.amazon.avod.thirdpartyclient" to setOf("primevideo.com"),
        "in.startv.hotstar" to setOf("hotstar.com"),
        "com.jio.media.ondemand" to setOf("jiocinema.com"),
        "tv.twitch.android.app" to setOf("twitch.tv"),
        "com.discord" to setOf("discord.com", "discord.gg"),
        "in.mohalla.sharechat" to setOf("sharechat.com"),
        "in.mohalla.video" to setOf("mojapp.in"),
        "com.tumblr" to setOf("tumblr.com"),
        "com.quora.android" to setOf("quora.com"),
        "com.spotify.music" to setOf("open.spotify.com"),
    )

    /** Domains to block when these apps are blocked, plus the custom ones. */
    fun domainsFor(blockedPackages: Set<String>, custom: Set<String>): Set<String> =
        blockedPackages.flatMap { MAP[it].orEmpty() }.toSet() + custom
}

object DomainMatcher {

    private val scheme = Regex("^[a-z][a-z0-9+.-]*://")

    /**
     * The host of whatever a browser's address bar shows: "m.youtube.com/watch?v=…",
     * "https://www.instagram.com/" or a search term. Null when it isn't a web address.
     */
    fun host(addressBar: String?): String? {
        val text = addressBar?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && ' ' !in it } ?: return null
        val host = text.replace(scheme, "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .removePrefix("www.")
        return host.takeIf { '.' in it && !it.startsWith('.') && !it.endsWith('.') }
    }

    /** The blocked domain [host] falls under, if any: "m.youtube.com" matches "youtube.com". */
    fun match(host: String?, blocked: Set<String>): String? {
        host ?: return null
        return blocked.firstOrNull { domain -> host == domain || host.endsWith(".$domain") }
    }

    /** Tidies what someone types as a custom domain: "https://www.Reddit.com/r/x" becomes "reddit.com". */
    fun normalizeDomain(input: String): String? = host(input)
}

/** Address bar view ids, by browser package. Incognito tabs use the same views. */
object BrowserBars {

    val VIEW_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
        "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
        "com.chrome.dev" to listOf("com.chrome.dev:id/url_bar"),
        "com.chrome.canary" to listOf("com.chrome.canary:id/url_bar"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.brave.browser_beta" to listOf("com.brave.browser_beta:id/url_bar"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
        "org.mozilla.firefox" to listOf(
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/url_bar_title",
        ),
        "org.mozilla.firefox_beta" to listOf("org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view"),
        "org.mozilla.fenix" to listOf("org.mozilla.fenix:id/mozac_browser_toolbar_url_view"),
    )
}
