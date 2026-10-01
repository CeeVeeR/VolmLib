package art.arcane.volmlib.util.noise;

/** A generator a SignedNoiseProgram can evaluate as data: plain simplex octaves or offset simplex octaves. */
public sealed interface NoiseSource permits SimplexOctaves, OffsetSimplexSource {
    double sample2D(double x, double z);

    double sample3D(double x, double y, double z);
}
