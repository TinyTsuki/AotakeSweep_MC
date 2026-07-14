package xin.vanilla.aotake.internal.common;

import net.minecraft.server.MinecraftServer;
import xin.vanilla.banira.api.BaniraServer;

import javax.annotation.Nullable;

/**
 * Aotake 内部的版本类型适配，业务类不直接重复转换 Banira 服务器句柄。
 */
public final class AotakeServerRuntime {
    private AotakeServerRuntime() {
    }

    public static boolean isRunning() {
        return BaniraServer.isRunning();
    }

    @Nullable
    public static MinecraftServer currentServer() {
        return BaniraServer.currentAs(MinecraftServer.class);
    }

    public static MinecraftServer requireServer() {
        return BaniraServer.require(MinecraftServer.class);
    }
}
