package xin.vanilla.banira.generated;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import xin.vanilla.aotake.api.sweep.*;

public final class PolicyAudit {
    public final String id;
    public final long generation;
    public final String mode;
    public final JsonArray events = new JsonArray();
    public int evaluated;
    public long collectedAt, cleanupAt;
    public xin.vanilla.aotake.api.rule.EntityAccess retained;
    public PolicyAudit(SweepContext context) {
        id = context.taskId().toString(); generation = context.generationId();
        mode = System.getProperty("aotake.policySmoke.mode", "normal");
        event(context, "create");
    }
    public void event(SweepContext context, String event) {
        if (!id.equals(context.taskId().toString()) || generation != context.generationId())
            throw new IllegalStateException("Task state or generation changed");
        events.add(event);
    }
    public void finish(SweepContext context, SweepOutcome outcome) {
        event(context, "finish");
        JsonObject value = new JsonObject();
        value.addProperty("id", id); value.addProperty("generation", generation);
        value.addProperty("mode", mode); value.addProperty("trigger", context.trigger().name());
        value.addProperty("operation", context.operation().name()); value.addProperty("status", outcome.status().name());
        value.addProperty("stoppedAt", outcome.stoppedAt().name()); value.addProperty("failure", outcome.failure());
        value.addProperty("selected", outcome.selected()); value.addProperty("processed", outcome.processed());
        value.addProperty("skipped", outcome.skipped()); value.addProperty("evaluated", evaluated);
        value.addProperty("delay", cleanupAt - collectedAt); value.add("events", events);
        value.addProperty("batchSize", ((Number) context.configuration().get("base.batch.sweepEntityLimit")).intValue());
        if (retained != null) value.addProperty("retainedAtFinish", retained.uuid().toString());
        try {
            Files.write(Paths.get(System.getProperty("aotake.policySmoke.root"), "tasks.jsonl"),
                    (value.toString() + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
}
