package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SimplexKernelsTest {
    @Test
    public void missingOrBlankPropertySelectsTheJavaKernel() {
        assertTrue(SimplexKernels.selectConfiguredKernel(null) instanceof JavaSimplexKernel);
        assertTrue(SimplexKernels.selectConfiguredKernel("  ") instanceof JavaSimplexKernel);
    }

    @Test
    public void unknownKernelClassFailsLoudlyInsteadOfSilentlyFallingBack() {
        assertSelectionFails("art.arcane.volmlib.util.noise.NoSuchKernel");
    }

    @Test
    public void classThatIsNotAKernelIsRejected() {
        assertSelectionFails("java.lang.Object");
    }

    @Test
    public void namedKernelIsInstantiatedWithSurroundingWhitespaceIgnored() {
        SimplexKernel kernel = SimplexKernels.selectConfiguredKernel(" " + JavaSimplexKernel.class.getName() + " ");
        assertTrue(kernel instanceof JavaSimplexKernel);
    }

    @Test
    public void fastNoiseDoubleSamplesThroughTheActiveKernelUnchanged() {
        FastNoiseDouble noise = new FastNoiseDouble(1337);
        double expected = FastNoiseDouble.simplex2DInJava(1337, 0.42 * noise.getFrequency(), -3.1 * noise.getFrequency());
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(noise.GetSimplex(0.42, -3.1)));
    }

    private static void assertSelectionFails(String kernelClassName) {
        try {
            SimplexKernels.selectConfiguredKernel(kernelClassName);
            fail("expected " + kernelClassName + " to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(kernelClassName));
        }
    }
}
