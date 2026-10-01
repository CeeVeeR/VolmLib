package art.arcane.volmlib.noise.nativekernel;

import art.arcane.volmlib.util.noise.NoiseProgramEvaluator;
import art.arcane.volmlib.util.noise.PreparedNoiseProgram;
import art.arcane.volmlib.util.noise.PreparedUnsignedNoiseProgram;
import art.arcane.volmlib.util.noise.SignedNoiseProgram;
import art.arcane.volmlib.util.noise.UnsignedNoiseProgram;

/** Selected with -Dvolmlib.noise.programEvaluator=art.arcane.volmlib.noise.nativekernel.NativeNoiseProgramEvaluator. */
public final class NativeNoiseProgramEvaluator implements NoiseProgramEvaluator {
    @Override
    public PreparedNoiseProgram prepare(SignedNoiseProgram program) {
        return new NativeSignedNoiseProgram(program);
    }

    @Override
    public PreparedUnsignedNoiseProgram prepareUnsigned(UnsignedNoiseProgram program) {
        return new NativeUnsignedNoiseProgram(program);
    }
}
