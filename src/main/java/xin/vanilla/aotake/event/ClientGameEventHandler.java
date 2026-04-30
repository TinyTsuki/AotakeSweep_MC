package xin.vanilla.aotake.event;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 原 Forge 客户端 Game 周期逻辑已废弃；进度条/HUD/垃圾箱界面由 Fabric {@link ClientEventHandler} 承担。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private ClientGameEventHandler() {
    }

    public static void register() {
        LOGGER.debug("ClientGameEventHandler: Fabric 侧请使用 ClientEventHandler");
    }
}
