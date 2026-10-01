package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.noise.NoiseFills;
import art.arcane.volmlib.util.noise.SimplexKernel;

import java.lang.foreign.MemorySegment;

public final class NativeSimplexKernel implements SimplexKernel {
    @Override
    public double simplex2D(long seed, double x, double y) {
        try {
            return (double) NativeNoiseLibrary.SIMPLEX_2D.invokeExact(seed, x, y);
        } catch (Throwable failure) {
            throw nativeCallFailed(failure);
        }
    }

    @Override
    public double simplex3D(long seed, double x, double y, double z) {
        try {
            return (double) NativeNoiseLibrary.SIMPLEX_3D.invokeExact(seed, x, y, z);
        } catch (Throwable failure) {
            throw nativeCallFailed(failure);
        }
    }

    public void fillSimplex2D(long seed, double[] xs, double[] ys, double[] out) {
        NoiseFills.requireMatchingLengths(out, xs, ys);
        NativeFillStatus.requireOk(callFill2D(seed, xs, ys, out));
    }

    public void fillSimplex3D(long seed, double[] xs, double[] ys, double[] zs, double[] out) {
        NoiseFills.requireMatchingLengths(out, xs, ys, zs);
        NativeFillStatus.requireOk(callFill3D(seed, xs, ys, zs, out));
    }

    private static int callFill2D(long seed, double[] xs, double[] ys, double[] out) {
        try {
            return (int) NativeNoiseLibrary.SIMPLEX_2D_FILL.invokeExact(seed, heap(xs), heap(ys), heap(out), (long) out.length);
        } catch (Throwable failure) {
            throw nativeCallFailed(failure);
        }
    }

    private static int callFill3D(long seed, double[] xs, double[] ys, double[] zs, double[] out) {
        try {
            return (int) NativeNoiseLibrary.SIMPLEX_3D_FILL.invokeExact(seed, heap(xs), heap(ys), heap(zs), heap(out), (long) out.length);
        } catch (Throwable failure) {
            throw nativeCallFailed(failure);
        }
    }

    private static MemorySegment heap(double[] array) {
        return MemorySegment.ofArray(array);
    }

    static RuntimeException nativeCallFailed(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException("Native simplex call failed", failure);
    }
}
