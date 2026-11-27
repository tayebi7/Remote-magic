package com.remotemagic.app

import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp
import com.remotemagic.app.voice.VoiceCommand
import com.remotemagic.app.voice.VoiceCommandParser.parse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandTest {
    private fun key(k: RemoteKey) = VoiceCommand.Key(k)

    @Test fun arabic() {
        assertEquals(VoiceCommand.Open(TvApp.YOUTUBE), parse("افتح YouTube"))
        assertEquals(VoiceCommand.Open(TvApp.NETFLIX), parse("افتح نتفليكس"))
        assertEquals(key(RemoteKey.VOLUME_UP), parse("ارفع الصوت"))
        assertEquals(key(RemoteKey.VOLUME_DOWN), parse("اخفض الصوت"))
        assertEquals(key(RemoteKey.MUTE), parse("أغلق الصوت"))
        assertEquals(key(RemoteKey.CHANNEL_UP), parse("انتقل إلى القناة التالية"))
        assertEquals(key(RemoteKey.HOME), parse("ارجع إلى الصفحة الرئيسية"))
        assertEquals(key(RemoteKey.SETTINGS), parse("افتح الإعدادات"))
        assertEquals(VoiceCommand.PowerOn, parse("شغل التلفاز"))
        assertEquals(key(RemoteKey.POWER), parse("أوقف التلفاز"))
    }

    @Test fun frenchAndEnglish() {
        assertEquals(key(RemoteKey.VOLUME_UP), parse("Monte le son"))
        assertEquals(key(RemoteKey.CHANNEL_UP), parse("chaîne suivante"))
        assertEquals(key(RemoteKey.POWER), parse("éteins la télé"))
        assertEquals(key(RemoteKey.VOLUME_DOWN), parse("volume down"))
        assertEquals(VoiceCommand.PowerOn, parse("turn on the TV"))
        assertNull(parse("what is the weather"))
    }
}
