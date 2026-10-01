package art.arcane.volmlib.util.noise;

/** Shared guard for batch fills: every input buffer must match the output length. */
public final class NoiseFills {
    private NoiseFills() {
    }

    public static void requireMatchingLengths(double[] out, double[]... inputs) {
        for (double[] input : inputs) {
            if (input.length != out.length) {
                throw new IllegalArgumentException("Input length " + input.length + " differs from output " + out.length);
            }
        }
    }
}
