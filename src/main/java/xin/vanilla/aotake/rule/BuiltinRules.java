package xin.vanilla.aotake.rule;

import xin.vanilla.aotake.rule.migration.JavaRuleEmitter;
import xin.vanilla.aotake.rule.migration.LegacyRuleParser;
import xin.vanilla.aotake.rule.migration.RuleAst;
import xin.vanilla.aotake.rule.migration.RuleTypeResolver;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Installs editable defaults once; existing sources remain administrator-owned. */
public final class BuiltinRules {
    public static final String CLEANUP = "builtin_cleanup";
    public static final String PROTECTION = "builtin_protection";

    private BuiltinRules() { }

    static void install(RuleFileStore store) throws IOException {
        RuleCatalog existing = store.loadCatalog();
        Map<String, RuleSource> additions = new LinkedHashMap<>();
        add(existing, additions, CLEANUP, "tick, clazz, itemClazz, createProcessing = [CreateData.Processing.Time]"
                + " -> tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)");
        add(existing, additions, PROTECTION, "customName, hasOwner, createProcessing = [CreateData.Processing.Time]"
                + " -> customName != null || hasOwner || createProcessing > 0");
        if (additions.isEmpty()) return;
        RuleCatalog catalog = RuleCatalog.extend(existing, additions);
        for (Map.Entry<String, byte[]> file : catalog.files().entrySet()) store.writeNew(file.getKey(), file.getValue());
        store.replace(RuleFileStore.MANIFEST, existing.originalManifest(), catalog.manifestBytes());
    }

    private static void add(RuleCatalog existing, Map<String, RuleSource> additions, String id, String expression) {
        if (existing.sources().containsKey(id)) return;
        RuleAst ast = LegacyRuleParser.parse(expression);
        additions.put(id, JavaRuleEmitter.emit(id, ast, RuleTypeResolver.resolve(ast, RuleBindingPlan.defaults())));
    }
}
