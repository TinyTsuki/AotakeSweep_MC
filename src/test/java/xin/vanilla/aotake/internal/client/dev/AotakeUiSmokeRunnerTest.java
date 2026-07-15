package xin.vanilla.aotake.internal.client.dev;

import org.junit.Test;

public class AotakeUiSmokeRunnerTest {
    @Test(expected = IllegalStateException.class)
    public void matchingProgressAndPlayerListKeysAreRejected() {
        AotakeUiSmokeRunner.validateProgressKey(258, 258);
    }

    @Test
    public void distinctProgressAndPlayerListKeysAreAccepted() {
        AotakeUiSmokeRunner.validateProgressKey(298, 258);
    }
}
