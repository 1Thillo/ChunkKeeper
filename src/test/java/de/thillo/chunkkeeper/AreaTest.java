package de.thillo.chunkkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AreaTest {

    @Test
    void rectangleContainsEveryChunkBetweenTheCorners() {
        Area area = Area.rectangle("world", 20, 20, 22, 21);
        assertEquals(6, area.chunks().size());
        assertTrue(area.chunks().contains(new KeptChunk("world", 20, 20)));
        assertTrue(area.chunks().contains(new KeptChunk("world", 22, 21)));
    }

    @Test
    void cornerOrderDoesNotMatter() {
        assertEquals(Area.rectangle("w", 5, -3, -2, 4).chunks().size(), Area.rectangle("w", -2, 4, 5, -3).chunks().size());
    }

    @Test
    void singleChunkRectangle() {
        assertEquals(1, Area.rectangle("w", 7, 7, 7, 7).chunks().size());
    }

    @Test
    void sizeMatchesTheList() {
        assertEquals(Area.rectangle("w", -4, 9, 3, 2).chunks().size(), Area.rectangleSize(-4, 9, 3, 2));
    }

    @Test
    void sizeOfHugeAreasDoesNotOverflow() {
        assertEquals(4_000_000_000_000L, Area.rectangleSize(-1_000_000, -1_000_000, 999_999, 999_999));
    }
}
