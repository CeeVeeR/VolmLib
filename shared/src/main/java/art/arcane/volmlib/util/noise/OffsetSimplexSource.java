package art.arcane.volmlib.util.noise;

/** OffsetNoiseGenerator over a plain SimplexNoise: 2D shifts x and z, 3D shifts x and z and keeps y. */
public record OffsetSimplexSource(SimplexOctaves octaves, double offsetX, double offsetZ) implements NoiseSource {
    @Override
    public double sample2D(double x, double z) {
        return octaves.sample2D(x + offsetX, z + offsetZ);
    }

    @Override
    public double sample3D(double x, double y, double z) {
        return octaves.sample3D(x + offsetX, y, z + offsetZ);
    }
}
