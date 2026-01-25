package xin.vanilla.aotake.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.util.AbstractGuiUtils;
import xin.vanilla.aotake.util.Component;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new Screen(Component.translatable("config.aotake_sweep.config.title").toTextComponent()) {
            @Override
            protected void init() {
                int buttonWidth = 200;
                int buttonHeight = 20;
                int padding = 5;
                int centerX = this.width / 2 - buttonWidth / 2;
                int centerY = this.height / 2 - buttonHeight;

                // Server Config Button
                this.addRenderableWidget(AbstractGuiUtils.newButton(
                        centerX,
                        centerY,
                        buttonWidth,
                        buttonHeight,
                        Component.translatable("config.aotake_sweep.config.server"),
                        button -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(AutoConfig.getConfigScreen(ServerConfig.class, this).get());
                            }
                        }
                ));

                // Client Config Button
                this.addRenderableWidget(AbstractGuiUtils.newButton(
                        centerX,
                        centerY + buttonHeight + padding,
                        buttonWidth,
                        buttonHeight,
                        Component.translatable("config.aotake_sweep.config.client"),
                        button -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(AutoConfig.getConfigScreen(ClientConfig.class, this).get());
                            }
                        }
                ));

                // Back Button
                this.addRenderableWidget(AbstractGuiUtils.newButton(
                        centerX,
                        centerY + (buttonHeight + padding) * 2 + 10,
                        buttonWidth,
                        buttonHeight,
                        Component.translatable("config.aotake_sweep.config.back"),
                        button -> this.onClose()
                ));
            }

            @Override
            public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                this.renderBackground(graphics);
                graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
                super.render(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public void onClose() {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(parent);
                }
            }
        };
    }
}
