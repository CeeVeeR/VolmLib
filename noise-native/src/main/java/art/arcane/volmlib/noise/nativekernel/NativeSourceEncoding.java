package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.noise.NoiseSource;
import art.arcane.volmlib.util.noise.OffsetSimplexSource;
import art.arcane.volmlib.util.noise.SimplexOctaves;

/** Source prefix shared by every program layout (rust/src/program.rs decode_source): 3 longs, 4 doubles. */
final class NativeSourceEncoding {
    private static final long PLAIN_SOURCE = 0;
    private static final long OFFSET_SOURCE = 1;

    private NativeSourceEncoding() {
    }

    static void encode(NoiseSource source, long[] longs, int longBase, double[] doubles, int doubleBase) {
        if (source instanceof OffsetSimplexSource offset) {
            encodeOctaves(offset.octaves(), OFFSET_SOURCE, longs, longBase, doubles, doubleBase);
            encodeOffset(offset, doubles, doubleBase);
            return;
        }
        encodeOctaves((SimplexOctaves) source, PLAIN_SOURCE, longs, longBase, doubles, doubleBase);
    }

    private static void encodeOffset(OffsetSimplexSource offset, double[] doubles, int doubleBase) {
        doubles[doubleBase + 2] = offset.offsetX();
        doubles[doubleBase + 3] = offset.offsetZ();
    }

    private static void encodeOctaves(SimplexOctaves octaves, long kind, long[] longs, int longBase, double[] doubles, int doubleBase) {
        longs[longBase] = kind;
        longs[longBase + 1] = octaves.seed();
        longs[longBase + 2] = octaves.octaves();
        doubles[doubleBase] = octaves.frequency();
        doubles[doubleBase + 1] = octaves.octaveBounding();
    }
}
