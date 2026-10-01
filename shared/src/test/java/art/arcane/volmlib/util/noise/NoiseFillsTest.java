package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NoiseFillsTest {
    private static final int POINTS = 4096;
    private static final double RANGE = 2000;
    private static final NoiseType[] FRACTURE_SOURCES = {NoiseType.SIMPLEX, NoiseType.PERLIN, NoiseType.CELLULAR};

    private final double[] xs = coordinates(11);
    private final double[] ys = coordinates(12);
    private final double[] zs = coordinates(13);

    @Test
    public void cngFill2DEqualsPerPointNoiseForFracturedSignatures() {
        for (NoiseType type : FRACTURE_SOURCES) {
            CNG noise = CNG.signature(new RNG(42), type);
            double[] out = new double[POINTS];
            noise.fill2D(xs, zs, out);
            assertEachPoint(type + " noise", out, i -> noise.noise(xs[i], zs[i]));
        }
    }

    @Test
    public void cngFillFast2DEqualsPerPointFastNoise() {
        CNG noise = CNG.signatureDouble(new RNG(7));
        double[] out = new double[POINTS];
        noise.fillFast2D(xs, zs, out);
        assertEachPoint("fast", out, i -> noise.noiseFast2D(xs[i], zs[i]));
    }

    @Test
    public void generatorFill3DEqualsPerPointNoise() {
        NoiseGenerator generator = new SimplexNoise(99);
        double[] out = new double[POINTS];
        generator.fill3D(xs, ys, zs, out);
        assertEachPoint("simplex 3D", out, i -> generator.noise(xs[i], ys[i], zs[i]));
    }

    @Test
    public void inputShorterThanOutputIsRefusedBeforeWriting() {
        double[] out = {7, 7};
        assertThrows(IllegalArgumentException.class, () -> new SimplexNoise(1).fill2D(new double[2], new double[1], out));
        assertEquals(7, out[0], 0);
    }

    @Test
    public void emptyBuffersFillNothing() {
        CNG.signature(new RNG(1)).fill2D(new double[0], new double[0], new double[0]);
    }

    private static void assertEachPoint(String label, double[] filled, java.util.function.IntToDoubleFunction expected) {
        for (int i = 0; i < filled.length; i++) {
            long want = Double.doubleToRawLongBits(expected.applyAsDouble(i));
            assertEquals(label + " point " + i, want, Double.doubleToRawLongBits(filled[i]));
        }
    }

    private static double[] coordinates(long stream) {
        SplittableRandom random = new SplittableRandom(stream);
        double[] values = new double[POINTS];
        java.util.Arrays.setAll(values, i -> (random.nextDouble() * 2 - 1) * RANGE);
        return values;
    }
}
