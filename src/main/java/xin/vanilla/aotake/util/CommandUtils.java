package xin.vanilla.aotake.util;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.enums.EnumMCColor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class CommandUtils {

    private static final Logger LOGGER = LogManager.getLogger();


    public static boolean checkModStatus(CommandContext<CommandSourceStack> context) {
        if (AotakeSweep.isDisable()) {
            CommandSourceStack source = context.getSource();
            Entity entity = source.getEntity();
            if (entity instanceof ServerPlayer) {
                AotakeUtils.sendMessage((ServerPlayer) entity, Component.translatable(EnumI18nType.MESSAGE, "mod_disabled"));
            }
        }
        return AotakeSweep.isDisable();
    }

    public static String getLanguage(CommandSourceStack source) {
        String lang = ServerConfig.DEFAULT_LANGUAGE.get();
        if (source.getEntity() != null && source.getEntity() instanceof ServerPlayer) {
            try {
                lang = AotakeUtils.getPlayerLanguage(source.getPlayerOrException());
            } catch (Exception ignored) {
            }
        }
        return lang;
    }


    // region 指令参数相关

    public static void addSuggestion(SuggestionsBuilder suggestion, String input, String suggest) {
        if (suggest.contains(input) || StringUtils.isNullOrEmpty(input)) {
            suggestion.suggest(suggest);
        }
    }

    public static String getStringEmpty(CommandContext<?> context, String name) {
        return getStringDefault(context, name, "");
    }

    public static String getStringDefault(CommandContext<?> context, String name, String defaultValue) {
        String result;
        try {
            result = StringArgumentType.getString(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static String getStringEx(CommandContext<?> context, String name, String defaultValue) {
        String result;
        try {
            result = String.valueOf(context.getArgument(name, Object.class));
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static String replaceResourcePath(String s) {
        if (StringUtils.isNullOrEmpty(s)) return "";
        return s.substring(s.indexOf(":") + 1);
    }

    public static int getIntDefault(CommandContext<?> context, String name, int defaultValue) {
        int result;
        try {
            result = IntegerArgumentType.getInteger(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static long getLongDefault(CommandContext<?> context, String name, long defaultValue) {
        long result;
        try {
            result = LongArgumentType.getLong(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static boolean getBooleanDefault(CommandContext<?> context, String name, boolean defaultValue) {
        boolean result;
        try {
            result = BoolArgumentType.getBool(context, name);
        } catch (IllegalArgumentException ignored) {
            result = defaultValue;
        }
        return result;
    }

    public static ServerLevel getDimensionDefault(CommandContext<CommandSourceStack> context, String name, ServerLevel defaultDimension) {
        ServerLevel result;
        try {
            result = DimensionArgument.getDimension(context, name);
        } catch (IllegalArgumentException | CommandSyntaxException e) {
            result = defaultDimension;
        }
        return result;
    }

    /**
     * 若为第一次使用指令则进行提示
     */
    public static void notifyHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            PlayerSweepData data = PlayerSweepData.getData(player);
            if (!data.isNotified()) {
                Component button = Component.literal("/" + AotakeUtils.getCommandPrefix())
                        .setColor(EnumMCColor.AQUA.getColor())
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + AotakeUtils.getCommandPrefix()))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/" + AotakeUtils.getCommandPrefix())
                                .toTextComponent())
                        );
                AotakeUtils.sendMessage(player, Component.translatable(EnumI18nType.MESSAGE, "notify_help", button));
                data.setNotified(true);
            }
        }
    }

    // endregion 指令参数相关


    // region config modifier

    public static void configKeySuggestion(Class<?> configClazz, SuggestionsBuilder builder, String configKey) {
        if (configKey == null) configKey = "";
        configKey = configKey.trim();
        boolean isEmpty = configKey.isEmpty();

        Map<String, ForgeConfigSpec.ConfigValue<?>> map = buildConfigKeyMap(configClazz);
        if (CollectionUtils.isNullOrEmpty(map)) return;

        String lowerInput = configKey.toLowerCase(Locale.ROOT);

        if (isEmpty) {
            for (String key : map.keySet()) {
                builder.suggest(key);
            }
            return;
        }

        if (configKey.indexOf('.') >= 0) {
            String[] inputParts = lowerInput.split("\\.");
            int prefixSegments = inputParts.length - 1;
            String lastInputPart = inputParts[inputParts.length - 1];

            for (String key : map.keySet()) {
                String lowerKey = key.toLowerCase(Locale.ROOT);
                String[] keyParts = lowerKey.split("\\.");

                if (keyParts.length < prefixSegments + 1) {
                    continue;
                }

                boolean prefixMatches = true;
                for (int i = 0; i < prefixSegments; i++) {
                    if (!keyParts[i].equals(inputParts[i])) {
                        prefixMatches = false;
                        break;
                    }
                }
                if (!prefixMatches) continue;

                String lastKeyPart = keyParts[keyParts.length - 1];
                if (lastKeyPart.contains(lastInputPart)) {
                    builder.suggest(key);
                }
            }
        } else {
            for (String key : map.keySet()) {
                if (key.toLowerCase(Locale.ROOT).contains(lowerInput)) {
                    builder.suggest(key);
                }
            }
        }

    }

    @SuppressWarnings("rawtypes")
    public static void configValueSuggestion(Class<?> configClazz, SuggestionsBuilder builder, String configKey) {
        ForgeConfigSpec.ConfigValue<?> cv = findConfigValueByKey(configClazz, configKey);
        if (cv == null) return;
        else builder.suggest(String.valueOf(cv.get()));

        ForgeConfigSpec.ValueSpec vs = getValueSpec(cv);
        if (vs != null) {
            builder.suggest(String.valueOf(vs.getDefault()));
        }

        Class<?> type = getConfigValueType(cv);
        if (type == Boolean.class || type == boolean.class) {
            builder.suggest("true").suggest("false");
        } else if (Enum.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum> enumClass = (Class<? extends Enum>) type;
            for (Object c : enumClass.getEnumConstants()) {
                builder.suggest(((Enum<?>) c).name());
            }
        } else if (Comparable.class.isAssignableFrom(type) && vs != null && vs.getRange() != null) {
            Class<?> rangeClass = FieldUtils.getClass(vs.getRange());
            for (String fieldName : FieldUtils.getPrivateFieldNames(rangeClass, Comparable.class, false, false, true)) {
                builder.suggest(String.valueOf(FieldUtils.getPrivateFieldValue(rangeClass, vs.getRange(), fieldName)));
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int executeModifyConfig(Class<?> configClazz, CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String lang = getLanguage(source);
        String configKey = StringArgumentType.getString(context, "configKey");
        String configValue = StringArgumentType.getString(context, "configValue");

        ForgeConfigSpec.ConfigValue<?> cv = findConfigValueByKey(configClazz, configKey);
        if (cv == null) {
            Component component = Component.translatable(EnumI18nType.MESSAGE, "config_key_absent", configKey);
            source.sendFailure(component.toChatComponent(lang));
            return 0;
        }

        Class<?> type = getConfigValueType(cv);
        Object parsed;
        try {
            parsed = parseStringToType(configValue, type);
        } catch (Exception e) {
            LOGGER.error(e);
            Component component = Component.translatable(EnumI18nType.MESSAGE, "config_value_parse_error", configValue, e.getMessage());
            source.sendFailure(component.toChatComponent(lang));
            return 0;
        }

        if (validateConfigValueWithSpec(cv, parsed)) {
            ((ForgeConfigSpec.ConfigValue) cv).set(parsed);
        } else {
            Component component = Component.translatable(EnumI18nType.MESSAGE, "config_value_set_error", configKey, configValue);
            source.sendFailure(component.toChatComponent(lang));
            return 0;
        }

        tryApplyServerConfigBake(configClazz);

        Component component = Component.translatable(EnumI18nType.MESSAGE, "config_value_set_success", configKey, parsed);
        source.sendSuccess(component.toChatComponent(lang), true);

        return 1;
    }


    private static final Map<Class<?>, List<Field>> allConfigValueFieldsCache = new HashMap<>();

    private static List<Field> getAllConfigValueFields(Class<?> configClazz) {
        return allConfigValueFieldsCache.computeIfAbsent(configClazz, (k) -> {
            List<Field> out = new ArrayList<>();
            for (Field f : k.getDeclaredFields()) {
                try {
                    if (ForgeConfigSpec.ConfigValue.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        out.add(f);
                    }
                } catch (Throwable ignored) {
                }
            }
            return out;
        });
    }

    public static final Map<Class<?>, Map<String, ForgeConfigSpec.ConfigValue<?>>> configKeyMapCache = new HashMap<>();

    private static Map<String, ForgeConfigSpec.ConfigValue<?>> buildConfigKeyMap(Class<?> configClazz) {
        return configKeyMapCache.computeIfAbsent(configClazz, (k) -> {
            Map<String, ForgeConfigSpec.ConfigValue<?>> map = new LinkedHashMap<>();
            for (Field f : getAllConfigValueFields(k)) {
                try {
                    Object raw = f.get(null);
                    if (raw instanceof ForgeConfigSpec.ConfigValue<?> cv) {
                        String path = getConfigValuePath(cv);
                        if (path != null) {
                            map.put(path, cv);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            return map;
        });
    }

    private static String getConfigValuePath(ForgeConfigSpec.ConfigValue<?> cv) {
        return cv.getPath().stream().map(String::valueOf).collect(Collectors.joining("."));
    }

    private static ForgeConfigSpec.ConfigValue<?> findConfigValueByKey(Class<?> configClazz, String key) {
        if (key == null) return null;
        Map<String, ForgeConfigSpec.ConfigValue<?>> map = buildConfigKeyMap(configClazz);

        if (map.containsKey(key)) return map.get(key);

        List<String> matches = map.keySet().stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))
                .toList();
        if (matches.size() == 1) return map.get(matches.get(0));

        return null;
    }

    private static Class<?> getConfigValueType(ForgeConfigSpec.ConfigValue<?> cv) {
        try {
            Object cur = cv.get();
            if (cur != null) return cur.getClass();
        } catch (Throwable ignored) {
        }
        return Object.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseStringToType(String parsedStr, Class<?> targetType) throws IllegalArgumentException {
        if (targetType == Boolean.class || targetType == boolean.class) {
            return StringUtils.stringToBoolean(parsedStr);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return StringUtils.toInt(parsedStr);
        }
        if (targetType == Long.class || targetType == long.class) {
            return StringUtils.toLong(parsedStr);
        }
        if (targetType == Double.class || targetType == double.class) {
            return StringUtils.toDouble(parsedStr);
        }
        if (Enum.class.isAssignableFrom(targetType)) {
            Class<? extends Enum> enumClass = (Class<? extends Enum>) targetType;
            for (Object c : enumClass.getEnumConstants()) {
                if (c.toString().equalsIgnoreCase(parsedStr) || ((Enum<?>) c).name().equalsIgnoreCase(parsedStr)) {
                    return Enum.valueOf(enumClass, ((Enum<?>) c).name());
                }
            }
            throw new IllegalArgumentException("Unknown enum constant: " + parsedStr);
        }
        if (targetType == String.class) {
            return parsedStr;
        }
        if (List.class.isAssignableFrom(targetType)) {
            String[] parts = parsedStr.split(",");
            return Arrays.stream(parts).map(String::trim).collect(Collectors.toList());
        }
        return parsedStr;
    }

    @SuppressWarnings("all")
    private static void tryApplyServerConfigBake(Class<?> configClazz) {
        try {
            Method m = configClazz.getDeclaredMethod("bake");
            if (m != null) {
                m.setAccessible(true);
                m.invoke(null);
                return;
            }
        } catch (Throwable ignored) {
        }

        String[] candidate = new String[]{"save", "sync", "write", "apply"};
        for (String name : candidate) {
            try {
                Method m = configClazz.getDeclaredMethod(name);
                if (m != null) {
                    m.setAccessible(true);
                    m.invoke(null);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static final Map<String, ForgeConfigSpec.ValueSpec> valueSpecCache = new HashMap<>();

    public static ForgeConfigSpec.ValueSpec getValueSpec(ForgeConfigSpec.ConfigValue<?> cv) {
        return valueSpecCache.computeIfAbsent(getConfigValuePath(cv), (k) -> {
            try {
                ForgeConfigSpec spec = null;
                for (String candidate : FieldUtils.getPrivateFieldNames(ForgeConfigSpec.ConfigValue.class, ForgeConfigSpec.class)) {
                    Object value = FieldUtils.getPrivateFieldValue(ForgeConfigSpec.ConfigValue.class, cv, candidate);
                    if (value != null) {
                        spec = (ForgeConfigSpec) value;
                        break;
                    }
                }
                if (spec != null) {
                    return spec.getSpec().get(cv.getPath());
                }
            } catch (Throwable ignored) {
            }
            return null;
        });
    }

    public static boolean validateConfigValueWithSpec(ForgeConfigSpec.ConfigValue<?> cv, Object parsedValue) {
        if (cv == null) return false;
        ForgeConfigSpec.ValueSpec vs = getValueSpec(cv);
        return vs != null && vs.test(parsedValue);
    }

    // endregion config modifier

}
