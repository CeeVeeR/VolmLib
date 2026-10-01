package art.arcane.volmlib.util.noise;

/**
 * Chooses the simplex implementation once per JVM. The choice is a static final so the JIT
 * inlines the call exactly as before when the Java kernel is active.
 */
public final class SimplexKernels {
    public static final String KERNEL_CLASS_PROPERTY = "volmlib.noise.kernel";
    private static final SimplexKernel ACTIVE = selectConfiguredKernel(System.getProperty(KERNEL_CLASS_PROPERTY));

    private SimplexKernels() {
    }

    public static SimplexKernel active() {
        return ACTIVE;
    }

    static SimplexKernel selectConfiguredKernel(String kernelClassName) {
        if (kernelClassName == null || kernelClassName.isBlank()) {
            return new JavaSimplexKernel();
        }
        return instantiateKernel(kernelClassName.trim());
    }

    private static SimplexKernel instantiateKernel(String kernelClassName) {
        try {
            return (SimplexKernel) loadKernelClass(kernelClassName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError failure) {
            throw unusableKernel(kernelClassName, failure);
        }
    }

    private static Class<?> loadKernelClass(String kernelClassName) throws ClassNotFoundException {
        return Class.forName(kernelClassName, true, SimplexKernels.class.getClassLoader());
    }

    private static IllegalStateException unusableKernel(String kernelClassName, Throwable failure) {
        return new IllegalStateException("Configured simplex kernel " + kernelClassName + " is unusable", failure);
    }
}
