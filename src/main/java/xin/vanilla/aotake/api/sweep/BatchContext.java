package xin.vanilla.aotake.api.sweep;

import xin.vanilla.aotake.api.rule.EntityAccess;
import java.util.List;

public interface BatchContext {
    SweepContext task();
    int index();
    int total();
    List<EntityAccess> entities();
}
