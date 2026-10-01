package art.arcane.volmlib.util.noise;

/**
 * A CNG's unsigned 2D path (noiseFast2D) as data. Repeats CNG.getNoise and applyPost operation for operation:
 * unsigned value ((signed / 2) + 0.5) * opacity, fracture displacement (u - 0.5) * fscale, and the post step
 * ((n / 1) - down + up) * patch whenever CNG leaves its identity-post fast path.
 */
public record UnsignedNoiseProgram(NoiseSource generator, UnsignedShaping shaping, UnsignedFracture fracture) {
    private static final double SIGNED_TO_UNSIGNED_DIVISOR = 2D;
    private static final double SIGNED_TO_UNSIGNED_OFFSET = 0.5D;

    public record UnsignedShaping(double scale, double opacity, PostAdjustment post) {
    }

    public record PostAdjustment(double down, double up, double patch) {
        private static final double SINGLE_SOURCE_WEIGHT = 1D;

        double apply(double n) {
            return ((n / SINGLE_SOURCE_WEIGHT) - down + up) * patch;
        }
    }

    public record UnsignedFracture(UnsignedNoiseProgram source, double scale) {
    }

    public double sample2D(double x, double z) {
        double scale = shaping.scale();
        double raw = fracture == null ? unsignedAt(x * scale, z * scale)
            : unsignedAt(warped(x, x, z) * scale, warped(z, z, x) * scale);
        return shaping.post() == null ? raw : shaping.post().apply(raw);
    }

    private double unsignedAt(double x, double z) {
        double signed = generator.sample2D(x, z);
        return ((signed / SIGNED_TO_UNSIGNED_DIVISOR) + SIGNED_TO_UNSIGNED_OFFSET) * shaping.opacity();
    }

    private double warped(double coordinate, double first, double second) {
        return coordinate + ((fracture.source().sample2D(first, second) - SIGNED_TO_UNSIGNED_OFFSET) * fracture.scale());
    }
}
