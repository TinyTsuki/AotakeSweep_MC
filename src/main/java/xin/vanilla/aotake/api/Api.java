package xin.vanilla.aotake.api;

import xin.vanilla.aotake.config.CustomConfig;

public class Api implements IApi {
    @Override
    public void reloadCustomConfig() {
        CustomConfig.loadCustomConfig(false);
    }
}
