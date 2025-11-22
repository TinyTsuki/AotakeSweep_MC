package xin.vanilla.aotake.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;

import javax.annotation.Nonnull;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class AotakeScheduler {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final PriorityBlockingQueue<ScheduledTask> serverTasks = new PriorityBlockingQueue<>();
    private static final PriorityBlockingQueue<ScheduledTask> clientTasks = new PriorityBlockingQueue<>();

    private static final AtomicLong SEQ = new AtomicLong(0);

    private static final AtomicLong serverExecutedCount = new AtomicLong(0);
    private static final AtomicLong clientExecutedCount = new AtomicLong(0);
    private static final AtomicLong clientTicks = new AtomicLong(0);

    public static void schedule(@Nonnull MinecraftServer server, int delayTicks, @Nonnull Runnable action) {
        long executeAt = server.getTickCount() + Math.max(0, delayTicks);
        serverTasks.add(ScheduledTask.server(executeAt, action));
    }

    @OnlyIn(Dist.CLIENT)
    public static void schedule(int delayTicks, @Nonnull Runnable action) {
        long executeAt = clientTicks.get() + Math.max(0, delayTicks);
        clientTasks.add(ScheduledTask.client(executeAt, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = AotakeSweep.getServerInstance().key();
        if (server == null) return;

        runTask(server.getTickCount(), serverTasks, serverExecutedCount);
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        runTask(clientTicks.incrementAndGet(), clientTasks, clientExecutedCount);
    }

    private static void runTask(long currentTick, PriorityBlockingQueue<ScheduledTask> scheduledTasks, AtomicLong executedCount) {
        try {
            while (true) {
                ScheduledTask task = scheduledTasks.peek();
                if (task == null) break;
                if (task.executeTick() <= currentTick) {
                    task = scheduledTasks.poll();
                    if (task == null) break;
                    try {
                        task.runnable().run();
                        executedCount.incrementAndGet();
                    } catch (Throwable t) {
                        LOGGER.warn("Scheduled task threw an exception", t);
                    }
                } else {
                    break;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Failed while executing scheduled tasks", t);
        }
    }

    public static int getServerPendingTaskCount() {
        return serverTasks.size();
    }

    public static long getServerExecutedCount() {
        return serverExecutedCount.get();
    }

    public static int getClientPendingTaskCount() {
        return clientTasks.size();
    }

    public static long getClientExecutedCount() {
        return clientExecutedCount.get();
    }

    public static int getPendingTaskCount() {
        return getServerPendingTaskCount() + getClientPendingTaskCount();
    }

    public static long getExecutedCount() {
        return getServerExecutedCount() + getClientExecutedCount();
    }

    public static boolean removeTask(ScheduledTask task) {
        if (task == null) return false;
        return serverTasks.remove(task);
    }

    @Getter
    @AllArgsConstructor
    @Accessors(fluent = true)
    public static class ScheduledTask implements Comparable<ScheduledTask> {
        private final long seqNo;
        private final long executeTick;
        private final Runnable runnable;
        private final boolean clientSide;

        public static ScheduledTask server(long executeTick, Runnable runnable) {
            return new ScheduledTask(executeTick, SEQ.getAndIncrement(), runnable, false);
        }

        public static ScheduledTask client(long executeTick, Runnable runnable) {
            return new ScheduledTask(executeTick, SEQ.getAndIncrement(), runnable, true);
        }

        @Override
        public int compareTo(ScheduledTask o) {
            int cmp = Long.compare(this.executeTick, o.executeTick);
            if (cmp != 0) return cmp;
            return Long.compare(this.seqNo, o.seqNo);
        }

        @Override
        public String toString() {
            return "ScheduledTask{" +
                    "executeTick=" + executeTick +
                    ", seqNo=" + seqNo +
                    ", runnable=" + runnable +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ScheduledTask)) return false;
            ScheduledTask that = (ScheduledTask) o;
            return executeTick == that.executeTick && seqNo == that.seqNo && runnable.equals(that.runnable);
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(executeTick);
            result = 31 * result + Long.hashCode(seqNo);
            result = 31 * result + runnable.hashCode();
            return result;
        }
    }
}
