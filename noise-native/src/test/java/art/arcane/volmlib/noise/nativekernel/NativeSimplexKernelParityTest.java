package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.noise.JavaSimplexKernel;
import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;

public class NativeSimplexKernelParityTest {
    private static final int RANDOM_POINTS_PER_SCALE = 400_000;
    private static final double[] COORDINATE_SCALES = {1, 1e3, 1e6, 1e12};
    private static final long[] EDGE_SEEDS = {0, 1, -1, 1337, Long.MIN_VALUE, Long.MAX_VALUE};
    private static final double[] EDGE_COORDINATES = {0.0, -0.0, 1e-310, -1e-310, 0.5, -0.5, 1 << 26,
        9007199254740992.0, 4.6e18, -4.6e18, 1e19, -1e19, Double.MAX_VALUE, Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY, Double.NaN};

    private final JavaSimplexKernel java = new JavaSimplexKernel();
    private final NativeSimplexKernel rust = new NativeSimplexKernel();

    @Test
    public void randomPointsMatchBitForBitAcrossCoordinateScales() {
        SplittableRandom random = new SplittableRandom(7);
        for (double scale : COORDINATE_SCALES) {
            for (int point = 0; point < RANDOM_POINTS_PER_SCALE; point++) {
                assertSamePointMatches(random.nextLong(), scaled(random, scale), scaled(random, scale), scaled(random, scale));
            }
        }
    }

    @Test
    public void edgeCoordinatesMatchIncludingSaturationInfinityAndNan() {
        for (long seed : EDGE_SEEDS) {
            for (double x : EDGE_COORDINATES) {
                for (double y : EDGE_COORDINATES) {
                    assertSamePointMatches(seed, x, y, -y);
                }
            }
        }
    }

    private void assertSamePointMatches(long seed, double x, double y, double z) {
        assertBitIdentical("2D " + seed + " " + x + " " + y, java.simplex2D(seed, x, y), rust.simplex2D(seed, x, y));
        assertBitIdentical("3D " + seed + " " + x + " " + y + " " + z, java.simplex3D(seed, x, y, z), rust.simplex3D(seed, x, y, z));
    }

    private static double scaled(SplittableRandom random, double scale) {
        return (random.nextDouble() * 2 - 1) * scale;
    }

    static void assertBitIdentical(String where, double expected, double actual) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) {
            return;
        }
        assertEquals(where, Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }
}
