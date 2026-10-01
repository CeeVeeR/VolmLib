package art.arcane.volmlib.util.noise;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NoiseProgramEvaluatorsTest {
    @Test
    public void missingOrBlankPropertyMeansNoEvaluatorSoFillsStayPerPoint() {
        assertNull(NoiseProgramEvaluators.selectConfiguredEvaluator(null));
        assertNull(NoiseProgramEvaluators.selectConfiguredEvaluator(" "));
    }

    @Test
    public void unknownOrWrongClassFailsLoudlyNamingTheClass() {
        for (String name : new String[]{"art.arcane.NoSuchEvaluator", "java.lang.Object"}) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> NoiseProgramEvaluators.selectConfiguredEvaluator(name));
            assertTrue(refused.getMessage().contains(name));
        }
    }
}
