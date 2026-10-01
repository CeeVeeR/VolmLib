package art.arcane.volmlib.util.noise;

/**
 * The evaluator CNG batch fills hand covered programs to, chosen once per JVM. Unset means no evaluator:
 * fills keep sampling CNG point by point, exactly as before.
 */
public final class NoiseProgramEvaluators {
    public static final String EVALUATOR_CLASS_PROPERTY = "volmlib.noise.programEvaluator";
    private static final NoiseProgramEvaluator ACTIVE = selectConfiguredEvaluator(System.getProperty(EVALUATOR_CLASS_PROPERTY));

    private NoiseProgramEvaluators() {
    }

    public static NoiseProgramEvaluator active() {
        return ACTIVE;
    }

    static NoiseProgramEvaluator selectConfiguredEvaluator(String evaluatorClassName) {
        if (evaluatorClassName == null || evaluatorClassName.isBlank()) {
            return null;
        }
        return instantiateEvaluator(evaluatorClassName.trim());
    }

    private static NoiseProgramEvaluator instantiateEvaluator(String evaluatorClassName) {
        try {
            return (NoiseProgramEvaluator) loadEvaluatorClass(evaluatorClassName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError failure) {
            throw unusableEvaluator(evaluatorClassName, failure);
        }
    }

    private static Class<?> loadEvaluatorClass(String evaluatorClassName) throws ClassNotFoundException {
        return Class.forName(evaluatorClassName, true, NoiseProgramEvaluators.class.getClassLoader());
    }

    private static IllegalStateException unusableEvaluator(String evaluatorClassName, Throwable failure) {
        return new IllegalStateException("Configured noise program evaluator " + evaluatorClassName + " is unusable", failure);
    }
}
