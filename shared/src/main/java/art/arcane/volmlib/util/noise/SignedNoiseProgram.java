package art.arcane.volmlib.util.noise;

/**
 * A CNG's signed fast path as plain data: simplex octaves, coordinate scale, opacity and an optional
 * fracture warp. Evaluating it repeats CNG.noiseFastSigned2D/3D operation for operation, so it can be
 * handed to another evaluator (Rust) without changing a single bit of output.
 */
public record SignedNoiseProgram(NoiseSource generator, double scale, double opacity, FractureWarp fracture) {
    private static final double UNITY_OPACITY = 1D;

    public record FractureWarp(SignedNoiseProgram source, double strength) {
    }

    public double sample2D(double x, double z) {
        if (fracture == null) {
            return withOpacity(generator.sample2D(x * scale, z * scale));
        }
        return withOpacity(generator.sample2D(warped2D(x, x, z) * scale, warped2D(z, z, x) * scale));
    }

    public double sample3D(double x, double y, double z) {
        if (fracture == null) {
            return withOpacity(generator.sample3D(x * scale, y * scale, z * scale));
        }
        double fx = warped3D(x, x, y, z);
        double fy = warped2D(y, y, x);
        return withOpacity(generator.sample3D(fx * scale, fy * scale, warped3D(z, z, x, y) * scale));
    }

    private double warped2D(double coordinate, double first, double second) {
        return coordinate + (fracture.source().sample2D(first, second) * fracture.strength());
    }

    private double warped3D(double coordinate, double first, double second, double third) {
        return coordinate + (fracture.source().sample3D(first, second, third) * fracture.strength());
    }

    private double withOpacity(double signed) {
        return opacity == UNITY_OPACITY ? signed : ((signed + 1D) * opacity) - 1D;
    }
}
