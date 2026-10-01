package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.SplittableRandom;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class UnsignedNoiseProgramTest {
    private static final int POINTS = 20_000;
    private static final double RANGE = 5000;
    private static final double[] EDGES = {0, -0.0, 1e-310, 4.6e18, -4.6e18, Double.NaN, Double.POSITIVE_INFINITY};

    @Test
    public void unfracturedShapesMatchNoiseFast2DBitForBit() {
        assertProgramMatches("plain", () -> simplex(1, 1D, 1));
        assertProgramMatches("opacity octaves", () -> simplex(3, 0.63D, 4).scale(0.11));
        assertProgramMatches("post adjusted", () -> simplex(4, 1D, 1).scale(0.02).up(0.25).down(0.1).patch(1.5));
    }

    @Test
    public void fracturedShapesMatchNoiseFast2DBitForBit() {
        assertProgramMatches("fracture", () -> simplex(5, 1D, 1).scale(0.05).fractureWith(simplex(6, 1D, 2).scale(0.9), 93));
        assertProgramMatches("deep fracture", UnsignedNoiseProgramTest::deepFracture);
    }

    @Test
    public void edgeCoordinatesMatchIncludingTheNegativeZeroPostStep() {
        UnsignedNoiseProgram program = deepFracture().compileUnsignedProgram();
        CNG reference = deepFracture();
        for (double x : EDGES) {
            for (double z : EDGES) {
                assertBits("edge " + x + "," + z, reference.noiseFast2D(x, z), program.sample2D(x, z));
            }
        }
    }

    @Test
    public void powerChildrenAndUncoveredGeneratorsFallBack() {
        assertNull(simplex(1, 1D, 1).pow(2).compileUnsignedProgram());
        assertNull(simplex(1, 1D, 1).child(simplex(2, 1D, 1)).compileUnsignedProgram());
        assertNull(simplex(1, 1D, 1).fractureWith(new CNG(new RNG(2), NoiseType.PERLIN, 1D, 1), 50).compileUnsignedProgram());
    }

    private static CNG deepFracture() {
        CNG inner = simplex(8, 1D, 1).scale(0.21).fractureWith(simplex(9, 1D, 1).scale(0.9), 620);
        return simplex(7, 0.8D, 1).fractureWith(inner, 145).bake();
    }

    private static CNG simplex(long seed, double opacity, int octaves) {
        return new CNG(new RNG(seed), opacity, octaves);
    }

    private static void assertProgramMatches(String label, Supplier<CNG> fresh) {
        UnsignedNoiseProgram program = fresh.get().compileUnsignedProgram();
        assertNotNull(label, program);
        CNG reference = fresh.get();
        SplittableRandom random = new SplittableRandom(label.hashCode());
        for (int point = 0; point < POINTS; point++) {
            assertPointMatches(label, program, reference, coordinate(random), coordinate(random));
        }
    }

    private static void assertPointMatches(String label, UnsignedNoiseProgram program, CNG reference, double x, double z) {
        assertBits(label + " " + x + "," + z, reference.noiseFast2D(x, z), program.sample2D(x, z));
    }

    private static double coordinate(SplittableRandom random) {
        return (random.nextDouble() * 2 - 1) * RANGE;
    }

    private static void assertBits(String label, double expected, double actual) {
        assertEquals(label, Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }
}
