package xin.vanilla.aotake.api.sweep;

import xin.vanilla.aotake.api.rule.NativeMembers;
import xin.vanilla.aotake.api.rule.ServerAccess;

import java.util.Map;
import java.util.UUID;

/**
 * Callback-scoped access. Candidate entity views remain valid until the task ends.
 */
public interface SweepContext {
    UUID taskId();

    long generationId();

    SweepTrigger trigger();

    SweepOperation operation();

    SweepPhase phase();

    String cancellationReason();

    SweepScope scope();

    boolean ignoreFilter();

    boolean preFiltered();

    Map<String, Object> configuration();

    ServerAccess server();

    NativeMembers members();

    /**
     * Only beforeCollect may change the scope, within the original request's upper bound.
     */
    void requestScope(SweepScope scope);
}
