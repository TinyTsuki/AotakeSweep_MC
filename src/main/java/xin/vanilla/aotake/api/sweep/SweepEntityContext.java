package xin.vanilla.aotake.api.sweep;

import xin.vanilla.aotake.api.rule.EntityAccess;

public interface SweepEntityContext {
    SweepContext task();
    EntityAccess entity();
    int index();
}
