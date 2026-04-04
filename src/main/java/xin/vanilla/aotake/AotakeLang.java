package xin.vanilla.aotake;

import lombok.NonNull;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.ScopedComponent;
import xin.vanilla.banira.common.enums.EnumI18nType;
import xin.vanilla.banira.common.util.Translator;

/**
 * 竹叶清语言入口
 */
public final class AotakeLang extends Translator {

    public static final AotakeLang INSTANCE = new AotakeLang();

    private AotakeLang() {
        super(AotakeSweep.class);
        registerInCache();
    }

    public static AotakeLang get() {
        return INSTANCE;
    }

    public static boolean hasTranslation(@NonNull EnumI18nType type, @NonNull String key) {
        return INSTANCE.hasTranslation(type, key, Translator.getClientLanguage());
    }

    public static String getClientLanguage() {
        return Translator.getClientLanguage();
    }

    public static String getServerLanguage() {
        return CommonConfig.get().base().common().defaultLanguage();
    }

    public static String getServerPlayerLanguage(ServerPlayerEntity player) {
        return Translator.getServerPlayerLanguage(player);
    }

    public static Component transLangAuto(String languageCode, String key, Object... args) {
        return transLangAuto(AotakeSweep.MODID, languageCode, key, args);
    }

    public static Component transLangAuto(String modId, String languageCode, String key, Object... args) {
        if (args == null || args.length == 0) {
            return new ScopedComponent(modId).transLang(modId, languageCode, EnumI18nType.WORD, key);
        }
        return new ScopedComponent(modId).transLang(modId, languageCode, EnumI18nType.FORMAT, key, args);
    }
}
