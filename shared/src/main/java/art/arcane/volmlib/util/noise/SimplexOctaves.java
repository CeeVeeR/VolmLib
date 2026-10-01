package art.arcane.volmlib.util.noise;

/** The sampling SimplexNoise.noiseSigned performs, captured as plain values. */
public record SimplexOctaves(long seed, double frequency, int octaves, double octaveBounding) implements NoiseSource {
    private static final double OCTAVE_FREQUENCY_STEP = 2D;
    private static final double OCTAVE_AMPLITUDE_STEP = 0.5D;

    @Override
    public double sample2D(double x, double z) {
        return octaves == 1 ? single2D(x, z) : octaveSum2D(x, z) * octaveBounding;
    }

    @Override
    public double sample3D(double x, double y, double z) {
        return octaves == 1 ? single3D(x, y, z) : octaveSum3D(x, y, z) * octaveBounding;
    }

    private double octaveSum2D(double x, double z) {
        double factor = 1D;
        double amplitude = 1D;
        double value = 0D;
        for (int octave = 0; octave < octaves; octave++) {
            value += single2D(x * factor, z * factor) * amplitude;
            factor *= OCTAVE_FREQUENCY_STEP;
            amplitude *= OCTAVE_AMPLITUDE_STEP;
        }
        return value;
    }

    private double octaveSum3D(double x, double y, double z) {
        double factor = 1D;
        double amplitude = 1D;
        double value = 0D;
        for (int octave = 0; octave < octaves; octave++) {
            value += single3D(x * factor, y * factor, z * factor) * amplitude;
            factor *= OCTAVE_FREQUENCY_STEP;
            amplitude *= OCTAVE_AMPLITUDE_STEP;
        }
        return value;
    }

    private double single2D(double x, double z) {
        return FastNoiseDouble.simplex2DInJava(seed, x * frequency, z * frequency);
    }

    private double single3D(double x, double y, double z) {
        return FastNoiseDouble.simplex3DInJava(seed, x * frequency, y * frequency, z * frequency);
    }
}
