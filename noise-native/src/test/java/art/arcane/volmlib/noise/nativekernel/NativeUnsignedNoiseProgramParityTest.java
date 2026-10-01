package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.UnsignedNoiseProgram;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;

import static org.junit.Assert.assertNotNull;

public class NativeUnsignedNoiseProgramParityTest {
    private static final int POINTS = 50_000;
    private static final double RANGE = 20_000;
    private static final double[] EDGES = {0, -0.0, 1e-310, 4.6e18, -4.6e18, 1e300, Double.NaN, Double.POSITIVE_INFINITY};

    @Test
    public void rustFillMatchesJavaProgramAndNoiseFast2DForEveryCoveredShape() {
        for (Supplier<CNG> shape : shapes()) {
            assertShapeMatches(shape, randomPoints(new SplittableRandom(shape.hashCode())));
            assertShapeMatches(shape, edgePoints());
        }
    }

    private static List<Supplier<CNG>> shapes() {
        return List.of(() -> simplex(1, 1D, 1), () -> simplex(2, 0.63D, 4).scale(0.11),
            () -> simplex(3, 1D, 1).scale(0.02).up(0.25).down(0.1).patch(1.5),
            () -> simplex(5, 1D, 1).scale(0.05).fractureWith(simplex(6, 1D, 2).scale(0.9), 93),
            NativeUnsignedNoiseProgramParityTest::deepFracture);
    }

    private static CNG deepFracture() {
        CNG inner = simplex(8, 1D, 1).scale(0.21).fractureWith(simplex(9, 1D, 1).scale(0.9), 620);
        return simplex(7, 0.8D, 1).fractureWith(inner, 145).bake();
    }

    private static void assertShapeMatches(Supplier<CNG> shape, double[][] points) {
        UnsignedNoiseProgram program = shape.get().compileUnsignedProgram();
        assertNotNull(program);
        CNG reference = shape.get();
        double[] out = rustFill(program, points);
        for (int i = 0; i < out.length; i++) {
            assertPointMatches(program, reference, points[0][i], points[1][i], out[i]);
        }
    }

    private static double[] rustFill(UnsignedNoiseProgram program, double[][] points) {
        double[] out = new double[points[0].length];
        new NativeUnsignedNoiseProgram(program).fill2D(points[0], points[1], out);
        return out;
    }

    private static void assertPointMatches(UnsignedNoiseProgram program, CNG reference, double x, double z, double fromRust) {
        NativeSimplexKernelParityTest.assertBitIdentical("program " + x + "," + z, program.sample2D(x, z), fromRust);
        NativeSimplexKernelParityTest.assertBitIdentical("cng " + x + "," + z, reference.noiseFast2D(x, z), fromRust);
    }

    private static double[][] randomPoints(SplittableRandom random) {
        double[][] points = new double[2][POINTS];
        for (double[] axis : points) {
            Arrays.setAll(axis, i -> (random.nextDouble() * 2 - 1) * RANGE);
        }
        return points;
    }

    private static double[][] edgePoints() {
        double[][] points = new double[2][EDGES.length * EDGES.length];
        for (int i = 0; i < points[0].length; i++) {
            points[0][i] = EDGES[i % EDGES.length];
            points[1][i] = EDGES[i / EDGES.length];
        }
        return points;
    }

    private static CNG simplex(long seed, double opacity, int octaves) {
        return new CNG(new RNG(seed), opacity, octaves);
    }
}
