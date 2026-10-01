package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.SplittableRandom;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SignedNoiseProgramTest {
    private static final int POINTS = 20_000;
    private static final double RANGE = 5000;

    @Test
    public void plainScaledOpacityAndOctaveSimplexMatchCngBitForBit() {
        assertProgramMatches("plain", () -> simplex(1, 1D, 1));
        assertProgramMatches("scaled", () -> simplex(2, 1D, 1).scale(0.037));
        assertProgramMatches("opacity", () -> simplex(3, 0.63D, 1).scale(0.11));
        assertProgramMatches("octaves", () -> simplex(4, 1D, 5).scale(0.02));
    }

    @Test
    public void nestedFractureChainsMatchCngBitForBit() {
        assertProgramMatches("fracture", () -> simplex(5, 1D, 1).scale(0.05).fractureWith(simplex(6, 1D, 2).scale(0.9), 93));
        assertProgramMatches("deep fracture", SignedNoiseProgramTest::deepFracture);
    }

    private static CNG deepFracture() {
        CNG inner = simplex(8, 1D, 1).scale(0.21).fractureWith(simplex(9, 1D, 1).scale(0.9), 620);
        return simplex(7, 0.8D, 1).fractureWith(inner, 145).bake();
    }

    private static CNG simplex(long seed, double opacity, int octaves) {
        return new CNG(new RNG(seed), opacity, octaves);
    }

    @Test
    public void uncoveredGeneratorsAnywhereInTheChainFallBackToCng() {
        CNG perlin = new CNG(new RNG(2), NoiseType.PERLIN, 1D, 1);
        assertNull(perlin.compileSignedProgram());
        assertNull(simplex(1, 1D, 1).fractureWith(perlin, 50).compileSignedProgram());
    }

    @Test
    public void powerAndChildrenFallBackToCng() {
        assertNull(simplex(1, 1D, 1).pow(2).compileSignedProgram());
        assertNull(simplex(1, 1D, 1).child(simplex(2, 1D, 1)).compileSignedProgram());
    }

    @Test
    public void selfReferencingFractureIsRefusedInsteadOfRecursingForever() {
        CNG looping = simplex(1, 1D, 1);
        looping.fractureWith(looping, 10);
        assertNull(looping.compileSignedProgram());
    }

    private static void assertProgramMatches(String label, Supplier<CNG> fresh) {
        SignedNoiseProgram program = fresh.get().compileSignedProgram();
        assertNotNull(label, program);
        CNG reference = fresh.get();
        SplittableRandom random = new SplittableRandom(label.hashCode());
        for (int point = 0; point < POINTS; point++) {
            assertPointMatches(label, program, reference, random);
        }
    }

    private static void assertPointMatches(String label, SignedNoiseProgram program, CNG reference, SplittableRandom random) {
        assertPointMatches(label, program, reference, coordinate(random), coordinate(random), coordinate(random));
    }

    private static void assertPointMatches(String label, SignedNoiseProgram program, CNG reference, double x, double y, double z) {
        assertBits(label + " 2D " + x + "," + z, reference.noiseFastSigned2D(x, z), program.sample2D(x, z));
        assertBits(label + " 3D " + x + "," + y + "," + z, reference.noiseFastSigned3D(x, y, z), program.sample3D(x, y, z));
    }

    private static void assertBits(String label, double expected, double actual) {
        assertEquals(label, Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static double coordinate(SplittableRandom random) {
        return (random.nextDouble() * 2 - 1) * RANGE;
    }
}
