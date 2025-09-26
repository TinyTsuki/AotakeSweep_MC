package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.ClientLoadedToServer;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.Component;

/**
 * 客户端 Game事件处理器
 */
@Mod.EventBusSubscriber(modid = AotakeSweep.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LOGGER.debug("Client: Player logged out.");
    }

    private static boolean keyDown = false;

    /**
     * 客户端Tick事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            if (ClientModEventHandler.DUSTBIN_KEY.consumeClick()) {
                if (!keyDown) {
                    AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0));
                    keyDown = true;
                }
            } else {
                keyDown = false;
            }
        } else {
            keyDown = false;
        }
    }

    /**
     * 服务端Tick事件
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        EventHandlerProxy.onServerTick(event);
    }

    /**
     * 世界Tick事件
     */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent.Post event) {
        EventHandlerProxy.onWorldTick(event);
    }

    /**
     * 玩家死亡后重生或者从末地回主世界
     */
    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        EventHandlerProxy.onPlayerCloned(event);
    }

    /**
     * 玩家事件
     */
    @SubscribeEvent
    public static void onPlayerEvent(PlayerEvent event) {
        if (event instanceof PlayerEvent.Clone) return;
        EventHandlerProxy.onPlayerUseItem(event);
    }

    /**
     * 玩家使用物品
     */
    @SubscribeEvent
    public static void onPlayerUseItem(PlayerInteractEvent.RightClickItem event) {
        EventHandlerProxy.onPlayerUseItem(event);
    }

    /**
     * 玩家右键方块事件
     */
    @SubscribeEvent
    public static void onRightBlock(PlayerInteractEvent.RightClickBlock event) {
        EventHandlerProxy.onRightBlock(event);
    }

    /**
     * 玩家右键实体事件
     */
    @SubscribeEvent
    public static void onRightEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        EventHandlerProxy.onRightEntity(event);
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        EventHandlerProxy.onContainerOpen(event);
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        EventHandlerProxy.onContainerClose(event);
    }

    /**
     * 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        EventHandlerProxy.onPlayerLoggedOut(event);
    }

    private static final Component MOD_NAME = Component.translatable(EnumI18nType.KEY, "categories");

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent event) {
        Screen screen = event.getScreen();
        if (screen instanceof ContainerScreen
                && Minecraft.getInstance().player != null
                && screen.getTitle().getString()
                .startsWith(MOD_NAME.toTextComponent(AotakeUtils.getPlayerLanguage(Minecraft.getInstance().player)).getString())
        ) {
            if (event instanceof ScreenEvent.Init.Post eve) {
                LocalPlayer player = Minecraft.getInstance().player;
                int yOffset = 0;
                // 清空缓存区
                if (AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                    eve.addListener(
                            new Button.Builder(Component.literal("✕").setColor(EnumMCColor.RED.getColor()).toTextComponent()
                                    , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, true)))
                                    .size(20, 20)
                                    .pos(screen.width / 2 - 88 - 21, screen.height / 2 - 111 + 21 * (yOffset++))
                                    .tooltip(Tooltip.create(Component.translatable(EnumI18nType.MESSAGE, "clear_cache").toTextComponent(AotakeUtils.getClientLanguage())))
                                    .build()
                    );
                }
                // 清空所有页
                if (AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                    eve.addListener(
                            new Button.Builder(Component.literal("✕").setColor(EnumMCColor.RED.getColor()).toTextComponent()
                                    , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, false)))
                                    .size(20, 20)
                                    .pos(screen.width / 2 - 88 - 21, screen.height / 2 - 111 + 21 * (yOffset++))
                                    .tooltip(Tooltip.create(Component.translatable(EnumI18nType.MESSAGE, "clear_all_dustbin").toTextComponent(AotakeUtils.getClientLanguage())))
                                    .build()
                    );
                    // 清空当前页
                    eve.addListener(
                            new Button.Builder(Component.literal("✕").setColor(EnumMCColor.YELLOW.getColor()).toTextComponent()
                                    , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(false, false)))
                                    .size(20, 20)
                                    .pos(screen.width / 2 - 88 - 21, screen.height / 2 - 111 + 21 * (yOffset++))
                                    .tooltip(Tooltip.create(Component.translatable(EnumI18nType.MESSAGE, "clear_cur_dustbin").toTextComponent(AotakeUtils.getClientLanguage())))
                                    .build()
                    );
                }
                // 刷新当前页
                eve.addListener(
                        new Button.Builder(Component.literal("↻").toTextComponent()
                                , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0)))
                                .size(20, 20)
                                .pos(screen.width / 2 - 88 - 21, screen.height / 2 - 111 + 21 * (yOffset++))
                                .tooltip(Tooltip.create(Component.translatable(EnumI18nType.MESSAGE, "refresh_page").toTextComponent(AotakeUtils.getClientLanguage())))
                                .build()
                );
                // 上一页
                eve.addListener(
                        new Button.Builder(Component.literal("▲").toTextComponent()
                                , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1)))
                                .size(20, 20)
                                .pos(screen.width / 2 - 88 - 21, screen.height / 2 - 111 + 21 * (yOffset++))
                                .tooltip(Tooltip.create(Component.translatable(EnumI18nType.MESSAGE, "previous_page").toTextComponent(AotakeUtils.getClientLanguage())))
                                .build()
                );
                // 下一页
                eve.addListener(
                        new Button.Builder(Component.literal("▼").toTextComponent()
                                , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1)))
                                .size(20, 20)
                                .pos(screen.width / 2 - 88 - 21, screen.height / 2 - 111 + 21 * (yOffset++))
                                .tooltip(Tooltip.create(Component.translatable(EnumI18nType.MESSAGE, "next_page").toTextComponent(AotakeUtils.getClientLanguage())))
                                .build()
                );
            } else if (event instanceof ScreenEvent.KeyPressed.Pre keyEvent) {
                if (keyEvent.getModifiers() != 0) return;
                if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_KEY.getKey().getValue()) {
                    if (!keyDown) {
                        Minecraft.getInstance().setScreen(null);
                        keyDown = true;
                    }
                } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_PRE_KEY.getKey().getValue()) {
                    if (!keyDown) {
                        AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                        keyDown = true;
                    }
                } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_NEXT_KEY.getKey().getValue()) {
                    if (!keyDown) {
                        AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1));
                        keyDown = true;
                    }
                } else {
                    keyDown = false;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.debug("Client: Player logged in.");
        // 通知服务器客户端已加载mod
        AotakeUtils.sendPacketToServer(new ClientLoadedToServer());
    }
}
