package xin.vanilla.aotake.network.packet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClearDustbinCommandTest {

    @Test
    public void cacheClearUsesInternalCommandWithoutChatPrefix() {
        assertEquals("aotake clearcache",
                ClearDustbinToServer.buildCommand(true, true, 3,
                        "aotake clearcache", "aotake cleardustbin"));
    }

    @Test
    public void fullDustbinClearUsesInternalCommandWithoutPage() {
        assertEquals("aotake cleardustbin",
                ClearDustbinToServer.buildCommand(true, false, 3,
                        "aotake clearcache", "aotake cleardustbin"));
    }

    @Test
    public void currentDustbinPageClearAppendsSelectedPage() {
        assertEquals("aotake cleardustbin 3",
                ClearDustbinToServer.buildCommand(false, false, 3,
                        "aotake clearcache", "aotake cleardustbin"));
    }
}
