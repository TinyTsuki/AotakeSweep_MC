package xin.vanilla.banira.generated;

import xin.vanilla.aotake.api.sweep.StageControl;

public final class PolicyState {
    private boolean visited;

    public StageControl next() {
        boolean previous = visited;
        visited = true;
        return previous ? StageControl.CANCEL : StageControl.CONTINUE;
    }
}
