package xin.vanilla.aotake.api.sweep;

import xin.vanilla.aotake.api.rule.EntityAccess;
import java.util.Objects;

public final class EntityEvaluation {
    private final EntityAccess entity;
    private final EntityDisposition disposition;
    private final boolean skipped, cancelled;
    private EntityEvaluation(EntityAccess entity, EntityDisposition disposition, boolean skipped, boolean cancelled) {
        this.entity = Objects.requireNonNull(entity, "entity"); this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.skipped = skipped; this.cancelled = cancelled;
    }
    public static EntityEvaluation of(EntityAccess entity, EntityDisposition disposition) { return new EntityEvaluation(entity, disposition, false, false); }
    public static EntityEvaluation skipped(EntityAccess entity) { return new EntityEvaluation(entity, EntityDisposition.KEEP, true, false); }
    public EntityEvaluation cancel() { return new EntityEvaluation(entity, disposition, skipped, true); }
    public EntityAccess entity() { return entity; }
    public EntityDisposition disposition() { return disposition; }
    public boolean skipped() { return skipped; }
    public boolean cancelled() { return cancelled; }
}
