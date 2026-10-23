package dev.glacia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreAccountTest {
    @Test fun showsVerifiedTelegramNameAndUsernameWithoutInventingOne() {
        val user = CorePerson(id = 101, firstName = "Aanya", lastName = "Sharma",
            username = "aanya", phone = "not_public")
        assertEquals("Aanya Sharma", user.displayName)
        assertEquals("aanya", user.username)
    }

    @Test fun emptyNamesNeverCrashContactList() {
        val person = CorePerson(105, firstName = "", lastName = "")
        assertEquals("Telegram user", person.displayName)
        assertTrue(person.username.isBlank())
    }

    @Test fun usernameMayBeAbsentAndShouldNotBeGuessedFromPhone() {
        val person = CorePerson(107, firstName = "A", phone = "9999999999")
        assertEquals("", person.username)
    }
}
