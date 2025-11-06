package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
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
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.ClientLoadedToServer;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.aotake.screen.component.Text;
import xin.vanilla.aotake.util.*;

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

    private static long lastTime = 0;

    /**
     * 重新打开垃圾箱页面前鼠标位置
     */
    private static final KeyValue<Double, Double> mousePos = new KeyValue<>(0D, 0D);

    /**
     * 客户端Tick事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            if (ClientModEventHandler.DUSTBIN_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                lastTime = System.currentTimeMillis();
                AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0));
            }
            if (ClientConfig.PROGRESS_BAR_KEY_APPLY_MODE.get()) {
                if (ClientModEventHandler.PROGRESS_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                    lastTime = System.currentTimeMillis();
                    ProgressRender.setShowProgress(!ProgressRender.isShowProgress());
                }
            } else {
                ProgressRender.setShowProgress(ClientModEventHandler.PROGRESS_KEY.isDown());
            }
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
     * 玩家登录事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        EventHandlerProxy.onPlayerLoggedIn(event);
    }

    /**
     * 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        EventHandlerProxy.onPlayerLoggedOut(event);
    }

    private static final Component MOD_NAME = Component.translatable(EnumI18nType.KEY, "categories");

    private static final MouseHelper mouseHelper = new MouseHelper();

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent event) {
        Screen screen = event.getScreen();
        Minecraft mc = Minecraft.getInstance();
        if (screen instanceof ContainerScreen
                && mc.player != null
                && screen.getTitle().getString()
                .startsWith(MOD_NAME.toTextComponent(AotakeUtils.getPlayerLanguage(mc.player)).getString())
        ) {
            switch (event) {
                case ScreenEvent.Init.Post ignored -> {
                    for (int i = 0; i < 20; i++) {
                        AotakeScheduler.schedule(i, () -> {
                            if (mousePos.key() > -1 && mousePos.val() > -1) {
                                KeyValue<Double, Double> pos = MouseHelper.getRawCursorPos();
                                if (Math.abs(pos.key() - mousePos.key()) < 1 && Math.abs(pos.val() - mousePos.val()) < 1) {
                                    mousePos.setKey(-1D).setValue(-1D);
                                }
                                if (mousePos.key() > -1 && mousePos.val() > -1) {
                                    MouseHelper.setMouseRawPos(mousePos);
                                }
                            }
                        });
                    }
                }
                case ScreenEvent.Render.Post eve -> {
                    LocalPlayer player = mc.player;
                    int mouseX = eve.getMouseX();
                    int mouseY = eve.getMouseY();
                    mouseHelper.tick(mouseX, mouseY);

                    GuiGraphics graphics = eve.getGuiGraphics();
                    int baseW = 16;
                    int baseH = 16;
                    int baseX = screen.width / 2 - 88;
                    int baseY = screen.height / 2 - 110;
                    int yOffset = 0;
                    // 清空缓存区
                    if (AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isLeftPressing() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_cache.png");
                        AbstractGuiUtils.blitBlend(graphics, texture, x, y, 0, 0, w, h, w, h);

                        if (hover) {
                            AbstractGuiUtils.drawPopupMessage(Text.empty()
                                            .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_cache"))
                                            .setGraphics(graphics)
                                    , mouseX
                                    , mouseY
                                    , screen.width
                                    , screen.height
                            );
                        }

                        if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, true));
                        }
                    }
                    if (AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                        // 清空所有页
                        {
                            int w = baseW;
                            int h = baseH;
                            int x = baseX - w - 1;
                            int y = baseY + (h + 1) * (yOffset++);
                            boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                            if (mouseHelper.isLeftPressing() && hover) {
                                x--;
                                y--;
                                w += 2;
                                h += 2;
                            }

                            ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_all.png");
                            AbstractGuiUtils.blitBlend(graphics, texture, x, y, 0, 0, w, h, w, h);

                            if (hover) {
                                AbstractGuiUtils.drawPopupMessage(Text.empty()
                                                .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_all_dustbin"))
                                                .setGraphics(graphics)
                                        , mouseX
                                        , mouseY
                                        , screen.width
                                        , screen.height
                                );
                            }

                            if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                                AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, false));
                            }
                        }

                        // 清空当前页
                        {
                            int w = baseW;
                            int h = baseH;
                            int x = baseX - w - 1;
                            int y = baseY + (h + 1) * (yOffset++);
                            boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                            if (mouseHelper.isLeftPressing() && hover) {
                                x--;
                                y--;
                                w += 2;
                                h += 2;
                            }

                            ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_page.png");
                            AbstractGuiUtils.blitBlend(graphics, texture, x, y, 0, 0, w, h, w, h);

                            if (hover) {
                                AbstractGuiUtils.drawPopupMessage(Text.empty()
                                                .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_cur_dustbin"))
                                                .setGraphics(graphics)
                                        , mouseX
                                        , mouseY
                                        , screen.width
                                        , screen.height
                                );
                            }

                            if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                                AotakeUtils.sendPacketToServer(new ClearDustbinToServer(false, false));
                            }
                        }
                    }
                    // 刷新当前页
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isLeftPressing() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "refresh.png");
                        AbstractGuiUtils.blitBlend(graphics, texture, x, y, 0, 0, w, h, w, h);

                        if (hover) {
                            AbstractGuiUtils.drawPopupMessage(Text.empty()
                                            .setText(Component.translatable(EnumI18nType.MESSAGE, "refresh_page"))
                                            .setGraphics(graphics)
                                    , mouseX
                                    , mouseY
                                    , screen.width
                                    , screen.height
                            );
                        }

                        if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0));
                        }
                    }
                    // 上一页
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isLeftPressing() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "up.png");
                        AbstractGuiUtils.blitBlend(graphics, texture, x, y, 0, 0, w, h, w, h);

                        if (hover) {
                            AbstractGuiUtils.drawPopupMessage(Text.empty()
                                            .setText(Component.translatable(EnumI18nType.MESSAGE, "previous_page"))
                                            .setGraphics(graphics)
                                    , mouseX
                                    , mouseY
                                    , screen.width
                                    , screen.height
                            );
                        }

                        if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                        }
                    }
                    // 下一页
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isLeftPressing() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "down.png");
                        AbstractGuiUtils.blitBlend(graphics, texture, x, y, 0, 0, w, h, w, h);

                        if (hover) {
                            AbstractGuiUtils.drawPopupMessage(Text.empty()
                                            .setText(Component.translatable(EnumI18nType.MESSAGE, "next_page"))
                                            .setGraphics(graphics)
                                    , mouseX
                                    , mouseY
                                    , screen.width
                                    , screen.height
                            );
                        }

                        if (mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1));
                        }
                    }
                }
                case ScreenEvent.KeyPressed.Pre keyEvent -> {
                    if (keyEvent.getModifiers() != 0) return;
                    if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_KEY.getKey().getValue()) {
                        if (System.currentTimeMillis() - lastTime > 200) {
                            lastTime = System.currentTimeMillis();
                            mc.setScreen(null);
                        }
                    } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_PRE_KEY.getKey().getValue()) {
                        if (System.currentTimeMillis() - lastTime > 200) {
                            lastTime = System.currentTimeMillis();
                            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                        }
                    } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_NEXT_KEY.getKey().getValue()) {
                        if (System.currentTimeMillis() - lastTime > 200) {
                            lastTime = System.currentTimeMillis();
                            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1));
                        }
                    }
                }
                default -> {
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
