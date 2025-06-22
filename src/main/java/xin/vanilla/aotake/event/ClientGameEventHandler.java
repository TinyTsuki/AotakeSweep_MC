package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
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
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
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

    /**
     * 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        EventHandlerProxy.onPlayerLoggedOut(event);
    }

    private static final Component MOD_NAME = Component.translatable(EnumI18nType.KEY, "categories");

    @SubscribeEvent
    public static void onRenderScreen(GuiScreenEvent event) {
        Screen screen = event.getGui();
        if (screen instanceof ChestScreen
                && Minecraft.getInstance().player != null
                && screen.getTitle().getContents()
                .startsWith(MOD_NAME.toTextComponent(AotakeUtils.getPlayerLanguage(Minecraft.getInstance().player)).getContents())
        ) {
            if (event instanceof GuiScreenEvent.InitGuiEvent.Post) {
                GuiScreenEvent.InitGuiEvent.Post eve = (GuiScreenEvent.InitGuiEvent.Post) event;
                ClientPlayerEntity player = Minecraft.getInstance().player;
                int yOffset = 0;
                // 清空缓存区
                if (AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                    eve.addWidget(
                            new Button(screen.width / 2 - 88 - 21
                                    , screen.height / 2 - 111 + 21 * (yOffset++)
                                    , 20, 20
                                    , Component.literal("✕").setColor(EnumMCColor.RED.getColor()).toTextComponent()
                                    , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, true))
                                    , (button, matrixStack, x, y) -> screen.renderTooltip(matrixStack
                                    , Component.translatable(EnumI18nType.MESSAGE, "clear_cache").toTextComponent(AotakeUtils.getClientLanguage())
                                    , x, y)
                            )
                    );
                }
                if (AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                    // 清空所有页
                    eve.addWidget(
                            new Button(screen.width / 2 - 88 - 21
                                    , screen.height / 2 - 111 + 21 * (yOffset++)
                                    , 20, 20
                                    , Component.literal("✕").setColor(EnumMCColor.RED.getColor()).toTextComponent()
                                    , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, false))
                                    , (button, matrixStack, x, y) -> screen.renderTooltip(matrixStack
                                    , Component.translatable(EnumI18nType.MESSAGE, "clear_all_dustbin").toTextComponent(AotakeUtils.getClientLanguage())
                                    , x, y)
                            )
                    );
                    // 清空当前页
                    eve.addWidget(
                            new Button(screen.width / 2 - 88 - 21
                                    , screen.height / 2 - 111 + 21 * (yOffset++)
                                    , 20, 20
                                    , Component.literal("✕").setColor(EnumMCColor.YELLOW.getColor()).toTextComponent()
                                    , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(false, false))
                                    , (button, matrixStack, x, y) -> screen.renderTooltip(matrixStack
                                    , Component.translatable(EnumI18nType.MESSAGE, "clear_cur_dustbin").toTextComponent(AotakeUtils.getClientLanguage())
                                    , x, y)
                            )
                    );
                }
                // 刷新当前页
                eve.addWidget(
                        new Button(screen.width / 2 - 88 - 21
                                , screen.height / 2 - 111 + 21 * (yOffset++)
                                , 20, 20
                                , Component.literal("↻").toTextComponent()
                                , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0))
                                , (button, matrixStack, x, y) -> screen.renderTooltip(matrixStack
                                , Component.translatable(EnumI18nType.MESSAGE, "refresh_page").toTextComponent(AotakeUtils.getClientLanguage())
                                , x, y)
                        )
                );
                // 上一页
                eve.addWidget(
                        new Button(screen.width / 2 - 88 - 21
                                , screen.height / 2 - 111 + 21 * (yOffset++)
                                , 20, 20
                                , Component.literal("▲").toTextComponent()
                                , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1))
                                , (button, matrixStack, x, y) -> screen.renderTooltip(matrixStack
                                , Component.translatable(EnumI18nType.MESSAGE, "previous_page").toTextComponent(AotakeUtils.getClientLanguage())
                                , x, y)
                        )
                );
                // 下一页
                eve.addWidget(
                        new Button(screen.width / 2 - 88 - 21
                                , screen.height / 2 - 111 + 21 * (yOffset++)
                                , 20, 20
                                , Component.literal("▼").toTextComponent()
                                , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1))
                                , (button, matrixStack, x, y) -> screen.renderTooltip(matrixStack
                                , Component.translatable(EnumI18nType.MESSAGE, "next_page").toTextComponent(AotakeUtils.getClientLanguage())
                                , x, y)
                        )
                );
            } else if (event instanceof GuiScreenEvent.KeyboardKeyPressedEvent.Pre) {
                GuiScreenEvent.KeyboardKeyPressedEvent.Pre keyEvent = (GuiScreenEvent.KeyboardKeyPressedEvent.Pre) event;
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
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        LOGGER.debug("Client: Player logged in.");
        // 通知服务器客户端已加载mod
        AotakeUtils.sendPacketToServer(new ClientLoadedToServer());
    }
}
