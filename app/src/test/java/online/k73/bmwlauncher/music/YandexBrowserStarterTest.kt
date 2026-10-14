package online.k73.bmwlauncher.music

import org.junit.Assert.assertEquals
import org.junit.Test

class YandexBrowserStarterTest {
    private val media3 = "com.yandex.music.shared.media.session.session.Media3LibraryService"

    @Test fun prefersMedia3LibraryService() =
        assertEquals(media3, YandexBrowserStarter.pickBrowserService(listOf("x.OtherBrowser", media3)))

    @Test fun takesAnyResolvedServiceWhenNoMedia3() =
        assertEquals("x.OtherBrowser", YandexBrowserStarter.pickBrowserService(listOf("x.OtherBrowser")))

    @Test fun fallsBackToLegacyNameWhenNothingResolves() =
        assertEquals(YandexBrowserStarter.LEGACY_SERVICE, YandexBrowserStarter.pickBrowserService(emptyList()))
}
