package art.arcane.volmlib.util.interpolation;

/** A run of consecutive lattice indices along one axis. */
public record LatticeRange(long first, int count) {
    static final int OUTSIDE = -1;

    public LatticeRange {
        if (count <= 0) {
            throw new IllegalArgumentException("Lattice range needs at least one index, got " + count);
        }
    }

    public static LatticeRange covering(double min, double max, double spacing) {
        if (!(max >= min)) {
            throw new IllegalArgumentException("Cannot cover [" + min + ", " + max + "]");
        }
        long first = (long) Math.floor(min / spacing) - 1;
        return new LatticeRange(first, countThrough(first, (long) Math.floor(max / spacing) + 2));
    }

    private static int countThrough(long first, long last) {
        long count = last - first + 1;
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Lattice range from " + first + " to " + last + " is too long");
        }
        return (int) count;
    }

    int offsetOf(long index) {
        long offset = index - first;
        return offset >= 0 && offset < count ? (int) offset : OUTSIDE;
    }

    long indexAt(int offset) {
        return first + offset;
    }
}
