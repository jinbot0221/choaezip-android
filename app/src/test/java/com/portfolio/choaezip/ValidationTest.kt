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
}
