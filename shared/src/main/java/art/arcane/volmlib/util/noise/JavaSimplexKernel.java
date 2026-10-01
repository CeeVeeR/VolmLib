package art.arcane.volmlib.util.noise;

public final class JavaSimplexKernel implements SimplexKernel {
    @Override
    public double simplex2D(long seed, double x, double y) {
        return FastNoiseDouble.simplex2DInJava(seed, x, y);
    }

    @Override
    public double simplex3D(long seed, double x, double y, double z) {
        return FastNoiseDouble.simplex3DInJava(seed, x, y, z);
    }
}
