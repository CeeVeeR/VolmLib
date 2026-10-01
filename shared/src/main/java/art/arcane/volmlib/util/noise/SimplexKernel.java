package art.arcane.volmlib.util.noise;

public interface SimplexKernel {
    double simplex2D(long seed, double x, double y);

    double simplex3D(long seed, double x, double y, double z);
}
