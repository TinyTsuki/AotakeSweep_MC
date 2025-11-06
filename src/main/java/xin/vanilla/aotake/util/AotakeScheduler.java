package xin.vanilla.aotake.util;

import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AotakeScheduler {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Queue<ScheduledTask> serverTasks = new ConcurrentLinkedQueue<>();
    private static final Queue<ScheduledTask> clientTasks = new ConcurrentLinkedQueue<>();
    private static long clientTicks = 0;

    public static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        long executeAt = server.getTickCount() + delayTicks;
        serverTasks.add(new ScheduledTask(executeAt, action, false));
    }

    @OnlyIn(Dist.CLIENT)
    public static void schedule(int delayTicks, Runnable action) {
        long executeAt = clientTicks + delayTicks;
        clientTasks.add(new ScheduledTask(executeAt, action, true));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
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
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        clientTicks++;
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

    @Accessors(chain = true)
    private record ScheduledTask(long executeTick, Runnable runnable, boolean clientSide) {
    }
}
