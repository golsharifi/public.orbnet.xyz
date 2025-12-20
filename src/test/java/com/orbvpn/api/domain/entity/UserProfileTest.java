package com.orbvpn.api.domain.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserProfile entity
 */
class UserProfileTest {

    @Test
    @DisplayName("setFirstName should apply title case")
    void testSetFirstNameTitleCase() {
        UserProfile profile = new UserProfile();

        profile.setFirstName("john");
        assertEquals("John", profile.getFirstName());

        profile.setFirstName("MARY");
        assertEquals("Mary", profile.getFirstName());

        profile.setFirstName("mohammad ali");
        assertEquals("Mohammad Ali", profile.getFirstName());
    }

    @Test
    @DisplayName("setLastName should apply title case")
    void testSetLastNameTitleCase() {
        UserProfile profile = new UserProfile();

        profile.setLastName("doe");
        assertEquals("Doe", profile.getLastName());

        profile.setLastName("SMITH");
        assertEquals("Smith", profile.getLastName());

        profile.setLastName("van der berg");
        assertEquals("Van Der Berg", profile.getLastName());
    }

    @Test
    @DisplayName("setFirstName and setLastName should handle null")
    void testSetNameNull() {
        UserProfile profile = new UserProfile();

        profile.setFirstName(null);
        assertNull(profile.getFirstName());

        profile.setLastName(null);
        assertNull(profile.getLastName());
    }

    @Test
    @DisplayName("setFirstName and setLastName should handle empty strings")
    void testSetNameEmpty() {
        UserProfile profile = new UserProfile();

        profile.setFirstName("");
        assertEquals("", profile.getFirstName());

        profile.setLastName("");
        assertEquals("", profile.getLastName());
    }

    @Test
    @DisplayName("setFirstName and setLastName should trim whitespace")
    void testSetNameTrimWhitespace() {
        UserProfile profile = new UserProfile();

        profile.setFirstName("  john  ");
        assertEquals("John", profile.getFirstName());

        profile.setLastName("  doe  ");
        assertEquals("Doe", profile.getLastName());
    }
}
