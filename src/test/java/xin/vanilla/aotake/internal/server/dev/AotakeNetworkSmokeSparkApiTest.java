package xin.vanilla.aotake.internal.server.dev;

import org.junit.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** Checks the actual dev runtime API without starting Spark or Minecraft. */
public class AotakeNetworkSmokeSparkApiTest {
    @Test
    public void specificDumperAcceptsTheActualRenderThreadNotLegacyThreadIds() throws Exception {
        Class<?> type = Class.forName("me.lucko.spark.common.sampler.ThreadDumper$Specific");
        Thread renderThread = Thread.currentThread();
        Object dumper = type.getConstructor(Thread.class).newInstance(renderThread);
        Set<?> threads = (Set<?>) type.getMethod("getThreads").invoke(dumper);
        assertEquals(1, threads.size());
        assertTrue(threads.contains(renderThread));
    }

    @Test
    public void serverDumperUsesTheCurrentGameThread() throws Exception {
        Class<?> type = Class.forName("me.lucko.spark.common.sampler.ThreadDumper$GameThread");
        Object gameThread = type.getConstructor().newInstance();
        type.getMethod("setThread", Thread.class).invoke(gameThread, Thread.currentThread());
        Object dumper = type.getMethod("get").invoke(gameThread);
        assertNotNull(dumper);
        assertTrue((Boolean) dumper.getClass().getMethod("isThreadIncluded", long.class, String.class)
                .invoke(dumper, Thread.currentThread().getId(), Thread.currentThread().getName()));
    }

    @Test
    public void nativeExportAcceptsLazySuppliersAndTwoArguments() throws Exception {
        Class<?> propsType = Class.forName("me.lucko.spark.common.sampler.Sampler$ExportProps");
        Object props = propsType.getConstructor().newInstance();
        AtomicInteger calls = new AtomicInteger();
        Supplier<Object> supplier = () -> { calls.incrementAndGet(); return null; };
        propsType.getMethod("mergeMode", Supplier.class).invoke(props, supplier);
        propsType.getMethod("classSourceLookup", Supplier.class).invoke(props, supplier);
        assertSame(supplier, propsType.getMethod("mergeMode").invoke(props));
        assertSame(supplier, propsType.getMethod("classSourceLookup").invoke(props));
        assertEquals(0, calls.get());
        Class<?> sampler = Class.forName("me.lucko.spark.common.sampler.Sampler");
        assertNotNull(sampler.getMethod("toProto", Class.forName("me.lucko.spark.common.SparkPlatform"), propsType));
        assertNotNull(sampler.getMethod("stop", boolean.class));
    }
}
