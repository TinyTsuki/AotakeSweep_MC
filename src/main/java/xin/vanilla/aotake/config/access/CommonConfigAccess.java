package xin.vanilla.aotake.config.access;

import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.banira.common.config.ConfigCategoryViewProxy;
import xin.vanilla.banira.common.config.ConfigHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * {@link CommonConfig} 运行时视图。
 */
public final class CommonConfigAccess {

    private static final CommonConfig.BaseCategory DEFAULT_BASE = new CommonConfig.BaseCategory();

    private CommonConfigAccess() {
    }

    public static CommonConfig.RootView root(ConfigHolder holder) {
        return (CommonConfig.RootView) Proxy.newProxyInstance(
                CommonConfig.class.getClassLoader(),
                new Class<?>[]{CommonConfig.RootView.class},
                (proxy, method, args) -> rootHandle(proxy, method, args, holder));
    }

    private static Object rootHandle(Object proxy, Method method, Object[] args, ConfigHolder holder) {
        if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxy, method, args, "CommonConfig.RootView");
        }
        switch (method.getName()) {
            case "base":
                return base(holder);
            case "command":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.CommandView.class, holder, "command",
                        new CommonConfig.CommandCategory(), CommonConfigAccess::readSimple);
            case "concise":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.ConciseView.class, holder, "concise",
                        new CommonConfig.ConciseCategory(), CommonConfigAccess::readSimple);
            case "permission":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.PermissionView.class, holder, "permission",
                        new CommonConfig.PermissionCategory(), CommonConfigAccess::readSimple);
            case "holder":
                return holder;
            default:
                throw new UnsupportedOperationException(method.toString());
        }
    }

    private static CommonConfig.BaseView base(ConfigHolder holder) {
        return (CommonConfig.BaseView) Proxy.newProxyInstance(
                CommonConfig.class.getClassLoader(),
                new Class<?>[]{CommonConfig.BaseView.class},
                (proxy, method, args) -> baseHandle(holder, proxy, method, args));
    }

    private static Object baseHandle(ConfigHolder holder, Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return objectMethod(proxy, method, args, "BaseView");
        }
        String n = method.getName();
        switch (n) {
            case "dustbin":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.DustbinView.class, holder, "base.dustbin",
                        DEFAULT_BASE.dustbin(), CommonConfigAccess::readSimple);
            case "sweep":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.SweepView.class, holder, "base.sweep",
                        DEFAULT_BASE.sweep(), CommonConfigAccess::readSimple);
            case "safe":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.SafeView.class, holder, "base.safe",
                        DEFAULT_BASE.safe(), CommonConfigAccess::readSimple);
            case "common":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.CommonSettingsView.class, holder, "base.common",
                        DEFAULT_BASE.common(), CommonConfigAccess::readSimple);
            case "chunk":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.ChunkView.class, holder, "base.chunk",
                        DEFAULT_BASE.chunk(), CommonConfigAccess::readSimple);
            case "entityCatch":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.EntityCatchView.class, holder, "base.entityCatch",
                        DEFAULT_BASE.entityCatch(), CommonConfigAccess::readSimple);
            case "batch":
                return ConfigCategoryViewProxy.create(
                        CommonConfig.BatchView.class, holder, "base.batch",
                        DEFAULT_BASE.batch(), CommonConfigAccess::readSimple);
            default:
                throw new UnsupportedOperationException(method.toString());
        }
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
