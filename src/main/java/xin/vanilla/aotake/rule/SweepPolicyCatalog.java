package xin.vanilla.aotake.rule;

import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.api.sweep.*;
import xin.vanilla.banira.api.script.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** Immutable code/member snapshot. Preparation does not activate policies or construct task instances. */
public final class SweepPolicyCatalog {
    public static final String CONFIG_PATH = "base.sweep.sweepPolicyId", DEFAULT_ID = "builtin:default";
    private final RuleFileStore store;
    private final Map<String, ScriptSourceGroup> groups;
    private final Map<String, NativeMemberDeclarations> members;
    private final Map<String, byte[]> files;

    SweepPolicyCatalog(RuleFileStore store, Map<String, ScriptSourceGroup> groups, Map<String, NativeMemberDeclarations> members, Map<String, byte[]> files) {
        this.store = store; this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
        this.members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        this.files = new LinkedHashMap<>(); files.forEach((path, bytes) -> this.files.put(path, bytes.clone()));
    }
    public Set<String> ids() { return groups.keySet(); }
    public CompletableFuture<PreparedScripts<ScriptFactory<SweepPolicy>>> prepare(ScriptSession<ScriptFactory<SweepPolicy>> session) {
        return session.prepareGroups(new ArrayList<>(groups.values()));
    }
    public Prepared bind(PreparedScripts<ScriptFactory<SweepPolicy>> compiled,
                         BiFunction<List<NativeMemberSpec>, ClassLoader, NativeMembers> binder) {
        if (!compiled.scripts().keySet().equals(groups.keySet())) throw new IllegalArgumentException("Compiled policy catalog mismatch");
        Map<String, NativeMembers> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, ScriptFactory<SweepPolicy>> entry : compiled.scripts().entrySet()) {
            ClassLoader loader = entry.getValue().entryType().getClassLoader();
            bindings.put(entry.getKey(), members.get(entry.getKey()).prepare(loader, binder));
        }
        return new Prepared(this, compiled.scripts(), bindings);
    }
    public void verifyFiles() throws IOException {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            int limit = file.getKey().equals(SweepPolicyFileStore.MANIFEST) ? RuleFileStore.MAX_METADATA_BYTES : RuleFileStore.MAX_SOURCE_BYTES;
            if (!Arrays.equals(file.getValue(), store.read(file.getKey(), limit))) throw new IOException("Policy file changed during preparation: " + file.getKey());
        }
    }
    public static final class Prepared {
        private final SweepPolicyCatalog source;
        private final Map<String, ScriptFactory<SweepPolicy>> factories;
        private final Map<String, NativeMembers> bindings;
        private final Set<String> ids;
        private Prepared(SweepPolicyCatalog source, Map<String, ScriptFactory<SweepPolicy>> factories, Map<String, NativeMembers> bindings) {
            this.source = source; this.factories = Collections.unmodifiableMap(new LinkedHashMap<>(factories));
            this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
            Set<String> ids = new LinkedHashSet<>(); ids.add(DEFAULT_ID); ids.addAll(factories.keySet()); this.ids = Collections.unmodifiableSet(ids);
        }
        public Set<String> ids() { return ids; }
        public void requireId(String id) { if (!ids.contains(id)) throw new IllegalArgumentException("Policy has not been locally prepared: " + id); }
        public SweepPolicy<?> create(String id) { requireId(id); return DEFAULT_ID.equals(id) ? new DefaultSweepPolicy<Void>() : factories.get(id).create(); }
        public NativeMembers members(String id) { requireId(id); return DEFAULT_ID.equals(id) ? NativeMembers.EMPTY : bindings.get(id); }
        public void verifyFiles() throws IOException { source.verifyFiles(); }
    }
}
