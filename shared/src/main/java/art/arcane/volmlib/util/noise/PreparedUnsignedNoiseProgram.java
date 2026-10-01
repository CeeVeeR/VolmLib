package art.arcane.volmlib.util.noise;

/** An UnsignedNoiseProgram made ready by an evaluator; fills must equal UnsignedNoiseProgram.sample2D bit for bit. */
public interface PreparedUnsignedNoiseProgram {
    void fill2D(double[] xs, double[] zs, double[] out);
}
