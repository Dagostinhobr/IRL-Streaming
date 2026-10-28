package app.streamlink

import app.streamlink.data.StreamProtocol
import app.streamlink.data.StreamTarget
import app.streamlink.util.DeepLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class DeepLinkParserTest {

    @Test
    fun `parses encoded moblin deep link`() {
        val payload = """{"streams":[{"name":"Casa","url":"rtmp://live.twitch.tv/app/abc123"}]}"""
        val encoded = URLEncoder.encode(payload, "UTF-8")
        val result = DeepLinkParser.parse("moblin://?$encoded")

        assertNotNull(result)
        assertEquals(1, result!!.targets.size)
        assertEquals("Casa", result.targets.first().name)
        assertEquals("rtmp://live.twitch.tv/app/abc123", result.targets.first().url)
    }

    @Test
    fun `parses unencoded streamlink deep link and codec`() {
        val payload = """
            {"streams":[{"name":"SRT","url":"srt://host:5000?streamid=1","video":{"codec":"H.265/HEVC"}}]}
        """.trimIndent()
        val result = DeepLinkParser.parse("streamlink://?$payload")

        assertNotNull(result)
        assertEquals(1, result!!.targets.size)
        assertEquals(StreamProtocol.SRT, result.targets.first().protocol)
        assertEquals(app.streamlink.data.CodecSetting.H265, result.codec)
    }

    @Test
    fun `rejects unrelated urls and empty payloads`() {
        assertNull(DeepLinkParser.parse("https://example.com"))
        assertNull(DeepLinkParser.parse("moblin://?"))
        assertNull(DeepLinkParser.parse("moblin://?%7B%22streams%22%3A%5B%5D%7D"))
    }

    @Test
    fun `rtmp endpoint appends stream key`() {
        val target = StreamTarget(
            name = "Twitch",
            url = "rtmp://live.twitch.tv/app/",
            streamKey = "minha-chave"
        )
        assertEquals("rtmp://live.twitch.tv/app/minha-chave", target.endpoint())
        assertTrue(target.displayEndpoint().endsWith("••••••••"))
    }

    @Test
    fun `srt endpoint keeps full url and ignores stream key`() {
        val target = StreamTarget(
            name = "SRT",
            url = "srt://example.com:5000?streamid=xyz",
            streamKey = "ignorada"
        )
        assertEquals(StreamProtocol.SRT, target.protocol)
        assertEquals("srt://example.com:5000?streamid=xyz", target.endpoint())
    }
}
