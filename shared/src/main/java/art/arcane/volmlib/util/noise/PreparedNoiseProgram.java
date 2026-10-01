package art.arcane.volmlib.util.noise;

/** A SignedNoiseProgram made ready by an evaluator; fills must equal SignedNoiseProgram.sample2D/3D bit for bit. */
public interface PreparedNoiseProgram {
    void fill2D(double[] xs, double[] zs, double[] out);

    void fill3D(double[] xs, double[] ys, double[] zs, double[] out);
}
