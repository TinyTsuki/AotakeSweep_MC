package xin.vanilla.aotake.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.event.ClientEventHandler;
import xin.vanilla.banira.client.util.InputStateManager;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.util.StringUtils;

/**
 * 垃圾箱/区块暂存 GUI 标题识别、光标恢复与分页缓存（曾与 Forge {@code ScreenEvent} 耦合的侧栏已下线）。
 */
public final class DustbinRender {

    private static final KeyValue<Double, Double> pendingMouseRaw = new KeyValue<>(-1D, -1D);

    private static int dustbinPage = -1;
    private static int dustbinTotalPage = -1;
    private static int chunkVaultPage = -1;
    private static int chunkVaultTotalPage = -1;

    private DustbinRender() {
    }

    public static boolean isDustbinTitle(String title) {
        return StringUtils.isNotNullOrEmpty(title)
                && AotakeLang.get().getI18nFiles().stream().anyMatch(lang ->
                title.startsWith(
                        AotakeComponent.get().transAuto("title").getString(lang)
                ));
    }

    public static void updateDustbinPage(int page, int totalPage) {
        dustbinPage = page;
        dustbinTotalPage = totalPage;
        ClientEventHandler.updateDustbinPage(page, totalPage);
    }

    public static void updateChunkVaultPage(int page, int totalPage) {
        chunkVaultPage = page;
        chunkVaultTotalPage = totalPage;
    }

    public static boolean isChunkVaultTitle(String title) {
        return StringUtils.isNotNullOrEmpty(title)
                && AotakeLang.get().getI18nFiles().stream().anyMatch(lang ->
                title.startsWith(
                        AotakeComponent.get().transAuto("chunk_vault_title").getString(lang)
                ));
    }

    public static void abandonPendingCursorRestore() {
        if (pendingMouseRaw.key() >= 0) {
            pendingMouseRaw.key(-1D).val(-1D);
        }
    }

    public static void tryConsumePendingCursorRaw() {
        if (pendingMouseRaw.key() < 0) {
            return;
        }
        double rx = pendingMouseRaw.key();
        double ry = pendingMouseRaw.val();
        pendingMouseRaw.key(-1D).val(-1D);
        InputStateManager.setMouseRawPos(rx, ry);
    }

    public static boolean isOurDustbinChestScreen(Screen screen, Minecraft mc) {
        return screen instanceof ContainerScreen
                && mc.player != null
                && isDustbinTitle(screen.getTitle().getString());
    }

    public static boolean isOurSpecialChestScreen(Screen screen, Minecraft mc) {
        return screen instanceof ContainerScreen
                && mc.player != null
                && (isDustbinTitle(screen.getTitle().getString())
                || isChunkVaultTitle(screen.getTitle().getString()));
    }
}
