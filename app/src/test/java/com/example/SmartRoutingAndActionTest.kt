package com.example

import com.example.data.model.ActionType
import com.example.domain.intent.IntentRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmartRoutingAndActionTest {

    private lateinit var router: IntentRouter

    @Before
    fun setUp() {
        router = IntentRouter()
    }

    @Test
    fun `whatsapp open commands are routed correctly`() {
        val cmd1 = router.resolveIntent("WhatsApp kholo", null)
        assertEquals(ActionType.WHATSAPP_OPEN, cmd1.action)
        assertTrue(router.isLocalFastPathAction(cmd1.action))

        val cmd2 = router.resolveIntent("open whatsapp", null)
        assertEquals(ActionType.WHATSAPP_OPEN, cmd2.action)

        val cmd3 = router.resolveIntent("whatsapp chalao", null)
        assertEquals(ActionType.WHATSAPP_OPEN, cmd3.action)
    }

    @Test
    fun `whatsapp message commands with colon are parsed accurately`() {
        val cmd = router.resolveIntent("Ansh ko WhatsApp par message bhejo: Kal college aana", null)
        assertEquals(ActionType.WHATSAPP_MESSAGE, cmd.action)
        assertTrue(router.isLocalFastPathAction(cmd.action))
        assertEquals("Ansh", cmd.target)
        assertEquals("Kal college aana", cmd.parameters["message"])
    }

    @Test
    fun `whatsapp message commands without colon are parsed accurately`() {
        val cmd = router.resolveIntent("Ansh ko whatsapp message bhejo kal college aana", null)
        assertEquals(ActionType.WHATSAPP_MESSAGE, cmd.action)
        assertEquals("Ansh", cmd.parameters["recipient"])
        assertEquals("kal college aana", cmd.parameters["message"])
    }

    @Test
    fun `youtube open and search commands are routed locally without hitting AI`() {
        val openCmd = router.resolveIntent("YouTube kholo", null)
        assertEquals(ActionType.YOUTUBE_OPEN, openCmd.action)
        assertTrue(router.isLocalFastPathAction(openCmd.action))

        val searchCmd1 = router.resolveIntent("YouTube par Kesariya song search karo", null)
        assertEquals(ActionType.YOUTUBE_SEARCH, searchCmd1.action)
        assertTrue(router.isLocalFastPathAction(searchCmd1.action))
        assertEquals("kesariya song", searchCmd1.target)

        val searchCmd2 = router.resolveIntent("YouTube pe DBMS tutorial chalao", null)
        assertEquals(ActionType.YOUTUBE_SEARCH, searchCmd2.action)
        assertEquals("dbms tutorial", searchCmd2.target)
    }

    @Test
    fun `phone calling commands are parsed accurately`() {
        val cmd1 = router.resolveIntent("Ansh ko call karo", null)
        assertEquals(ActionType.CALL_PHONE, cmd1.action)
        assertTrue(router.isLocalFastPathAction(cmd1.action))
        assertEquals("Ansh", cmd1.target)

        val cmd2 = router.resolveIntent("Call Mom", null)
        assertEquals(ActionType.CALL_PHONE, cmd2.action)
        assertEquals("Mom", cmd2.target)

        val cmd3 = router.resolveIntent("9876543210 ko call karo", null)
        assertEquals(ActionType.CALL_PHONE, cmd3.action)
        assertEquals("9876543210", cmd3.target)
    }

    @Test
    fun `alarm commands parse hours minutes and time of day correctly`() {
        val cmd1 = router.resolveIntent("Kal 7 baje alarm laga do", null)
        assertEquals(ActionType.SET_ALARM, cmd1.action)
        assertTrue(router.isLocalFastPathAction(cmd1.action))
        assertEquals("7", cmd1.parameters["hour"])
        assertEquals("0", cmd1.parameters["minute"])

        val cmd2 = router.resolveIntent("Subah 6:30 baje ka alarm lagao", null)
        assertEquals(ActionType.SET_ALARM, cmd2.action)
        assertEquals("6", cmd2.parameters["hour"])
        assertEquals("30", cmd2.parameters["minute"])

        val cmd3 = router.resolveIntent("Shaam ko 5 baje alarm lagao", null)
        assertEquals(ActionType.SET_ALARM, cmd3.action)
        assertEquals("17", cmd3.parameters["hour"])
        assertEquals("0", cmd3.parameters["minute"])
    }

    @Test
    fun `web search commands are routed locally`() {
        val cmd1 = router.resolveIntent("Google par DBMS normalization search karo", null)
        assertEquals(ActionType.SEARCH_WEB, cmd1.action)
        assertTrue(router.isLocalFastPathAction(cmd1.action))
        assertEquals("dbms normalization", cmd1.target)

        val cmd2 = router.resolveIntent("Web par Python tutorial search karo", null)
        assertEquals(ActionType.SEARCH_WEB, cmd2.action)
        assertEquals("python tutorial", cmd2.target)
    }

    @Test
    fun `app launcher commands are parsed correctly`() {
        val cmd1 = router.resolveIntent("Instagram kholo", null)
        assertEquals(ActionType.OPEN_APP, cmd1.action)
        assertTrue(router.isLocalFastPathAction(cmd1.action))
        assertEquals("instagram", cmd1.target)

        val cmd2 = router.resolveIntent("Calculator kholo", null)
        assertEquals(ActionType.OPEN_APP, cmd2.action)
        assertEquals("calculator", cmd2.target)

        val cmd3 = router.resolveIntent("Chrome kholo", null)
        assertEquals(ActionType.OPEN_APP, cmd3.action)
        assertEquals("chrome", cmd3.target)
    }

    @Test
    fun `android settings commands are routed to correct target`() {
        val wifiCmd = router.resolveIntent("Wi-Fi settings kholo", null)
        assertEquals(ActionType.OPEN_SETTINGS, wifiCmd.action)
        assertEquals("wifi", wifiCmd.target)

        val btCmd = router.resolveIntent("Bluetooth settings kholo", null)
        assertEquals(ActionType.OPEN_SETTINGS, btCmd.action)
        assertEquals("bluetooth", btCmd.target)

        val displayCmd = router.resolveIntent("Display settings kholo", null)
        assertEquals(ActionType.OPEN_SETTINGS, displayCmd.action)
        assertEquals("display", displayCmd.target)

        val batteryCmd = router.resolveIntent("Battery settings kholo", null)
        assertEquals(ActionType.OPEN_SETTINGS, batteryCmd.action)
        assertEquals("battery", batteryCmd.target)
    }

    @Test
    fun `confirmation responses are recognized correctly`() {
        assertTrue(router.isAffirmativeResponse("haan"))
        assertTrue(router.isAffirmativeResponse("yes"))
        assertTrue(router.isAffirmativeResponse("bhejo"))
        assertTrue(router.isAffirmativeResponse("call karo"))
        assertTrue(router.isAffirmativeResponse("theek hai"))

        assertTrue(router.isNegativeResponse("nahi"))
        assertTrue(router.isNegativeResponse("no"))
        assertTrue(router.isNegativeResponse("cancel"))
        assertTrue(router.isNegativeResponse("radd karo"))
        assertTrue(router.isNegativeResponse("mat karo"))
    }

    @Test
    fun `general questions fall through to remote AI execution`() {
        val query1 = router.resolveIntent("What is the difference between SQL and NoSQL?", null)
        assertEquals(ActionType.NONE, query1.action)
        assertFalse(router.isLocalFastPathAction(query1.action))

        val query2 = router.resolveIntent("Explain photosynthesis in simple words", null)
        assertEquals(ActionType.NONE, query2.action)
        assertFalse(router.isLocalFastPathAction(query2.action))
    }
}
