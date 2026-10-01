package art.arcane.volmlib.util.interpolation;

import art.arcane.volmlib.util.function.NoiseProvider;

/**
 * Noise precomputed with one batch fill on a {@link LatticeBox}. Lattice hits read the array; every other
 * coordinate asks the fallback, so the grid always answers exactly what the fallback would.
 */
public final class LatticeNoiseGrid implements NoiseProvider {
    private final LatticeBox box;
    private final double[] values;
    private final NoiseProvider fallback;

    private LatticeNoiseGrid(LatticeBox box, double[] values, NoiseProvider fallback) {
        this.box = box;
        this.values = values;
        this.fallback = fallback;
    }

    public static LatticeNoiseGrid sample(LatticeBox box, LatticeFill fill, NoiseProvider fallback) {
        double[] xs = new double[box.size()];
        double[] zs = new double[box.size()];
        writeLatticeCoordinates(box, xs, zs);
        double[] values = new double[box.size()];
        fill.fill(xs, zs, values);
        return new LatticeNoiseGrid(box, values, fallback);
    }

    @Override
    public double noise(double x, double z) {
        int index = box.indexOf(x, z);
        return index == LatticeRange.OUTSIDE ? fallback.noise(x, z) : values[index];
    }

    private static void writeLatticeCoordinates(LatticeBox box, double[] xs, double[] zs) {
        int width = box.xs().count();
        for (int index = 0; index < xs.length; index++) {
            xs[index] = box.coordinate(box.xs().indexAt(index % width));
            zs[index] = box.coordinate(box.zs().indexAt(index / width));
        }
    }
}
