package xin.vanilla.banira.generated;

import xin.vanilla.aotake.api.sweep.*;

public final class GroupedPolicy extends DefaultSweepPolicy<Object> {
    @Override public Object createState(SweepContext context) {
        return new PolicyState();
    }

    @Override public StageControl beforeCollect(SweepContext context, Object state) {
        return ((PolicyState) state).next();
    }
}
