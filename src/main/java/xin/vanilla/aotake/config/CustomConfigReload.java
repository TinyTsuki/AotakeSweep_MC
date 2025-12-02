package xin.vanilla.aotake.config;

import xin.vanilla.aotake.AotakeSweep;

public class CustomConfigReload implements Runnable {
    @Override
    public void run() {
        AotakeSweep.reloadCustomConfig();
    }
}
