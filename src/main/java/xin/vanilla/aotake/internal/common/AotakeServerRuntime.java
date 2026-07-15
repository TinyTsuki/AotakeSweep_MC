package xin.vanilla.aotake.internal.common;

import net.minecraft.server.MinecraftServer;
import xin.vanilla.banira.api.BaniraServer;

import javax.annotation.Nullable;

/** 将 Banira 的加载器无关服务器句柄收窄为当前 Minecraft 版本类型。 */
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
