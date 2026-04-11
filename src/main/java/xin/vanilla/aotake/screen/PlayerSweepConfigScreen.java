package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.PlayerConfigSyncToServer;
import xin.vanilla.banira.common.util.PacketUtils;

import javax.annotation.Nullable;

/**
 * 编辑 {@link xin.vanilla.aotake.data.player.PlayerSweepData} 中的客户端相关偏好
 */
public class PlayerSweepConfigScreen extends Screen {

    @Nullable
    private final Screen parent;
    private boolean showSweepResult;
    private boolean enableWarningVoice;

    public PlayerSweepConfigScreen(@Nullable Screen parent, boolean showSweepResult, boolean enableWarningVoice) {
        super(AotakeComponent.get().transClientAuto("player_prefs_screen_title").toVanilla());
        this.parent = parent;
        this.showSweepResult = showSweepResult;
        this.enableWarningVoice = enableWarningVoice;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 42;
        this.addButton(new Button(cx - 155, y, 310, 20, rowShow(), b -> {
            this.showSweepResult = !this.showSweepResult;
            b.setMessage(rowShow());
        }));
        y += 24;
        this.addButton(new Button(cx - 155, y, 310, 20, rowVoice(), b -> {
            this.enableWarningVoice = !this.enableWarningVoice;
            b.setMessage(rowVoice());
        }));
        y += 28;
        this.addButton(new Button(cx - 155, y, 150, 20, AotakeComponent.get().transClientAuto("save").toVanilla(), b -> {
            PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new PlayerConfigSyncToServer(this.showSweepResult, this.enableWarningVoice));
            this.minecraft.setScreen(this.parent);
        }));
        this.addButton(new Button(cx + 5, y, 150, 20, AotakeComponent.get().transClientAuto("cancel").toVanilla(), b -> this.minecraft.setScreen(this.parent)));
    }

    private ITextComponent rowShow() {
        String state = AotakeComponent.get().transClientAuto(this.showSweepResult ? "enabled" : "disabled").toVanilla().getString();
        return AotakeComponent.get().transClientAuto("player_prefs_show_row", state).toVanilla();
    }

    private ITextComponent rowVoice() {
        String state = AotakeComponent.get().transClientAuto(this.enableWarningVoice ? "enabled" : "disabled").toVanilla().getString();
        return AotakeComponent.get().transClientAuto("player_prefs_voice_row", state).toVanilla();
    }

    @Override
    public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        this.drawCenteredString(poseStack, this.font, this.title, this.width / 2, this.height / 2 - 72, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
