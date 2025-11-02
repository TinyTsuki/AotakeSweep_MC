package xin.vanilla.aotake.util;

import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AotakeScheduler {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Queue<ScheduledTask> serverTasks = new ConcurrentLinkedQueue<>();
    private static final Queue<ScheduledTask> clientTasks = new ConcurrentLinkedQueue<>();
    private static long clientTicks = 0;

    public static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        long executeAt = server.getTickCount() + delayTicks;
        serverTasks.add(new ScheduledTask(executeAt, action));
    }

    @OnlyIn(Dist.CLIENT)
    public static void schedule(int delayTicks, Runnable action) {
        long executeAt = clientTicks + delayTicks;
        clientTasks.add(new ScheduledTask(executeAt, action, true));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = AotakeSweep.getServerInstance().key();
        long currentTick = server.getTickCount();

        try {
            while (!serverTasks.isEmpty()) {
                ScheduledTask task = serverTasks.peek();
                if (task.executeTick <= currentTick) {
                    if (!task.clientSide) {
                        server.execute(task.runnable);
                    }
                    serverTasks.poll();
                } else break;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to execute task", e);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        clientTicks++;
        if (event.phase != TickEvent.Phase.END) return;
        long currentTick = clientTicks;

        try {
            while (!clientTasks.isEmpty()) {
                ScheduledTask task = clientTasks.peek();
                if (task.executeTick <= currentTick) {
                    if (task.clientSide) {
                        Minecraft.getInstance().execute(task.runnable);
                    }
                    clientTasks.poll();
                } else break;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to execute task", e);
        }
    }

    @Data
    @Accessors(chain = true)
    private static class ScheduledTask {
        private final long executeTick;
        private final Runnable runnable;
        private final boolean clientSide;

        public ScheduledTask(long executeTick, Runnable runnable) {
            this.executeTick = executeTick;
            this.runnable = runnable;
            this.clientSide = false;
        }

        public ScheduledTask(long executeTick, Runnable runnable, boolean clientSide) {
            this.executeTick = executeTick;
            this.runnable = runnable;
            this.clientSide = clientSide;
        }
    }
}
