package com.orbvpn.api.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Utilities class
 */
class UtilitiesTest {

    @Test
    @DisplayName("Title case should capitalize first letter of single word")
    void testTitleCaseSingleWord() {
        assertEquals("John", Utilities.toTitleCase("john"));
        assertEquals("John", Utilities.toTitleCase("JOHN"));
        assertEquals("John", Utilities.toTitleCase("jOHN"));
    }

    @Test
    @DisplayName("Title case should handle multiple words")
    void testTitleCaseMultipleWords() {
        assertEquals("John Doe", Utilities.toTitleCase("john doe"));
        assertEquals("John Doe", Utilities.toTitleCase("JOHN DOE"));
        assertEquals("Mohammad Ali", Utilities.toTitleCase("mohammad ali"));
        assertEquals("Mohammad Reza", Utilities.toTitleCase("MOHAMMAD REZA"));
    }

    @Test
    @DisplayName("Title case should handle null and empty strings")
    void testTitleCaseNullAndEmpty() {
        assertNull(Utilities.toTitleCase(null));
        assertEquals("", Utilities.toTitleCase(""));
        assertEquals("", Utilities.toTitleCase("   "));
    }

    @Test
    @DisplayName("Title case should trim whitespace")
    void testTitleCaseTrimWhitespace() {
        assertEquals("John", Utilities.toTitleCase("  john  "));
        assertEquals("John Doe", Utilities.toTitleCase("  john   doe  "));
    }

    @Test
    @DisplayName("Title case should handle already correct names")
    void testTitleCaseAlreadyCorrect() {
        assertEquals("John", Utilities.toTitleCase("John"));
        assertEquals("John Doe", Utilities.toTitleCase("John Doe"));
    }

    @Test
    @DisplayName("Title case should handle single character names")
    void testTitleCaseSingleChar() {
        assertEquals("J", Utilities.toTitleCase("j"));
        assertEquals("J", Utilities.toTitleCase("J"));
    }
}
