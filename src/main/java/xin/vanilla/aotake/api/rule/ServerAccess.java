package xin.vanilla.aotake.api.rule;

import java.util.List;
import java.util.Optional;

public interface ServerAccess {
    long tick();
    List<EntityAccess> players();
    Optional<WorldAccess> world(String dimensionId);
    <T> T nativeServer(Class<T> type);
}
