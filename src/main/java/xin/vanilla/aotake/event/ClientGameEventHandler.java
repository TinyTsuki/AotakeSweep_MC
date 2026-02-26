package xin.vanilla.aotake.event;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;
import xin.vanilla.aotake.mixin.AbstractContainerScreenAccessor;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.ModLoadedToBoth;
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
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(AotakeUtils.getCommandPrefix())
                .then(Commands.literal("config")
                        .then(Commands.literal("client")
                                .then(Commands.argument("configKey", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            String input = CommandUtils.getStringEmpty(context, "configKey");
                                            CommandUtils.configKeySuggestion(ClientConfig.class, builder, input);
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("configValue", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    String configKey = StringArgumentType.getString(context, "configKey");
                                                    CommandUtils.configValueSuggestion(ClientConfig.class, builder, configKey);
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    Player player = Minecraft.getInstance().player;
                                                    if (player == null) return 0;
                                                    String configKey = StringArgumentType.getString(context, "configKey");
                                                    String configValue = StringArgumentType.getString(context, "configValue");
                                                    return CommandUtils.executeModifyConfigClient(ClientConfig.class, player, configKey, configValue);
                                                })
                                        )
                                )
                        )
                )
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LOGGER.debug("Client: Player logged out.");
    }

    private static long lastTime = 0;

    /**
     * 重新打开垃圾箱页面前鼠标位置
     */
    private static final KeyValue<Double, Double> mousePos = new KeyValue<>(-1D, -1D);
    private static int mouseRestoreTicks = 0;
    private static int dustbinPage = -1;
    private static int dustbinTotalPage = -1;
    /**
     * 是否显示进度条
     */
    private static boolean showProgress = false;

    public static void updateDustbinPage(int page, int totalPage) {
        dustbinPage = page;
        dustbinTotalPage = totalPage;
    }

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

    private static final Component TITLE = Component.translatable(EnumI18nType.WORD, "title");

    private static final MouseHelper mouseHelper = new MouseHelper();
    private static Button dustbinPrevButton;
    private static Button dustbinNextButton;

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent event) {
        Screen screen = event.getScreen();
        Minecraft mc = Minecraft.getInstance();
        if (screen instanceof ContainerScreen
                && mc.player != null
                && screen.getTitle().getString()
                .startsWith(TITLE.toTextComponent(AotakeUtils.getPlayerLanguage(mc.player)).getString())
        ) {
            if (event instanceof ScreenEvent.Init.Post eve) {
                for (int i = 0; i < 20; i++) {
                    AotakeScheduler.schedule(i, () -> {
                        if (mousePos.key() > -1 && mousePos.val() > -1 && mouseRestoreTicks > 0) {
                            MouseHelper.setMouseRawPos(mousePos);
                            mouseRestoreTicks--;
                            if (mouseRestoreTicks <= 0) {
                                mousePos.setKey(-1D).setValue(-1D);
                            }
                        }
                    });
                }
                if (ClientConfig.VANILLA_DUSTBIN.get()) {
                    LocalPlayer player = mc.player;
                    AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
                    int baseX = accessor.aotake$getLeftPos();
                    int baseY = accessor.aotake$getTopPos();
                    int yOffset = 0;
                    boolean canPrev = true;
                    boolean canNext = true;
                    if (dustbinPage > 0 && dustbinTotalPage > 0) {
                        canPrev = dustbinPage > 1;
                        canNext = dustbinPage < dustbinTotalPage;
                    }
                    if (AotakeUtils.hasCommandPermission(player, EnumCommandType.CACHE_CLEAR)) {
                        eve.addListener(
                                AbstractGuiUtils.newButton(baseX - 21
                                        , baseY + 21 * (yOffset++)
                                        , 20, 20
                                        , Component.literal("✕").setColor(EnumMCColor.RED.getColor())
                                        , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, true))
                                        , Component.translatable(EnumI18nType.MESSAGE, "clear_cache")
                                )
                        );
                    }
                    if (AotakeUtils.hasCommandPermission(player, EnumCommandType.DUSTBIN_CLEAR)) {
                        eve.addListener(
                                AbstractGuiUtils.newButton(baseX - 21
                                        , baseY + 21 * (yOffset++)
                                        , 20, 20
                                        , Component.literal("✕").setColor(EnumMCColor.RED.getColor())
                                        , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(true, false))
                                        , Component.translatable(EnumI18nType.MESSAGE, "clear_all_dustbin")
                                )
                        );
                        eve.addListener(
                                AbstractGuiUtils.newButton(baseX - 21
                                        , baseY + 21 * (yOffset++)
                                        , 20, 20
                                        , Component.literal("✕").setColor(EnumMCColor.YELLOW.getColor())
                                        , button -> AotakeUtils.sendPacketToServer(new ClearDustbinToServer(false, false))
                                        , Component.translatable(EnumI18nType.MESSAGE, "clear_cur_dustbin")
                                )
                        );
                    }
                    eve.addListener(
                            AbstractGuiUtils.newButton(baseX - 21
                                    , baseY + 21 * (yOffset++)
                                    , 20, 20
                                    , Component.literal("↻")
                                    , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0))
                                    , Component.translatable(EnumI18nType.MESSAGE, "refresh_page")
                            )
                    );
                    Button prevButton = AbstractGuiUtils.newButton(baseX - 21
                            , baseY + 21 * (yOffset++)
                            , 20, 20
                            , Component.literal("▲")
                            , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1))
                            , Component.translatable(EnumI18nType.MESSAGE, "previous_page")
                    );
                    prevButton.active = canPrev;
                    dustbinPrevButton = prevButton;
                    eve.addListener(prevButton);
                    Button nextButton = AbstractGuiUtils.newButton(baseX - 21
                            , baseY + 21 * (yOffset++)
                            , 20, 20
                            , Component.literal("▼")
                            , button -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1))
                            , Component.translatable(EnumI18nType.MESSAGE, "next_page")
                    );
                    nextButton.active = canNext;
                    dustbinNextButton = nextButton;
                    eve.addListener(nextButton);
                }
            } else if (event instanceof ScreenEvent.Render.Post eve) {
                if (ClientConfig.VANILLA_DUSTBIN.get()) {
                    boolean canPrev = true;
                    boolean canNext = true;
                    if (dustbinPage > 0 && dustbinTotalPage > 0) {
                        canPrev = dustbinPage > 1;
                        canNext = dustbinPage < dustbinTotalPage;
                    }
                    if (dustbinPrevButton != null) {
                        dustbinPrevButton.active = canPrev;
                    }
                    if (dustbinNextButton != null) {
                        dustbinNextButton.active = canNext;
                    }
                }
                if (!ClientConfig.VANILLA_DUSTBIN.get()) {
                    LocalPlayer player = mc.player;
                    int mouseX = eve.getMouseX();
                    int mouseY = eve.getMouseY();
                    mouseHelper.tick(mouseX, mouseY);

                    GuiGraphics graphics = eve.getGuiGraphics();
                    int baseW = 16;
                    int baseH = 16;
                    AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
                    int baseX = DustbinGuiLayoutCache.valid
                            ? DustbinGuiLayoutCache.leftPos + DustbinGuiConfig.getButtonXOffset()
                            : accessor.aotake$getLeftPos();
                    int baseY = DustbinGuiLayoutCache.valid
                            ? DustbinGuiLayoutCache.topPos + DustbinGuiConfig.getButtonYOffset()
                            : accessor.aotake$getTopPos();
                    boolean canPrev = true;
                    boolean canNext = true;
                    if (dustbinPage > 0 && dustbinTotalPage > 0) {
                        canPrev = dustbinPage > 1;
                        canNext = dustbinPage < dustbinTotalPage;
                    }

                    int yOffset = 0;
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
                            mouseRestoreTicks = 20;
                            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0));
                        }
                    }
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = canPrev && mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isLeftPressing() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "up.png");
                        AbstractGuiUtils.blitBlend(graphics, texture, x, y, 0, 0, w, h, w, h);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, canPrev ? 1.0F : 0.5F);
                        // RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

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

                        if (canPrev && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                            mouseRestoreTicks = 20;
                            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                        }
                    }
                    {
                        int w = baseW;
                        int h = baseH;
                        int x = baseX - w - 1;
                        int y = baseY + (h + 1) * (yOffset++);
                        boolean hover = canNext && mouseHelper.isHoverInRect(x, y, w, h);

                        if (mouseHelper.isLeftPressing() && hover) {
                            x--;
                            y--;
                            w += 2;
                            h += 2;
                        }

                        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "down.png");
                        AbstractGuiUtils.blitBlend(graphics, texture, x, y, 0, 0, w, h, w, h);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, canNext ? 1.0F : 0.5F);
                        // RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

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

                        if (canNext && mouseHelper.isLeftPressedInRect(x, y, w, h)) {
                            KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                            mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                            mouseRestoreTicks = 20;
                            AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1));
                        }
                    }
                }
            } else if (event instanceof ScreenEvent.KeyPressed.Pre keyEvent) {
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
                        mouseRestoreTicks = 20;
                        AotakeUtils.sendPacketToServer(new OpenDustbinToServer(-1));
                    }
                } else if (keyEvent.getKeyCode() == ClientModEventHandler.DUSTBIN_NEXT_KEY.getKey().getValue()) {
                    if (System.currentTimeMillis() - lastTime > 200) {
                        lastTime = System.currentTimeMillis();
                        KeyValue<Double, Double> cursorPos = MouseHelper.getRawCursorPos();
                        mousePos.setKey(cursorPos.getKey()).setValue(cursorPos.getValue());
                        mouseRestoreTicks = 20;
                        AotakeUtils.sendPacketToServer(new OpenDustbinToServer(1));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.debug("Client: Player logged in.");
        // 通知服务器客户端已加载mod
        AotakeUtils.sendPacketToServer(new ModLoadedToBoth());
    }
}
