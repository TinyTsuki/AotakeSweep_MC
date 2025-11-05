package xin.vanilla.aotake.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.gui.ForgeIngameGui;
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
import xin.vanilla.aotake.data.Color;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.aotake.enums.EnumRotationCenter;
import xin.vanilla.aotake.network.packet.ClearDustbinToServer;
import xin.vanilla.aotake.network.packet.ClientLoadedToServer;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.component.Text;
import xin.vanilla.aotake.util.*;

import java.util.Date;
import java.util.List;

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

    private static long lastTime = 0;

    /**
     * 重新打开垃圾箱页面前鼠标位置
     */
    private static final KeyValue<Double, Double> mousePos = new KeyValue<>(0D, 0D);
    /**
     * 是否显示进度条
     */
    private static boolean showProgress = false;

    /**
     * 客户端Tick事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (Minecraft.getInstance().screen == null) {
                if (ClientModEventHandler.DUSTBIN_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                    lastTime = System.currentTimeMillis();
                    AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0));
                }
                if (ClientConfig.PROGRESS_BAR_KEY_APPLY_MODE.get()) {
                    if (ClientModEventHandler.PROGRESS_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                        lastTime = System.currentTimeMillis();
                        showProgress = !showProgress;
                    }
                } else {
                    showProgress = ClientModEventHandler.PROGRESS_KEY.isDown();
                }
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
     * 世界Tick事件
     */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
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
    public static void onRenderScreen(GuiScreenEvent event) {
        Screen screen = event.getGui();
        Minecraft mc = Minecraft.getInstance();
        if (screen instanceof ContainerScreen
                && mc.player != null
                && screen.getTitle().getContents()
                .startsWith(MOD_NAME.toTextComponent(AotakeUtils.getPlayerLanguage(mc.player)).getContents())
        ) {
            if (event instanceof GuiScreenEvent.InitGuiEvent.Post) {
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
            } else if (event instanceof GuiScreenEvent.DrawScreenEvent.Post) {
                GuiScreenEvent.DrawScreenEvent.Post eve = (GuiScreenEvent.DrawScreenEvent.Post) event;
                LocalPlayer player = mc.player;
                int mouseX = eve.getMouseX();
                int mouseY = eve.getMouseY();
                mouseHelper.tick(mouseX, mouseY);

                PoseStack ms = eve.getMatrixStack();
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

                    AbstractGuiUtils.bindTexture(TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_cache.png"));
                    AbstractGuiUtils.blitBlend(ms, x, y, 0, 0, w, h, w, h);

                    if (hover) {
                        AbstractGuiUtils.drawPopupMessage(Text.empty()
                                        .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_cache"))
                                        .setPoseStack(ms)
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

                        AbstractGuiUtils.bindTexture(TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_all.png"));
                        AbstractGuiUtils.blitBlend(ms, x, y, 0, 0, w, h, w, h);

                        if (hover) {
                            AbstractGuiUtils.drawPopupMessage(Text.empty()
                                            .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_all_dustbin"))
                                            .setPoseStack(ms)
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

                        AbstractGuiUtils.bindTexture(TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "clear_page.png"));
                        AbstractGuiUtils.blitBlend(ms, x, y, 0, 0, w, h, w, h);

                        if (hover) {
                            AbstractGuiUtils.drawPopupMessage(Text.empty()
                                            .setText(Component.translatable(EnumI18nType.MESSAGE, "clear_cur_dustbin"))
                                            .setPoseStack(ms)
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

                    AbstractGuiUtils.bindTexture(TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "refresh.png"));
                    AbstractGuiUtils.blitBlend(ms, x, y, 0, 0, w, h, w, h);

                    if (hover) {
                        AbstractGuiUtils.drawPopupMessage(Text.empty()
                                        .setText(Component.translatable(EnumI18nType.MESSAGE, "refresh_page"))
                                        .setPoseStack(ms)
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

                    AbstractGuiUtils.bindTexture(TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "up.png"));
                    AbstractGuiUtils.blitBlend(ms, x, y, 0, 0, w, h, w, h);

                    if (hover) {
                        AbstractGuiUtils.drawPopupMessage(Text.empty()
                                        .setText(Component.translatable(EnumI18nType.MESSAGE, "previous_page"))
                                        .setPoseStack(ms)
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

                    AbstractGuiUtils.bindTexture(TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "down.png"));
                    AbstractGuiUtils.blitBlend(ms, x, y, 0, 0, w, h, w, h);

                    if (hover) {
                        AbstractGuiUtils.drawPopupMessage(Text.empty()
                                        .setText(Component.translatable(EnumI18nType.MESSAGE, "next_page"))
                                        .setPoseStack(ms)
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
            } else if (event instanceof GuiScreenEvent.KeyboardKeyPressedEvent.Pre keyEvent) {
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
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGameOverlayEvent.PreLayer event) {
        if (event.getOverlay() == ForgeIngameGui.EXPERIENCE_BAR_ELEMENT) {
            renderProgress(event);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGameOverlayEvent.PostLayer event) {
        if (event.getOverlay() == ForgeIngameGui.EXPERIENCE_BAR_ELEMENT) {
            renderProgress(event);
        }
    }

    private static void renderProgress(RenderGameOverlayEvent event) {
        // 避免重复渲染
        if (event instanceof RenderGameOverlayEvent.PostLayer
                && ((ClientConfig.HIDE_EXPERIENCE_BAR_POLE.get() && ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get().contains(EnumProgressBarType.POLE.name()))
                || (ClientConfig.HIDE_EXPERIENCE_BAR_TEXT.get() && ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get().contains(EnumProgressBarType.TEXT.name()))
                || (ClientConfig.HIDE_EXPERIENCE_BAR_LEAF.get() && ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get().contains(EnumProgressBarType.LEAF.name())))
        ) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (mc.player == null) return;
        PoseStack ms = event.getMatrixStack();
        boolean hold = showProgress && mc.screen == null;
        List<? extends String> displayList = hold ? ClientConfig.PROGRESS_BAR_DISPLAY_HOLD.get() : ClientConfig.PROGRESS_BAR_DISPLAY_NORMAL.get();

        double scale = ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0;

        if (displayList.contains(EnumProgressBarType.POLE.name())) {
            if (ClientConfig.HIDE_EXPERIENCE_BAR_POLE.get()) {
                event.setCanceled(true);
            }
            int width = ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();
            int height = ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get();
            int drawX = getPoleX();
            int drawY = getPoleY();

            AbstractGuiUtils.TransformArgs transformArgs = new AbstractGuiUtils.TransformArgs(ms);
            transformArgs.setAngle(ClientConfig.PROGRESS_BAR_POLE_ANGLE.get())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(drawX)
                    .setY(drawY)
                    .setWidth(width)
                    .setHeight(height);
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) -> {
                AbstractGuiUtils.bindTexture(TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "pole.png"));
                AbstractGuiUtils.blitBlend(ms, (int) arg.getX(), (int) arg.getY(), 0, 0, (int) arg.getWidth(), (int) arg.getHeight(), (int) arg.getWidth(), (int) arg.getHeight());
            });
        }

        if (displayList.contains(EnumProgressBarType.TEXT.name())) {
            if (ClientConfig.HIDE_EXPERIENCE_BAR_TEXT.get()) {
                event.setCanceled(true);
            }
            Text time = Text.literal(getText())
                    .setPoseStack(ms)
                    .setColor(getTextColor())
                    .setShadow(true)
                    .setFont(Minecraft.getInstance().font);
            AbstractGuiUtils.TransformArgs textTransformArgs = new AbstractGuiUtils.TransformArgs(ms);
            textTransformArgs.setScale(scale)
                    .setAngle(ClientConfig.PROGRESS_BAR_TEXT_ANGLE.get())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(getTextX())
                    .setY(getTextY())
                    .setWidth(getTextWidth())
                    .setHeight(getTextHeight());
            AbstractGuiUtils.renderByTransform(textTransformArgs, (arg) -> AbstractGuiUtils.drawString(time
                    , arg.getX()
                    , arg.getY()
            ));
        }

        if (displayList.contains(EnumProgressBarType.LEAF.name())) {
            if (ClientConfig.HIDE_EXPERIENCE_BAR_LEAF.get()) {
                event.setCanceled(true);
            }
            int poleW = ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();

            int width = ClientConfig.PROGRESS_BAR_LEAF_WIDTH.get();
            int height = ClientConfig.PROGRESS_BAR_LEAF_HEIGHT.get();
            int rangeWidth = poleW - width;
            int startX = getLeafX();

            int drawX = (int) (startX + rangeWidth * getProgress());
            int drawY = getLeafY();

            AbstractGuiUtils.TransformArgs transformArgs = new AbstractGuiUtils.TransformArgs(ms);
            transformArgs.setAngle(ClientConfig.PROGRESS_BAR_LEAF_ANGLE.get())
                    .setCenter(EnumRotationCenter.CENTER)
                    .setX(drawX)
                    .setY(drawY)
                    .setWidth(width)
                    .setHeight(height);
            AbstractGuiUtils.renderByTransform(transformArgs, (arg) -> {
                ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "leaf.png");
                AbstractGuiUtils.bindTexture(texture);
                AbstractGuiUtils.blitBlend(ms, (int) arg.getX(), (int) arg.getY(), 0, 0, (int) arg.getWidth(), (int) arg.getHeight(), (int) arg.getWidth(), (int) arg.getHeight());
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        LOGGER.debug("Client: Player logged in.");
        // 通知服务器客户端已加载mod
        AotakeUtils.sendPacketToServer(new ClientLoadedToServer());
    }

    private static int getLeafX() {
        int baseX = getPoleX();
        int width = ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();
        double x;
        String xString = ClientConfig.PROGRESS_BAR_LEAF_POSITION.get().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_LEAF_SCREEN_QUADRANT.get();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_LEAF_BASE.get())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.PROGRESS_BAR_LEAF_WIDTH.get() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.PROGRESS_BAR_LEAF_WIDTH.get();
            }
            break;
        }
        return (int) x;
    }

    private static int getLeafY() {
        int baseY = getPoleY();
        int height = ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get();
        double y;
        String yString = ClientConfig.PROGRESS_BAR_LEAF_POSITION.get().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_LEAF_SCREEN_QUADRANT.get();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_LEAF_BASE.get())) {
            case CENTER: {
                y -= ClientConfig.PROGRESS_BAR_LEAF_HEIGHT.get() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.PROGRESS_BAR_LEAF_HEIGHT.get();
            }
            break;
        }
        return (int) y;
    }

    private static int getPoleX() {
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        double x;
        String xString = ClientConfig.PROGRESS_BAR_POLE_POSITION.get().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_POLE_SCREEN_QUADRANT.get();
        if (quadrant == 2 || quadrant == 3) {
            x = width - x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_POLE_BASE.get())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.PROGRESS_BAR_POLE_WIDTH.get() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();
            }
            break;
        }
        return (int) x;
    }

    private static int getPoleY() {
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        double y;
        String yString = ClientConfig.PROGRESS_BAR_POLE_POSITION.get().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_POLE_SCREEN_QUADRANT.get();
        if (quadrant == 1 || quadrant == 2) {
            y = height - y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_POLE_BASE.get())) {
            case CENTER: {
                y -= ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get();
            }
            break;
        }
        return (int) y;
    }

    private static int getTextX() {
        int baseX = getPoleX();
        int width = ClientConfig.PROGRESS_BAR_POLE_WIDTH.get();
        double x;
        String xString = ClientConfig.PROGRESS_BAR_TEXT_POSITION.get().split(",")[0];
        if (xString.endsWith("%")) {
            x = StringUtils.toDouble(xString.replace("%", "")) * 0.01d * width;
        } else {
            x = StringUtils.toInt(xString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_TEXT_SCREEN_QUADRANT.get();
        if (quadrant == 2 || quadrant == 3) {
            x = baseX - x;
        } else {
            x = baseX + x;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_TEXT_BASE.get())) {
            case CENTER:
            case TOP_CENTER:
            case BOTTOM_CENTER: {
                x -= ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0 * getTextWidth() / 2.0;
            }
            break;
            case TOP_RIGHT:
            case BOTTOM_RIGHT: {
                x -= ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0 * getTextWidth();
            }
            break;
        }
        return (int) x;
    }

    private static int getTextY() {
        int baseY = getPoleY();
        int height = ClientConfig.PROGRESS_BAR_POLE_HEIGHT.get();
        double y;
        String yString = ClientConfig.PROGRESS_BAR_TEXT_POSITION.get().split(",")[1];
        if (yString.endsWith("%")) {
            y = StringUtils.toDouble(yString.replace("%", "")) * 0.01d * height;
        } else {
            y = StringUtils.toInt(yString);
        }
        int quadrant = ClientConfig.PROGRESS_BAR_TEXT_SCREEN_QUADRANT.get();
        if (quadrant == 1 || quadrant == 2) {
            y = baseY - y;
        } else {
            y = baseY + y;
        }
        switch (EnumRotationCenter.valueOf(ClientConfig.PROGRESS_BAR_TEXT_BASE.get())) {
            case CENTER: {
                y -= ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0 * getTextHeight() / 2.0;
            }
            break;
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT: {
                y -= ClientConfig.PROGRESS_BAR_TEXT_SIZE.get() / 16.0 * getTextHeight();
            }
            break;
        }
        return (int) y;
    }

    private static String getText() {
        Date now = new Date();
        Date nextSweepTime = getNextSweepTime(now);
        if (nextSweepTime.after(now)) {
            long seconds = DateUtils.secondsOfTwo(now, nextSweepTime);
            if (seconds / 60 >= 100) {
                long hours = seconds / 3600;
                long minutes = (seconds % 3600) / 60;
                long secs = seconds % 60;
                return String.format("%02d:%02d:%02d", hours, minutes, secs);
            } else {
                long minutes = seconds / 60;
                long secs = seconds % 60;
                return String.format("%02d:%02d", minutes, secs);
            }
        } else {
            return "∞";
        }
    }

    private static double getProgress() {
        Date now = new Date();
        Date nextSweepTime = getNextSweepTime(now);
        if (nextSweepTime.after(now)) {
            Date lastSweepTime = DateUtils.addMilliSecond(nextSweepTime, -AotakeSweep.getSweepTime().key().intValue());
            return (double) (now.getTime() - lastSweepTime.getTime()) / (double) (nextSweepTime.getTime() - lastSweepTime.getTime());
        } else {
            return 0;
        }
    }

    private static Date getNextSweepTime(Date now) {
        int difference = (int) ((AotakeSweep.getClientServerTime().key() - AotakeSweep.getClientServerTime().val()) / 1000L);
        Date nextSweepTime = DateUtils.addSecond(new Date(AotakeSweep.getSweepTime().val()), difference);
        if (nextSweepTime.before(now)) {
            for (int i = 0; i < 5; i++) {
                if (nextSweepTime.before(now)) {
                    nextSweepTime = DateUtils.addMilliSecond(nextSweepTime, AotakeSweep.getSweepTime().key().intValue());
                } else {
                    break;
                }
            }
        }
        return nextSweepTime;
    }

    private static int getTextWidth() {
        return AbstractGuiUtils.getStringWidth(Minecraft.getInstance().font, getText());
    }

    private static int getTextHeight() {
        return AbstractGuiUtils.getStringHeight(Minecraft.getInstance().font, getText());
    }

    private static Color getTextColor() {
        return Color.parse(ClientConfig.PROGRESS_BAR_TEXT_COLOR.get(), Color.white());
    }

}
