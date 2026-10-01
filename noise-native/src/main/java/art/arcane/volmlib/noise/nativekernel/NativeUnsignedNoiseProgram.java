package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.noise.NoiseFills;
import art.arcane.volmlib.util.noise.PreparedUnsignedNoiseProgram;
import art.arcane.volmlib.util.noise.UnsignedNoiseProgram;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/** An UnsignedNoiseProgram encoded once for the Rust evaluator (layout mirrors rust/src/unsigned.rs). */
public final class NativeUnsignedNoiseProgram implements PreparedUnsignedNoiseProgram {
    private static final int LONGS_PER_NODE = 4;
    private static final int DOUBLES_PER_NODE = 10;

    private final long[] longs;
    private final double[] doubles;
    private final int nodes;

    public NativeUnsignedNoiseProgram(UnsignedNoiseProgram program) {
        List<UnsignedNoiseProgram> chain = fractureChain(program);
        nodes = chain.size();
        longs = new long[nodes * LONGS_PER_NODE];
        doubles = new double[nodes * DOUBLES_PER_NODE];
        for (int index = 0; index < nodes; index++) {
            encodeNode(chain.get(index), index);
        }
    }

    @Override
    public void fill2D(double[] xs, double[] zs, double[] out) {
        NoiseFills.requireMatchingLengths(out, xs, zs);
        NativeFillStatus.requireOk(callFill2D(xs, zs, out));
    }

    private int callFill2D(double[] xs, double[] zs, double[] out) {
        try {
            return (int) NativeNoiseLibrary.UNSIGNED_FILL_2D.invokeExact(heap(longs), heap(doubles), (long) nodes,
                heap(xs), heap(zs), heap(out), (long) out.length);
        } catch (Throwable failure) {
            throw NativeSimplexKernel.nativeCallFailed(failure);
        }
    }

    private static List<UnsignedNoiseProgram> fractureChain(UnsignedNoiseProgram root) {
        List<UnsignedNoiseProgram> chain = new ArrayList<>();
        for (UnsignedNoiseProgram node = root; node != null; node = node.fracture() == null ? null : node.fracture().source()) {
            chain.add(node);
        }
        return chain;
    }

    private void encodeNode(UnsignedNoiseProgram node, int index) {
        int base = index * DOUBLES_PER_NODE;
        NativeSourceEncoding.encode(node.generator(), longs, index * LONGS_PER_NODE, doubles, base);
        encodeShaping(node.shaping(), base);
        doubles[base + 6] = node.fracture() == null ? 0D : node.fracture().scale();
        encodePost(node.shaping().post(), index, base);
    }

    private void encodeShaping(UnsignedNoiseProgram.UnsignedShaping shaping, int base) {
        doubles[base + 4] = shaping.scale();
        doubles[base + 5] = shaping.opacity();
    }

    private static MemorySegment heap(long[] array) {
        return MemorySegment.ofArray(array);
    }

    private static MemorySegment heap(double[] array) {
        return MemorySegment.ofArray(array);
    }

    private void encodePost(UnsignedNoiseProgram.PostAdjustment post, int index, int base) {
        longs[index * LONGS_PER_NODE + 3] = post == null ? 0 : 1;
        if (post != null) {
            doubles[base + 7] = post.down();
            doubles[base + 8] = post.up();
            doubles[base + 9] = post.patch();
        }
    }
}
