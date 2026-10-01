package art.arcane.volmlib.util.interpolation;

import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class LatticeNoiseGridTest {
    private static final InterpolationMethod[] PACK_METHODS = {InterpolationMethod.BILINEAR_STARCAST_3,
        InterpolationMethod.BILINEAR_STARCAST_9, InterpolationMethod.BILINEAR_STARCAST_12,
        InterpolationMethod.STARCAST_12, InterpolationMethod.NONE, InterpolationMethod.BILINEAR};
    private static final double[] SPACINGS = {4, 7.5, 16, 33.3};
    private static final int CHUNK = 16;
    private static final double STARCAST_REACH_FACTOR = 1.5;
    private static final double[][] FALLBACK_COORDINATES = {{3.5, 0}, {8_000, 0}, {-0.0, 0}, {Double.NaN, 0},
        {Double.POSITIVE_INFINITY, 8}};

    private final CNG noise = CNG.signature(new RNG(5));
    private final NoiseProvider provider = noise::noise;

    @Test
    public void gridBackedInterpolationIsBitIdenticalToDirectSamplingInsideAChunk() {
        SplittableRandom random = new SplittableRandom(3);
        for (double spacing : SPACINGS) {
            int x = randomChunkOrigin(random);
            int z = randomChunkOrigin(random);
            assertChunkMatches(gridForChunk(x, z, spacing, LatticeFill.pointwise(provider)), x, z, spacing);
        }
    }

    @Test
    public void batchFillFromCngGivesTheSameGridAsPointwiseSampling() {
        LatticeNoiseGrid batched = gridForChunk(-48, 112, 16, noise::fill2D);
        assertChunkMatches(batched, -48, 112, 16);
    }

    @Test
    public void latticeHitsNeverReachTheFallbackInsideTheBox() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        LatticeNoiseGrid grid = gridForChunk(0, 0, 8, LatticeFill.pointwise(provider), counting(fallbackCalls, provider));
        IrisInterpolation.getNoise(InterpolationMethod.BILINEAR_STARCAST_9, 5.5, 9.25, 8, grid);
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    public void offLatticeOutsideNanAndNegativeZeroUseTheFallback() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        NoiseProvider answer = counting(fallbackCalls, (x, z) -> 42);
        assertFallbackAnswersEveryMiss(gridForChunk(0, 0, 8, LatticeFill.pointwise(provider), answer));
        assertEquals(FALLBACK_COORDINATES.length, fallbackCalls.get());
    }

    private static void assertFallbackAnswersEveryMiss(LatticeNoiseGrid grid) {
        for (double[] miss : FALLBACK_COORDINATES) {
            assertEquals(42, grid.noise(miss[0], miss[1]), 0);
        }
    }

    private static int randomChunkOrigin(SplittableRandom random) {
        return random.nextInt(-2000, 2000) * CHUNK;
    }

    private static NoiseProvider counting(AtomicInteger calls, NoiseProvider delegate) {
        return (x, z) -> {
            calls.incrementAndGet();
            return delegate.noise(x, z);
        };
    }

    @Test
    public void unusableSpacingIsRejected() {
        for (double spacing : new double[]{0, -4, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> LatticeBox.covering(0, 16, 0, 16, spacing, 0));
        }
    }

    @Test
    public void boxesBeyondThePointBudgetAreRejectedBeforeAllocating() {
        assertThrows(IllegalArgumentException.class, () -> LatticeBox.covering(0, 1e7, 0, 1e7, 1, 0));
    }

    @Test
    public void invertedOrNanBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LatticeRange.covering(10, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> LatticeRange.covering(Double.NaN, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> new LatticeRange(0, 0));
    }

    private LatticeNoiseGrid gridForChunk(int chunkX, int chunkZ, double spacing, LatticeFill fill) {
        return gridForChunk(chunkX, chunkZ, spacing, fill, provider);
    }

    private static LatticeNoiseGrid gridForChunk(int x, int z, double spacing, LatticeFill fill, NoiseProvider fallback) {
        double reach = spacing * STARCAST_REACH_FACTOR;
        LatticeBox box = LatticeBox.covering(x, x + CHUNK - 1, z, z + CHUNK - 1, spacing, reach);
        return LatticeNoiseGrid.sample(box, fill, fallback);
    }

    private void assertChunkMatches(LatticeNoiseGrid grid, int chunkX, int chunkZ, double spacing) {
        for (InterpolationMethod method : PACK_METHODS) {
            for (int i = 0; i < CHUNK * CHUNK; i++) {
                assertPointMatches(method, grid, chunkX + i % CHUNK, chunkZ + i / CHUNK, spacing);
            }
        }
    }

    private void assertPointMatches(InterpolationMethod method, LatticeNoiseGrid grid, double x, double z, double spacing) {
        double expected = IrisInterpolation.getNoise(method, x, z, spacing, provider);
        double actual = IrisInterpolation.getNoise(method, x, z, spacing, grid);
        assertEquals(method + " " + spacing + " at " + x + "," + z,
            Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }
}
