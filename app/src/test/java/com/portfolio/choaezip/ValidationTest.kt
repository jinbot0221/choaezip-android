package com.portfolio.choaezip
import org.junit.Assert.*
import org.junit.Test
class ValidationTest {
    @Test fun invalidInputCannotBeSubmitted() {
        assertFalse(Validation.email("user@")); assertTrue(Validation.email("fan@example.com"))
        assertFalse(Validation.nickname("나")); assertFalse(Validation.password("1234567"))
        assertTrue(Validation.password("12345678"))
        assertNotNull(Validation.content("기록", " ", true))
        assertNotNull(Validation.content("a".repeat(51), "본문", true))
        assertNull(Validation.content("a".repeat(80), "본문", false))
        assertNotNull(Validation.content("이야기", "a".repeat(3001), false))
    }
    @Test fun signupExplainsTheFailingFieldAndAcceptsCompleteInput() {
        val valid = Validation.signupErrors(" fan@example.com ", "password123", "password123", "테스터", true)
        assertTrue(valid.isEmpty())
        assertEquals(setOf("password"), Validation.signupErrors("fan@example.com", "1234567", "1234567", "테스터", true).keys)
        assertEquals(setOf("confirm"), Validation.signupErrors("fan@example.com", "password123", "different", "테스터", true).keys)
        assertEquals(setOf("agree"), Validation.signupErrors("fan@example.com", "password123", "password123", "테스터", false).keys)
    }
}
