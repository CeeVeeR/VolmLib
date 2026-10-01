package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.noise.JavaSimplexKernel;
import art.arcane.volmlib.util.noise.SimplexKernel;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Warm-up then timed rounds; prints the median nanoseconds per sampled point. Coordinates sit in
 * the range Iris feeds the kernel (block position times a small frequency).
 */
public final class SimplexKernelBenchmark {
    private static final int POINTS = 1 << 20;
    private static final int WARMUP_ROUNDS = 15;
    private static final int MEASURED_ROUNDS = 25;
    private static final double COORDINATE_RANGE = 500;
    private static final long SEED = 1337;

    private final double[] xs = randomCoordinates(1);
    private final double[] ys = randomCoordinates(2);
    private final double[] zs = randomCoordinates(3);
    private final double[] out = new double[POINTS];
    private double sink;

    public static void main(String[] args) {
        new SimplexKernelBenchmark().runAll();
    }

    private void runAll() {
        SimplexKernel java = new JavaSimplexKernel();
        NativeSimplexKernel rust = new NativeSimplexKernel();
        runFlatCases(java, rust);
        runVolumeCases(java, rust);
        System.out.println("sink " + sink);
    }

    private void runFlatCases(SimplexKernel java, NativeSimplexKernel rust) {
        report("2D java per point", () -> samplePoints2D(java));
        report("2D rust per point", () -> samplePoints2D(rust));
        report("2D rust batch fill", () -> rust.fillSimplex2D(SEED, xs, ys, out));
    }

    private void runVolumeCases(SimplexKernel java, NativeSimplexKernel rust) {
        report("3D java per point", () -> samplePoints3D(java));
        report("3D rust per point", () -> samplePoints3D(rust));
        report("3D rust batch fill", () -> rust.fillSimplex3D(SEED, xs, ys, zs, out));
    }

    private void samplePoints2D(SimplexKernel kernel) {
        for (int i = 0; i < POINTS; i++) {
            out[i] = kernel.simplex2D(SEED, xs[i], ys[i]);
        }
    }

    private void samplePoints3D(SimplexKernel kernel) {
        for (int i = 0; i < POINTS; i++) {
            out[i] = kernel.simplex3D(SEED, xs[i], ys[i], zs[i]);
        }
    }

    private void report(String label, Runnable round) {
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            round.run();
        }
        double[] nanosPerPoint = new double[MEASURED_ROUNDS];
        Arrays.setAll(nanosPerPoint, i -> timeRound(round));
        Arrays.sort(nanosPerPoint);
        System.out.printf("%-22s %7.2f ns/point%n", label, nanosPerPoint[MEASURED_ROUNDS / 2]);
    }

    private double timeRound(Runnable round) {
        long start = System.nanoTime();
        round.run();
        sink += out[(int) (start & (POINTS - 1))];
        return (System.nanoTime() - start) / (double) POINTS;
    }

    private static double[] randomCoordinates(long stream) {
        SplittableRandom random = new SplittableRandom(stream);
        double[] coordinates = new double[POINTS];
        Arrays.setAll(coordinates, i -> (random.nextDouble() * 2 - 1) * COORDINATE_RANGE);
        return coordinates;
    }
}
