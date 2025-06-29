package xin.vanilla.aotake.util;

import lombok.Data;
import lombok.experimental.Accessors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import xin.vanilla.aotake.AotakeSweep;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AotakeScheduler {
    private static final Queue<ScheduledTask> tasks = new ConcurrentLinkedQueue<>();

    public static void schedule(ServerWorld world, int delayTicks, Runnable action) {
        MinecraftServer server = world.getServer();
        long executeAt = server.getTickCount() + delayTicks;
        tasks.add(new ScheduledTask(executeAt, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = AotakeSweep.getServerInstance();
        long currentTick = server.getTickCount();

        while (!tasks.isEmpty()) {
            ScheduledTask task = tasks.peek();
            if (task.executeTick <= currentTick) {
                server.execute(task.runnable);
                tasks.poll();
            } else break;
        }
    }

    @Data
    @Accessors(chain = true)
    private static class ScheduledTask {
        private final long executeTick;
        private final Runnable runnable;
    }
}
