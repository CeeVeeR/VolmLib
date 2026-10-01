package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.NoiseProgramEvaluators;
import art.arcane.volmlib.util.noise.NoiseType;
import org.junit.Test;

import java.util.Arrays;
import java.util.SplittableRandom;

import static org.junit.Assert.assertTrue;

public class NativeCngFillRoutingTest {
    private static final int POINTS = 4096;
    private static final double RANGE = 8000;

    private final double[] xs = coordinates(1);
    private final double[] ys = coordinates(2);
    private final double[] zs = coordinates(3);

    @Test
    public void testJvmRoutesFillsThroughTheNativeEvaluator() {
        assertTrue(NoiseProgramEvaluators.active() instanceof NativeNoiseProgramEvaluator);
    }

    @Test
    public void coveredAndUncoveredCngsFillExactlyLikePerPointSampling() {
        assertFillsMatch(fractured());
        assertFillsMatch(new CNG(new RNG(4), NoiseType.PERLIN, 1D, 1).scale(0.03));
    }

    /** Compares with a freshly built chain: per-point CNG itself serves its coordinate cache after a fracture changes. */
    @Test
    public void changingAFractureCngAfterAFillFillsWithTheChangedChain() {
        CNG inner = new CNG(new RNG(8), 1D, 1).scale(0.21);
        CNG outer = new CNG(new RNG(7), 1D, 1).scale(0.05).fractureWith(inner, 145);
        outer.fillFastSigned3D(xs, ys, zs, new double[POINTS]);
        inner.scale(0.47);
        assertFillsMatchReference(outer, chainWithInnerScale(0.47));
    }

    private static CNG chainWithInnerScale(double innerScale) {
        return new CNG(new RNG(7), 1D, 1).scale(0.05).fractureWith(new CNG(new RNG(8), 1D, 1).scale(innerScale), 145);
    }

    private void assertFillsMatchReference(CNG filled, CNG reference) {
        double[] volume = new double[POINTS];
        filled.fillFastSigned3D(xs, ys, zs, volume);
        for (int i = 0; i < POINTS; i++) {
            NativeSimplexKernelParityTest.assertBitIdentical("3D " + i, reference.noiseFastSigned3D(xs[i], ys[i], zs[i]), volume[i]);
        }
    }

    private void assertFillsMatch(CNG cng) {
        assertFillsMatchReference(cng, cng);
        double[] flat = new double[POINTS];
        cng.fillFastSigned2D(xs, zs, flat);
        for (int i = 0; i < POINTS; i++) {
            NativeSimplexKernelParityTest.assertBitIdentical("2D " + i, cng.noiseFastSigned2D(xs[i], zs[i]), flat[i]);
        }
        assertUnsignedFillMatches(cng);
    }

    private void assertUnsignedFillMatches(CNG cng) {
        double[] unsigned = new double[POINTS];
        cng.fillFast2D(xs, zs, unsigned);
        for (int i = 0; i < POINTS; i++) {
            NativeSimplexKernelParityTest.assertBitIdentical("unsigned 2D " + i, cng.noiseFast2D(xs[i], zs[i]), unsigned[i]);
        }
    }

    private static CNG fractured() {
        CNG inner = new CNG(new RNG(8), 1D, 1).scale(0.21).fractureWith(new CNG(new RNG(9), 1D, 1).scale(0.9), 620);
        return new CNG(new RNG(7), 0.8D, 1).scale(0.05).fractureWith(inner, 145);
    }

    private static double[] coordinates(long stream) {
        SplittableRandom random = new SplittableRandom(stream);
        double[] values = new double[POINTS];
        Arrays.setAll(values, i -> (random.nextDouble() * 2 - 1) * RANGE);
        return values;
    }
}
