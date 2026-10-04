package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.api.sweep.*;
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
import java.lang.reflect.Proxy;
import java.util.*;

class SweepFixture implements SweepBackend {
    static final class Node {
        final String id;
        boolean safe, junk = true, valid = true;
        String dimension = "overworld", type = "item";
        Node parent;
        Node(String id) { this.id = id; }
    }
    final List<EntityAccess> source = new ArrayList<>();
    final List<String> events = new ArrayList<>();
    boolean usable = true, failExecution, failFlush;
    int typeLimit = 1, safeLimit = 1, filtered, executed, released, flushed, faults;
    EntityAccess add(String id) { EntityAccess e = view(new Node(id), () -> {}); source.add(e); return e; }
    static Node node(EntityAccess e) { return e.nativeEntity(Node.class); }
    static EntityAccess view(Node n, Runnable guard) {
        return (EntityAccess) Proxy.newProxyInstance(EntityAccess.class.getClassLoader(), new Class<?>[]{EntityAccess.class},
                (p, m, a) -> {
                    guard.run();
                    switch (m.getName()) {
                        case "nativeEntity": return ((Class<?>) a[0]).cast(n);
                        case "resourceId": return n.id;
                        case "alive": return n.valid;
                        case "removed": return !n.valid;
                        case "x": case "y": case "z": return 0D;
                        case "toString": return n.id;
                        default: throw new UnsupportedOperationException(m.getName());
                    }
                });
    }
    public long generationId() { return 7; }
    public void requireUsable() { if (!usable) throw new IllegalStateException("retired"); }
    public ServerAccess server(Runnable guard, Runnable entityGuard) {
        WorldAccess world = (WorldAccess) Proxy.newProxyInstance(WorldAccess.class.getClassLoader(), new Class<?>[]{WorldAccess.class}, (p, m, a) -> {
            guard.run();
            if (!m.getName().equals("entities")) throw new UnsupportedOperationException(m.getName());
            List<EntityAccess> result = new ArrayList<>();
            for (EntityAccess e : source) result.add(view(node(e), entityGuard));
            return result;
        });
        return (ServerAccess) Proxy.newProxyInstance(ServerAccess.class.getClassLoader(), new Class<?>[]{ServerAccess.class}, (p, m, a) -> {
            guard.run();
            if (m.getName().equals("world")) return Optional.of(world);
            throw new UnsupportedOperationException(m.getName());
        });
    }
    public NativeMembers members() { return NativeMembers.EMPTY; }
    public ConfigEditSnapshot configuration() {
        return new ConfigEditSnapshot(this, "test", new byte[0], Collections.singletonMap("limit", typeLimit));
    }
    public List<EntityAccess> collect(SweepRequest request, SweepScope scope, Runnable guard) {
        List<EntityAccess> result = new ArrayList<>();
        for (EntityAccess e : source) result.add(view(node(e), guard));
        return result;
    }
    public EntityAccess normalize(EntityAccess e, SweepScope scope, Runnable guard) {
        Node n = node(e);
        if (n.parent != null) n = n.parent;
        return n.valid && scope.contains(n.dimension, 0, 0) ? view(n, guard) : null;
    }
    public Object identity(EntityAccess e) { return node(e); }
    public DefaultSweepFilter.Entry<EntityAccess, Object> evaluate(SweepRequest request, EntityAccess e) {
        Node n = node(e);
        return new DefaultSweepFilter.Entry<>(e, n.safe, n.junk, n.type, n.dimension);
    }
    public List<EntityAccess> filter(SweepRequest request, List<DefaultSweepFilter.Entry<EntityAccess, Object>> scans) {
        filtered += scans.size();
        return DefaultSweepFilter.selectEntries(scans, typeLimit, safeLimit);
    }
    public List<EntityAccess> preflight(SweepRequest request, SweepScope scope, List<EntityAccess> selected) {
        events.add("preflight");
        return selected;
    }
    public void execute(BatchContext context, Progress progress) {
        executed++;
        events.add("execute" + context.index());
        for (EntityAccess ignored : context.entities()) {
            if (failExecution && progress.processed() == 1) throw new IllegalStateException("execute failed");
            progress.processed(1, 1, 1, true);
        }
    }
    public void flush() { flushed++; if (failFlush) throw new IllegalStateException("flush failed"); }
    public void release() { released++; }
    public void fault(Throwable error) { faults++; }
}
