package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.network.packet.OpenDustbinNotice;
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (Minecraft.getInstance().screen == null) {
                if (ClientModEventHandler.DUSTBIN_KEY.consumeClick()) {
                    if (!keyDown) {
                        AotakeUtils.sendPacketToServer(new OpenDustbinNotice(0));
                        keyDown = true;
                    }
                } else {
                    keyDown = false;
                }
            } else {
                keyDown = false;
            }
        }
    }

    /**
     * 服务端Tick事件
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        EventHandlerProxy.onServerTick(event);
    }

    /**
     * 能力附加事件
     */
    @SubscribeEvent
    public static void onAttachCapabilityEvent(AttachCapabilitiesEvent<Entity> event) {
        EventHandlerProxy.onAttachCapabilityEvent(event);
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

    private static final Component MOD_NAME = Component.translatable(EnumI18nType.KEY, "categories");

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent event) {
        if (event.getScreen() instanceof ContainerScreen
                && Minecraft.getInstance().player != null
                && event.getScreen().getTitle().getString()
                .startsWith(MOD_NAME.toTextComponent(AotakeUtils.getPlayerLanguage(Minecraft.getInstance().player)).getString())
        ) {
            // if (event.isCancelable()) event.setCanceled(false);
            if (event instanceof ScreenEvent.Init.Post) {
                ((ScreenEvent.Init.Post) event).addListener(
                        new Button.Builder(Component.literal("▲").toTextComponent()
                                , button -> AotakeUtils.sendPacketToServer(new OpenDustbinNotice(-1)))
                                .size(20, 20)
                                .pos(event.getScreen().width / 2 - 88 - 21, event.getScreen().height / 2 - 111)
                                .build()
                );
                ((ScreenEvent.Init.Post) event).addListener(
                        new Button.Builder(Component.literal("▼").toTextComponent()
                                , button -> AotakeUtils.sendPacketToServer(new OpenDustbinNotice(1)))
                                .size(20, 20)
                                .pos(event.getScreen().width / 2 - 88 - 21, event.getScreen().height / 2 - 90)
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
                        AotakeUtils.sendPacketToServer(new OpenDustbinNotice(-1));
                        keyDown = true;
                    }
                } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_NEXT_KEY.getKey().getValue()) {
                    if (!keyDown) {
                        AotakeUtils.sendPacketToServer(new OpenDustbinNotice(1));
                        keyDown = true;
                    }
                } else {
                    keyDown = false;
                }
            }
        }
    }
}
