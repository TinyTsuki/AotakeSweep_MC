package xin.vanilla.aotake.util;

import lombok.experimental.Accessors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AotakeScheduler {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Queue<ScheduledTask> tasks = new ConcurrentLinkedQueue<>();

    public static void schedule(ServerLevel world, int delayTicks, Runnable action) {
        MinecraftServer server = world.getServer();
        long executeAt = server.getTickCount() + delayTicks;
        tasks.add(new ScheduledTask(executeAt, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long currentTick = server.getTickCount();

        try {
            while (!tasks.isEmpty()) {
                ScheduledTask task = tasks.peek();
                if (task.executeTick <= currentTick) {
                    server.execute(task.runnable);
                    tasks.poll();
                } else break;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to execute task", e);
        }
    }

    @Accessors(chain = true)
    private record ScheduledTask(long executeTick, Runnable runnable) {
    }
}
