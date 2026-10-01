package art.arcane.volmlib.util.noise;

public interface NoiseProgramEvaluator {
    PreparedNoiseProgram prepare(SignedNoiseProgram program);

    /** Null when the evaluator has no unsigned support; CNG then keeps sampling point by point. */
    default PreparedUnsignedNoiseProgram prepareUnsigned(UnsignedNoiseProgram program) {
        return null;
    }
}
