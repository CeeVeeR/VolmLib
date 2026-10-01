package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.SignedNoiseProgram;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class NativeSignedNoiseProgramParityTest {
    private static final int POINTS = 50_000;
    private static final double RANGE = 20_000;
    private static final double[] EDGES = {0, -0.0, 1e-310, -1e-310, 4.6e18, -4.6e18, 1e300, Double.NaN,
        Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

    @Test
    public void rustFillMatchesJavaProgramAndCngForEveryCoveredShape() {
        for (Supplier<CNG> shape : shapes()) {
            assertShapeMatches(shape, randomPoints(new SplittableRandom(shape.hashCode())));
            assertShapeMatches(shape, edgePoints());
        }
    }

    @Test
    public void mismatchedLengthsAreRefusedBeforeCallingNative() {
        NativeSignedNoiseProgram program = new NativeSignedNoiseProgram(simplex(1, 1D, 1).compileSignedProgram());
        double[] two = new double[2];
        assertThrows(IllegalArgumentException.class, () -> program.fill2D(two, new double[3], two.clone()));
    }

    private static List<Supplier<CNG>> shapes() {
        return List.of(() -> simplex(1, 1D, 1), () -> simplex(2, 0.63D, 1).scale(0.11), () -> simplex(4, 1D, 5).scale(0.02),
            () -> simplex(5, 1D, 1).scale(0.05).fractureWith(simplex(6, 1D, 2).scale(0.9), 93),
            () -> simplex(7, 0.8D, 1).fractureWith(simplex(8, 1D, 1).scale(0.21)
                .fractureWith(simplex(9, 1D, 1).scale(0.9), 620), 145).bake());
    }

    private static void assertShapeMatches(Supplier<CNG> shape, double[][] points) {
        SignedNoiseProgram program = shape.get().compileSignedProgram();
        assertNotNull(program);
        CNG reference = shape.get();
        assertFlatMatches(new NativeSignedNoiseProgram(program), program, reference, points);
        assertVolumeMatches(new NativeSignedNoiseProgram(program), program, reference, points);
    }

    private static void assertFlatMatches(NativeSignedNoiseProgram rust, SignedNoiseProgram java, CNG cng, double[][] p) {
        double[] out = new double[p[0].length];
        rust.fill2D(p[0], p[2], out);
        for (int i = 0; i < out.length; i++) {
            assertBoth("2D " + i, java.sample2D(p[0][i], p[2][i]), cng.noiseFastSigned2D(p[0][i], p[2][i]), out[i]);
        }
    }

    private static void assertVolumeMatches(NativeSignedNoiseProgram rust, SignedNoiseProgram java, CNG cng, double[][] p) {
        double[] out = new double[p[0].length];
        rust.fill3D(p[0], p[1], p[2], out);
        for (int i = 0; i < out.length; i++) {
            double x = p[0][i], y = p[1][i], z = p[2][i];
            assertBoth("3D " + i, java.sample3D(x, y, z), cng.noiseFastSigned3D(x, y, z), out[i]);
        }
    }

    private static void assertBoth(String label, double fromProgram, double fromCng, double fromRust) {
        NativeSimplexKernelParityTest.assertBitIdentical(label + " program", fromProgram, fromRust);
        NativeSimplexKernelParityTest.assertBitIdentical(label + " cng", fromCng, fromRust);
    }

    private static double[][] randomPoints(SplittableRandom random) {
        double[][] points = new double[3][POINTS];
        for (double[] axis : points) {
            Arrays.setAll(axis, i -> (random.nextDouble() * 2 - 1) * RANGE);
        }
        return points;
    }

    private static double[][] edgePoints() {
        int count = EDGES.length * EDGES.length;
        double[][] points = new double[3][count];
        for (int i = 0; i < count; i++) {
            points[0][i] = EDGES[i % EDGES.length];
            points[1][i] = EDGES[i / EDGES.length];
            points[2][i] = -EDGES[i % EDGES.length];
        }
        return points;
    }

    private static CNG simplex(long seed, double opacity, int octaves) {
        return new CNG(new RNG(seed), opacity, octaves);
    }
}
