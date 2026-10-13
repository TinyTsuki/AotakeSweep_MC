package xin.vanilla.aotake.client.minimap;

import org.junit.Test;
import xin.vanilla.banira.common.network.BaniraNetworkContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class SweepCountdownConnectionTest {
    private Object connection(AtomicReference<Object> current) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("xin.vanilla.aotake.client.minimap.SweepCountdownConnection");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing connection-bound countdown receiver", e);
        }
        AtomicLong time = new AtomicLong();
        return type.getConstructor(LongSupplier.class, Supplier.class)
                .newInstance((LongSupplier) time::get, (Supplier<Object>) current::get);
    }

    private Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) return method.invoke(target, args);
        }
        throw new AssertionError("Missing connection method " + name);
    }

    private static final class PacketContext implements BaniraNetworkContext {
        private final Object key;
        private final List<Runnable> pending = new ArrayList<>();

        private PacketContext(Object key) { this.key = key; }
        @Override public Object connectionKey() { return key; }
        @Override public void enqueueWork(Runnable work) { pending.add(work); }
        @Override public void markHandled() { }
        @Override public boolean isClientSide() { return true; }
        @Override public boolean isServerSide() { return false; }
        @Override public Object sender() { return null; }
        private void flush() { for (Runnable work : pending) work.run(); pending.clear(); }
    }

    @Test
    public void acceptedSyncAlsoUpdatesCompatibleCachesOnce() throws Exception {
        Object key = new Object();
        Object receiver = connection(new AtomicReference<>(key));
        PacketContext ctx = new PacketContext(key);
        AtomicInteger cache = new AtomicInteger();
        call(receiver, "accept", ctx, 1000L, 61000L, 60000L, (Runnable) cache::incrementAndGet);
        assertEquals("", call(receiver, "formattedTime"));
        ctx.flush();
        assertEquals("01:00", call(receiver, "formattedTime"));
        assertEquals(1, cache.get());
    }

    @Test
    public void queuedOldConnectionCannotRestoreTimeOrCompatibleCaches() throws Exception {
        Object old = new Object();
        AtomicReference<Object> live = new AtomicReference<>(old);
        Object receiver = connection(live);
        PacketContext ctx = new PacketContext(old);
        AtomicInteger cache = new AtomicInteger();
        call(receiver, "accept", ctx, 1000L, 61000L, 60000L, (Runnable) cache::incrementAndGet);
        live.set(new Object());
        call(receiver, "beginConnection");
        ctx.flush();
        assertEquals("", call(receiver, "formattedTime"));
        assertEquals(0, cache.get());
    }

    @Test
    public void packetArrivingAfterReconnectWithOldKeyIsRejected() throws Exception {
        Object old = new Object();
        Object current = new Object();
        Object receiver = connection(new AtomicReference<>(current));
        AtomicInteger cache = new AtomicInteger();
        PacketContext oldCtx = new PacketContext(old);
        call(receiver, "accept", oldCtx, 1000L, 61000L, 60000L, (Runnable) cache::incrementAndGet);
        oldCtx.flush();
        assertEquals("", call(receiver, "formattedTime"));
        assertEquals(0, cache.get());
        PacketContext newCtx = new PacketContext(current);
        call(receiver, "accept", newCtx, 1000L, 31000L, 60000L, (Runnable) cache::incrementAndGet);
        newCtx.flush();
        assertEquals("00:30", call(receiver, "formattedTime"));
        assertEquals(1, cache.get());
    }

    @Test
    public void nullIdentityIsNeverAcceptedAsWildcard() throws Exception {
        Object receiver = connection(new AtomicReference<>(new Object()));
        PacketContext ctx = new PacketContext(null);
        AtomicInteger cache = new AtomicInteger();
        call(receiver, "accept", ctx, 1000L, 61000L, 60000L, (Runnable) cache::incrementAndGet);
        ctx.flush();
        assertEquals("", call(receiver, "formattedTime"));
        assertEquals(0, cache.get());
    }

    @Test
    public void loginEventAfterEarlySyncOnSameConnectionPreservesIt() throws Exception {
        Object key = new Object();
        Object receiver = connection(new AtomicReference<>(key));
        PacketContext ctx = new PacketContext(key);
        call(receiver, "accept", ctx, 1000L, 61000L, 60000L, (Runnable) () -> { });
        ctx.flush();
        call(receiver, "beginConnection");
        assertEquals("01:00", call(receiver, "formattedTime"));
    }

    @Test
    public void disconnectClearsStateAndQueuedSameKeySync() throws Exception {
        Object key = new Object();
        AtomicReference<Object> live = new AtomicReference<>(key);
        Object receiver = connection(live);
        PacketContext ctx = new PacketContext(key);
        call(receiver, "accept", ctx, 1000L, 61000L, 60000L, (Runnable) () -> { });
        ctx.flush();
        AtomicInteger cache = new AtomicInteger();
        call(receiver, "accept", ctx, 1000L, 11000L, 60000L, (Runnable) cache::incrementAndGet);
        live.set(null);
        call(receiver, "reset");
        ctx.flush();
        assertEquals("", call(receiver, "formattedTime"));
        assertEquals(0, cache.get());
    }
}
