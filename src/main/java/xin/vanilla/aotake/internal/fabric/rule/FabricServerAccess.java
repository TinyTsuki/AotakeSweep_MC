package xin.vanilla.aotake.internal.fabric.rule;

import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.rule.ServerAccess;
import xin.vanilla.aotake.api.rule.WorldAccess;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class FabricServerAccess implements ServerAccess {
    private final Supplier<MinecraftServer> source;
    private final Runnable guard;
    private final Runnable entityGuard;

    public FabricServerAccess(Supplier<MinecraftServer> source, Runnable guard) {
        this(source, guard, guard);
    }

    public FabricServerAccess(Supplier<MinecraftServer> source, Runnable guard, Runnable entityGuard) {
        this.source = Objects.requireNonNull(source);
        this.guard = Objects.requireNonNull(guard);
        this.entityGuard = Objects.requireNonNull(entityGuard);
    }

    private MinecraftServer current() {
        guard.run();
        return Objects.requireNonNull(source.get(), "server");
    }

    @Override
    public long tick() {
        return current().getTickCount();
    }

    @Override
    public List<EntityAccess> players() {
        return FabricEntityAccess.views(current().getPlayerList().getPlayers(), entityGuard);
    }

    @Override
    public Optional<WorldAccess> world(String dimensionId) {
        ResourceLocation id = new ResourceLocation(dimensionId);
        for (ServerLevel world : current().getAllLevels()) {
            if (world.dimension().location().equals(id))
                return Optional.of(new FabricWorldAccess(() -> world, guard, entityGuard));
        }
        return Optional.empty();
    }

    @Override
    public <T> T nativeServer(Class<T> type) {
        return FabricEntityAccess.cast(current(), type);
    }
}
