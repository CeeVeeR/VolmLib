package art.arcane.volmlib.util.interpolation;

/**
 * The lattice getBilinearNoise samples: coordinates {@code (double) k * spacing}. A coordinate only maps
 * to an index when it is bit-identical to that product, so a grid lookup can never answer for a
 * different coordinate than the one asked.
 */
public record LatticeBox(double spacing, LatticeRange xs, LatticeRange zs) {
    public static final int MAXIMUM_POINTS = 1 << 20;

    public LatticeBox {
        if (!(spacing > 0) || Double.isInfinite(spacing)) {
            throw new IllegalArgumentException("Lattice spacing must be finite and positive, got " + spacing);
        }
        if ((long) xs.count() * zs.count() > MAXIMUM_POINTS) {
            throw new IllegalArgumentException("Lattice box of " + xs.count() + "x" + zs.count() + " is too large");
        }
    }

    public static LatticeBox covering(double minX, double maxX, double minZ, double maxZ, double spacing, double reach) {
        LatticeRange x = LatticeRange.covering(minX - reach, maxX + reach, spacing);
        return new LatticeBox(spacing, x, LatticeRange.covering(minZ - reach, maxZ + reach, spacing));
    }

    public int size() {
        return xs.count() * zs.count();
    }

    public double coordinate(long index) {
        return (double) index * spacing;
    }

    int indexOf(double x, double z) {
        int offsetX = offsetOnAxis(xs, x);
        int offsetZ = offsetOnAxis(zs, z);
        boolean inside = offsetX != LatticeRange.OUTSIDE && offsetZ != LatticeRange.OUTSIDE;
        return inside ? offsetZ * xs.count() + offsetX : LatticeRange.OUTSIDE;
    }

    private int offsetOnAxis(LatticeRange range, double coordinate) {
        long index = Math.round(coordinate / spacing);
        boolean exact = Double.doubleToRawLongBits(coordinate(index)) == Double.doubleToRawLongBits(coordinate);
        return exact ? range.offsetOf(index) : LatticeRange.OUTSIDE;
    }
}
