package com.example.opendash.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashMenuTest {
    private val calls = listOf(
        RecentCall("Asha", "+911111111111", RecentCall.Kind.MISSED, 0L),
        RecentCall("", "+912222222222", RecentCall.Kind.OUTGOING, 0L),
    )

    @Test fun dialNeedsListThenConfirm() {
        val m = DashMenu()
        m.open(navActive = false)
        assertEquals(DashMenu.Action.LoadCalls, m.select())
        m.setCalls(calls)
        m.move(+1)
        assertEquals(DashMenu.Action.None, m.select())
        assertEquals(DashMenu.Page.CONFIRM, m.page)
        assertEquals("Call +912222222222?", m.title())
        val action = m.select()
        assertTrue(action is DashMenu.Action.Dial)
        assertEquals("+912222222222", (action as DashMenu.Action.Dial).call.number)
        assertFalse(m.isOpen)
    }

    @Test fun backStepsOutOnePageAtATime() {
        val m = DashMenu()
        m.open(navActive = true)
        m.select(); m.setCalls(calls); m.select()
        m.back(); assertEquals(DashMenu.Page.CALLS, m.page)
        m.back(); assertEquals(DashMenu.Page.ROOT, m.page)
        m.back(); assertFalse(m.isOpen)
    }

    @Test fun movingWrapsAround() {
        val m = DashMenu()
        m.open(navActive = false)
        m.move(-1)
        assertEquals(DashMenu.Action.Voice, m.select())
    }

    @Test fun stopNavigationOnlyWhileNavigating() {
        val m = DashMenu()
        m.open(navActive = false)
        assertEquals(3, m.rows(0L).size)
        m.open(navActive = true)
        assertEquals(4, m.rows(0L).size)
    }

    @Test fun emptyCallListCannotDial() {
        val m = DashMenu()
        m.open(navActive = false); m.select(); m.setCalls(emptyList(), "No recent calls")
        assertEquals(DashMenu.Action.None, m.select())
        assertEquals(DashMenu.Page.CALLS, m.page)
    }

    @Test fun ages() {
        assertEquals("now", DashMenu.age(20_000))
        assertEquals("5m", DashMenu.age(5 * 60_000))
        assertEquals("3h", DashMenu.age(3 * 3_600_000))
        assertEquals("2d", DashMenu.age(2 * 86_400_000L))
    }
}
