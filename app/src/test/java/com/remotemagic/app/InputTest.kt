package com.remotemagic.app

import com.remotemagic.app.bluetooth.HidReports
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.protocols.AndroidTvMessages
import com.remotemagic.app.protocols.ProtoMessage
import com.remotemagic.app.protocols.SamsungClient
import com.remotemagic.app.remote.MotionMapper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class InputTest {
    @Test fun bluetoothMouseAndKeyboard() {
        assertArrayEquals(byteArrayOf(1, 127, -127, 0), HidReports.mouse(1, 500, -500, 0))
        val upper = HidReports.character('A')!!
        assertEquals(0x02.toByte(), upper.press[0])
        assertEquals(0x04.toByte(), upper.press[2])
        assertNull(HidReports.character('ع'))
        assertNull(HidReports.character('é'))
        assertEquals(0x25.toByte(), HidReports.report(RemoteKey.STAR)!!.press[2])
    }

    @Test fun samsungTextAndPointer() {
        val text = JSONObject(SamsungClient.textMessage("مرحبا")).getJSONObject("params")
        assertEquals("مرحبا", String(Base64.getDecoder().decode(text.getString("Cmd"))))
        val move = JSONObject(SamsungClient.moveMessage(5, -3)).getJSONObject("params")
        assertEquals(-3, move.getJSONObject("Position").getInt("y"))
    }

    @Test fun androidTvImeCarriesCountersAndText() {
        val batch = ProtoMessage.parse(AndroidTvMessages.imeText(4, 7, "abc")).message(21)!!
        assertEquals(4L, batch.number(1))
        assertEquals(7L, batch.number(2))
        assertEquals("abc", String(batch.message(3)!!.message(2)!!.blobs[3]!!))
    }

    @Test fun airMouseCalibrationRemovesDrift() {
        val mapper = MotionMapper()
        mapper.startCalibration()
        repeat(MotionMapper.CALIBRATION_SAMPLES) { mapper.map(0.1f, -0.1f, 0.02f) }
        assertFalse(mapper.calibrating)
        assertEquals(0 to 0, mapper.map(0.1f, -0.1f, 0.02f))
        assertTrue(mapper.map(0.1f, -1.1f, 0.02f).first > 0) // turned right
    }
}
