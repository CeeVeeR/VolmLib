package art.arcane.volmlib.util.interpolation;

import art.arcane.volmlib.util.function.NoiseProvider;

/** Fills {@code out[i]} with noise at {@code (xs[i], zs[i])}; CNG::fill2D and NoiseGenerator::fill2D fit. */
@FunctionalInterface
public interface LatticeFill {
    void fill(double[] xs, double[] zs, double[] out);

    static LatticeFill pointwise(NoiseProvider provider) {
        return (xs, zs, out) -> {
            for (int i = 0; i < out.length; i++) {
                out[i] = provider.noise(xs[i], zs[i]);
            }
        };
    }
}
