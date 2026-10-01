package art.arcane.volmlib.util.cache;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldCache2DDoubleOfferTest {
    private static final int CHUNKS = 64;

    @Test
    public void offeredValueIsServedWithoutCallingTheResolver() {
        AtomicInteger resolved = new AtomicInteger();
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> resolved.incrementAndGet(), CHUNKS);
        assertTrue(cache.offer(-17, 40, 2.5));
        assertEquals(2.5, cache.get(-17, 40), 0);
        assertEquals(0, resolved.get());
    }

    @Test
    public void offerNeverReplacesAResolvedValue() {
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> 7.0, CHUNKS);
        assertEquals(7.0, cache.get(3, 3), 0);
        assertFalse(cache.offer(3, 3, 99.0));
        assertEquals(7.0, cache.get(3, 3), 0);
    }

    @Test
    public void secondOfferForTheSameSlotIsRejected() {
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> 0.0, CHUNKS);
        assertTrue(cache.offer(100, -100, 1.0));
        assertFalse(cache.offer(100, -100, 2.0));
        assertEquals(1.0, cache.get(100, -100), 0);
    }

    @Test
    public void offersStayInTheirOwnSlotAcrossChunkBoundaries() {
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> -1.0, CHUNKS);
        cache.offer(15, 0, 15.0);
        cache.offer(16, 0, 16.0);
        cache.offer(-1, 0, -1.5);
        assertEquals(15.0, cache.get(15, 0), 0);
        assertEquals(16.0, cache.get(16, 0), 0);
        assertEquals(-1.5, cache.get(-1, 0), 0);
        assertEquals(-1.0, cache.get(0, 0), 0);
    }

    @Test
    public void isResolvedReportsOnlyFilledSlotsWithoutCreatingChunks() {
        WorldCache2DDouble cache = new WorldCache2DDouble((x, z) -> 1.0, CHUNKS);
        assertFalse(cache.isResolved(40, 40));
        assertEquals(0, cache.getSize());
        cache.get(40, 40);
        cache.offer(-3, 9, 2.0);
        assertTrue(cache.isResolved(40, 40));
        assertTrue(cache.isResolved(-3, 9));
        assertFalse(cache.isResolved(41, 40));
    }
}
