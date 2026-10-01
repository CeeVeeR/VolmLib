package art.arcane.volmlib.noise.nativekernel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeSimplexKernelFillTest {
    private final NativeSimplexKernel rust = new NativeSimplexKernel();

    private static final double[] XS = {0.1, -4.2, 9.9, 1e6};
    private static final double[] YS = {3.3, 0.0, -7.7, -1e6};
    private static final double[] ZS = {-1.0, 2.5, 0.0, 12.0};

    @Test
    public void filled2DValuesEqualPointSamples() {
        double[] out = new double[XS.length];
        rust.fillSimplex2D(9, XS, YS, out);
        for (int i = 0; i < XS.length; i++) {
            NativeSimplexKernelParityTest.assertBitIdentical("2D " + i, rust.simplex2D(9, XS[i], YS[i]), out[i]);
        }
    }

    @Test
    public void filled3DValuesEqualPointSamples() {
        double[] out = new double[XS.length];
        rust.fillSimplex3D(9, XS, YS, ZS, out);
        for (int i = 0; i < XS.length; i++) {
            NativeSimplexKernelParityTest.assertBitIdentical("3D " + i, rust.simplex3D(9, XS[i], YS[i], ZS[i]), out[i]);
        }
    }

    @Test
    public void inputShorterThanOutputIsRejectedBeforeCallingNative() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> rust.fillSimplex2D(1, new double[2], new double[3], new double[3]));
        assertTrue(refused.getMessage().contains("differs"));
    }

    @Test
    public void outputArrayReusedAsInputIsRefusedByTheNativeAliasCheck() {
        double[] shared = {0.5, 1.5};
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> rust.fillSimplex2D(1, shared, new double[2], shared));
        assertTrue(refused.getMessage().contains("OUTPUT_ALIASES_INPUT"));
    }

    @Test
    public void emptyBuffersAreAcceptedAndLeftEmpty() {
        double[] empty = new double[0];
        rust.fillSimplex3D(1, empty, new double[0], new double[0], new double[0]);
        assertEquals(0, empty.length);
    }

    @Test
    public void unknownNativeStatusCodeIsNotMistakenForSuccess() {
        assertThrows(IllegalStateException.class, () -> NativeFillStatus.requireOk(99));
    }

    @Test
    public void missingLibraryPathNamesThePropertyToSet() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> NativeNoiseLibrary.openConfiguredLibrary(" "));
        assertTrue(refused.getMessage().contains(NativeNoiseLibrary.LIBRARY_PATH_PROPERTY));
    }
}
