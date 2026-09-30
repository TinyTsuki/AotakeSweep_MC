package xin.vanilla.aotake.internal.forge.rule;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import xin.vanilla.aotake.api.rule.*;
import java.util.*;
import java.util.function.Supplier;

public final class ForgeServerAccess implements ServerAccess {
    private final Supplier<MinecraftServer> source;
    private final Runnable guard;
    public ForgeServerAccess(Supplier<MinecraftServer> source, Runnable guard) {
        this.source = Objects.requireNonNull(source); this.guard = Objects.requireNonNull(guard);
    }
    private MinecraftServer current() { guard.run(); return Objects.requireNonNull(source.get(), "server"); }
    @Override public long tick() { return current().getTickCount(); }
    @Override public List<EntityAccess> players() { return ForgeEntityAccess.views(current().getPlayerList().getPlayers(), guard); }
    @Override public Optional<WorldAccess> world(String dimensionId) {
        ResourceLocation id = new ResourceLocation(dimensionId);
        for (ServerWorld world : current().getAllLevels()) {
            if (world.dimension().location().equals(id)) return Optional.of(new ForgeWorldAccess(() -> world, guard));
        }
        return Optional.empty();
    }
    @Override public <T> T nativeServer(Class<T> type) { return ForgeEntityAccess.cast(current(), type); }
}
