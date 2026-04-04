package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ForgeConfigAdapter;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.network.ModLoadedPresence;
import xin.vanilla.banira.common.util.CommandUtils;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Mod(AotakeSweep.MODID)
public class AotakeSweep {

    public final static String DEFAULT_COMMAND_PREFIX = "aotake";

    public static final String MODID = "aotake_sweep";

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 玩家当前浏览的垃圾箱页数
     */
    @Getter
    private static final Map<String, Integer> playerDustbinPage = new ConcurrentHashMap<>();

    /**
     * 客户端-服务器时间
     */
    @Getter
    private static final KeyValue<Long, Long> clientServerTime = new KeyValue<>(0L, 0L);

    /**
     * 扫地间隔-下次扫地时间
     */
    @Getter
    private static final KeyValue<Long, Long> sweepTime = new KeyValue<>(0L, 0L);


    public static final Random RANDOM = new Random();

    @Getter
    @Setter
    private static boolean disable = false;

    @Getter
    private static final EntitySweeper entitySweeper = new EntitySweeper();
    @Getter
    private static final EntityFilter entityFilter = new EntityFilter();

    public AotakeSweep() {
        // 注册网络通道
        NetworkInit.registerPackets();

        ModLoadedPresence.register(MODID, player -> CommandUtils.refreshPermission(player));

        // 注册服务器启动和关闭事件
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);

        // 注册当前实例到事件总线
        MinecraftForge.EVENT_BUS.register(this);

        ForgeConfigAdapter.register(CommonConfig.class, MODID);
        ForgeConfigAdapter.register(ClientConfig.class, MODID);

        // 注册配置文件重载事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);
    }

    private void onServerStarting(FMLServerStartingEvent event) {
        entitySweeper.clear();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("Registering commands");
        AotakeCommand.register(event.getDispatcher());
    }

    public void onConfigReload(ModConfig.ModConfigEvent event) {
        try {
            ModConfig cfg = event.getConfig();
            ConfigHolder commonHolder = ForgeConfigAdapter.getHolder(CommonConfig.class);
            if (commonHolder != null && cfg.getSpec() == commonHolder.getSpec()
                    && BaniraCodex.serverInstance().val()) {
                entityFilter.clear();
            }
        } catch (Exception ignored) {
        }
    }

}
