package app.zenith.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureActionTest {
    @Test fun storedNameReadsBack() {
        assertEquals(GestureAction.QuickSettings, GestureAction.parse("QuickSettings", GestureAction.None))
    }

    @Test fun missingNameGivesDefault() {
        assertEquals(GestureAction.Notifications, GestureAction.parse(null, GestureAction.Notifications))
    }

    @Test fun unknownNameGivesDefault() {
        assertEquals(GestureAction.None, GestureAction.parse("Flashlight", GestureAction.None))
    }
}
