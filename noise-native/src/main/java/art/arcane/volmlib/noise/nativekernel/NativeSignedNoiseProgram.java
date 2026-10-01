package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.noise.NoiseFills;
import art.arcane.volmlib.util.noise.NoiseSource;
import art.arcane.volmlib.util.noise.OffsetSimplexSource;
import art.arcane.volmlib.util.noise.PreparedNoiseProgram;
import art.arcane.volmlib.util.noise.SignedNoiseProgram;
import art.arcane.volmlib.util.noise.SimplexOctaves;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/** A SignedNoiseProgram encoded once for the Rust evaluator (layout mirrors rust/src/program.rs). */
public final class NativeSignedNoiseProgram implements PreparedNoiseProgram {
    private static final int LONGS_PER_NODE = 3;
    private static final int DOUBLES_PER_NODE = 7;
    private static final long PLAIN_SOURCE = 0;
    private static final long OFFSET_SOURCE = 1;

    private final long[] longs;
    private final double[] doubles;
    private final int nodes;

    public NativeSignedNoiseProgram(SignedNoiseProgram program) {
        List<SignedNoiseProgram> chain = fractureChain(program);
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

    @Override
    public void fill3D(double[] xs, double[] ys, double[] zs, double[] out) {
        NoiseFills.requireMatchingLengths(out, xs, ys, zs);
        NativeFillStatus.requireOk(callFill3D(xs, ys, zs, out));
    }

    private int callFill2D(double[] xs, double[] zs, double[] out) {
        try {
            return (int) NativeNoiseLibrary.PROGRAM_FILL_2D.invokeExact(heap(longs), heap(doubles), (long) nodes,
                heap(xs), heap(zs), heap(out), (long) out.length);
        } catch (Throwable failure) {
            throw NativeSimplexKernel.nativeCallFailed(failure);
        }
    }

    private int callFill3D(double[] xs, double[] ys, double[] zs, double[] out) {
        try {
            return (int) NativeNoiseLibrary.PROGRAM_FILL_3D.invokeExact(heap(longs), heap(doubles), (long) nodes,
                heap(xs), heap(ys), heap(zs), heap(out), (long) out.length);
        } catch (Throwable failure) {
            throw NativeSimplexKernel.nativeCallFailed(failure);
        }
    }

    private static List<SignedNoiseProgram> fractureChain(SignedNoiseProgram root) {
        List<SignedNoiseProgram> chain = new ArrayList<>();
        for (SignedNoiseProgram node = root; node != null; node = node.fracture() == null ? null : node.fracture().source()) {
            chain.add(node);
        }
        return chain;
    }

    private void encodeNode(SignedNoiseProgram node, int index) {
        encodeSource(node.generator(), index);
        int base = index * DOUBLES_PER_NODE;
        doubles[base + 4] = node.scale();
        doubles[base + 5] = node.opacity();
        doubles[base + 6] = node.fracture() == null ? 0D : node.fracture().strength();
    }

    private void encodeSource(NoiseSource source, int index) {
        if (source instanceof OffsetSimplexSource offset) {
            encodeOctaves(offset.octaves(), index, OFFSET_SOURCE);
            doubles[index * DOUBLES_PER_NODE + 2] = offset.offsetX();
            doubles[index * DOUBLES_PER_NODE + 3] = offset.offsetZ();
            return;
        }
        encodeOctaves((SimplexOctaves) source, index, PLAIN_SOURCE);
    }

    private void encodeOctaves(SimplexOctaves octaves, int index, long sourceKind) {
        longs[index * LONGS_PER_NODE] = sourceKind;
        longs[index * LONGS_PER_NODE + 1] = octaves.seed();
        longs[index * LONGS_PER_NODE + 2] = octaves.octaves();
        doubles[index * DOUBLES_PER_NODE] = octaves.frequency();
        doubles[index * DOUBLES_PER_NODE + 1] = octaves.octaveBounding();
    }

    private static MemorySegment heap(long[] array) {
        return MemorySegment.ofArray(array);
    }

    private static MemorySegment heap(double[] array) {
        return MemorySegment.ofArray(array);
    }
}
