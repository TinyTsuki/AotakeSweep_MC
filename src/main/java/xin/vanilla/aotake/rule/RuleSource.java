package xin.vanilla.aotake.rule;

import xin.vanilla.banira.api.script.ScriptSource;
import java.util.*;

public final class RuleSource {
    public static final String CONTRACT_VERSION = "1";
    private final ScriptSource script;
    private final RuleBindingPlan bindings;
    private final String sourceHash;
    private final List<String> adjustments;
    public RuleSource(ScriptSource script, RuleBindingPlan bindings, String sourceHash, List<String> adjustments) {
        this.script = script; this.bindings = bindings; this.sourceHash = sourceHash;
        this.adjustments = Collections.unmodifiableList(new ArrayList<>(adjustments));
    }
    public ScriptSource script() { return script; }
    public RuleBindingPlan bindings() { return bindings; }
    public String sourceHash() { return sourceHash; }
    public List<String> adjustments() { return adjustments; }
}
