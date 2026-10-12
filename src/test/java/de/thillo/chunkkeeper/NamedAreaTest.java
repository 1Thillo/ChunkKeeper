package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NamedAreaTest {

    @Test
    void acceptsSimpleNames() {
        assertTrue(NamedArea.isValidName("halloween"));
        assertTrue(NamedArea.isValidName("farm-2"));
        assertTrue(NamedArea.isValidName("rail_main"));
    }

    @Test
    void rejectsNamesThatAreHardToTypeOrBreakTheFile() {
        assertFalse(NamedArea.isValidName(""));
        assertFalse(NamedArea.isValidName("Halloween"));
        assertFalse(NamedArea.isValidName("my area"));
        assertFalse(NamedArea.isValidName("a.b"));
        assertFalse(NamedArea.isValidName("x".repeat(33)));
    }
}
