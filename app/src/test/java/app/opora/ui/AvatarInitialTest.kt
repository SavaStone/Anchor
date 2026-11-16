package app.opora.ui

import app.opora.ui.screens.avatarInitial
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarInitialTest {
    @Test
    fun emojiInitialIsKeptWhole() {
        assertEquals("😀", avatarInitial("😀 Саша"))
    }

    @Test
    fun letterIsUppercased() {
        assertEquals("С", avatarInitial("саша"))
    }

    @Test
    fun blankNameHasNoInitial() {
        assertEquals("", avatarInitial("   "))
    }
}
