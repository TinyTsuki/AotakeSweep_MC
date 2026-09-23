package xin.vanilla.aotake.internal.fabric.modmenu;

import xin.vanilla.banira.api.BaniraConfigs;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;

/**
 * 将 Mod Menu 的设置按钮连接到竹叶清客户端配置。
 */
public final class AotakeModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ConfigEditorScreen(
                BaniraConfigs.holder(ClientConfig.class),
                new ConfigEditorScreen.Args().parentScreen(parent));
    }
}
