package xin.vanilla.aotake.config.access;

import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.banira.common.config.ConfigCategoryViewProxy;
import xin.vanilla.banira.common.config.ConfigHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * {@link ClientConfig} 运行时视图（与 {@link xin.vanilla.banira.common.config.ForgeConfigAdapter} 生成的路径一致）。
 */
public final class ClientConfigAccess {

    private static final ClientConfig.ProgressBarCategory DEFAULT_PROGRESS_BAR = new ClientConfig.ProgressBarCategory();
    private static final ClientConfig.DustbinCategory DEFAULT_DUSTBIN = new ClientConfig.DustbinCategory();

    private ClientConfigAccess() {
    }

    public static ClientConfig.RootView root(ConfigHolder holder) {
        return (ClientConfig.RootView) Proxy.newProxyInstance(
                ClientConfig.class.getClassLoader(),
                new Class<?>[]{ClientConfig.RootView.class},
                (proxy, method, args) -> rootHandle(proxy, method, args, holder));
    }

    private static Object rootHandle(Object proxy, Method method, Object[] args, ConfigHolder holder) {
        if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxy, method, args, "ClientConfig.RootView");
        }
        switch (method.getName()) {
            case "progressBar":
                return progressBar(holder);
            case "dustbin":
                return ConfigCategoryViewProxy.create(
                        ClientConfig.DustbinView.class, holder, "dustbin", DEFAULT_DUSTBIN, ClientConfigAccess::readSimple);
            case "holder":
                return holder;
            default:
                throw new UnsupportedOperationException(method.toString());
        }
    }

    private static ClientConfig.ProgressBarView progressBar(ConfigHolder holder) {
        return (ClientConfig.ProgressBarView) Proxy.newProxyInstance(
                ClientConfig.class.getClassLoader(),
                new Class<?>[]{ClientConfig.ProgressBarView.class},
                (proxy, method, args) -> progressBarHandle(holder, proxy, method, args));
    }

    private static Object progressBarHandle(ConfigHolder holder, Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxy, method, args, "ProgressBarView");
        }
        String n = method.getName();
        int pc = method.getParameterCount();
        switch (n) {
            case "leaf":
                return ConfigCategoryViewProxy.create(
                        ClientConfig.ProgressBarLeafView.class, holder, "progressBar.leaf",
                        DEFAULT_PROGRESS_BAR.leaf(), ClientConfigAccess::readSimple);
            case "pole":
                return ConfigCategoryViewProxy.create(
                        ClientConfig.ProgressBarPoleView.class, holder, "progressBar.pole",
                        DEFAULT_PROGRESS_BAR.pole(), ClientConfigAccess::readSimple);
            case "text":
                return ConfigCategoryViewProxy.create(
                        ClientConfig.ProgressBarTextView.class, holder, "progressBar.text",
                        DEFAULT_PROGRESS_BAR.text(), ClientConfigAccess::readSimple);
            default:
                break;
        }
        if (pc == 0) {
            Object raw = holder != null ? holder.get("progressBar." + n) : null;
            try {
                return readSimple(n, raw, DEFAULT_PROGRESS_BAR);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (pc == 1) {
            if (holder != null) {
                holder.set("progressBar." + n, args[0]);
            }
            return proxy;
        }
        throw new UnsupportedOperationException(method.toString());
    }

    private static Object readSimple(String leaf, Object raw, Object bean) throws Exception {
        if (raw == null) {
            return field(bean, leaf);
        }
        return raw;
    }

    private static Object field(Object bean, String name) throws Exception {
        Field f = bean.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(bean);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args, String tag) {
        String n = method.getName();
        switch (n) {
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return tag + "@" + System.identityHashCode(proxy);
        }
        throw new UnsupportedOperationException(method.toString());
    }
}
