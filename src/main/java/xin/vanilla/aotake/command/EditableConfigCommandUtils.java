package xin.vanilla.aotake.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import xin.vanilla.banira.editable.ConfigEntryDescriptor;
import xin.vanilla.banira.editable.EditableConfigHolder;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * /{prefix} config common ... 用到的可编辑配置补全与写入（Banira CommandUtils 中无对等 API 时在工程内实现）。
 */
public final class EditableConfigCommandUtils {

    private EditableConfigCommandUtils() {
    }

    public static CompletableFuture<Suggestions> configKeySuggestion(EditableConfigHolder holder, SuggestionsBuilder builder, String input) {
        String pref = input == null ? "" : input.toLowerCase(Locale.ROOT);
        for (ConfigEntryDescriptor d : holder.getDescriptors()) {
            String path = d.getPath();
            if (pref.isEmpty() || path.toLowerCase(Locale.ROOT).startsWith(pref)) {
                builder.suggest(path);
            }
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> configValueSuggestion(EditableConfigHolder holder, SuggestionsBuilder builder, String configKey) {
        ConfigEntryDescriptor desc = holder.getDescriptor(configKey);
        if (desc != null && desc.getValueType() != null) {
            switch (desc.getValueType()) {
                case BOOLEAN -> {
                    builder.suggest("true");
                    builder.suggest("false");
                }
                case ENUM -> {
                    if (desc.getEnumClass() != null) {
                        for (Object ec : desc.getEnumClass().getEnumConstants()) {
                            builder.suggest(((Enum<?>) ec).name());
                        }
                    }
                }
                default -> {
                }
            }
        }
        return builder.buildFuture();
    }

    public static int executeModifyConfig(EditableConfigHolder holder, CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "configKey");
        String raw = StringArgumentType.getString(context, "configValue");
        ConfigEntryDescriptor desc = holder.getDescriptor(key);
        if (desc == null) {
            context.getSource().sendFailure(Component.literal("Unknown config path: " + key));
            return 0;
        }
        Object value;
        try {
            value = parseValue(desc, raw);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal(ex.getMessage() == null ? "Invalid value" : ex.getMessage()));
            return 0;
        }
        holder.set(key, value);
        holder.validateAfterChanges();
        holder.save();
        context.getSource().sendSuccess(Component.literal("Updated " + key + " = " + raw), false);
        return 1;
    }

    private static Object parseValue(ConfigEntryDescriptor desc, String raw) {
        ConfigEntryDescriptor.ConfigValueType t = desc.getValueType();
        if (t == null) {
            throw new IllegalArgumentException("No value type for path");
        }
        return switch (t) {
            case STRING -> raw;
            case BOOLEAN -> Boolean.parseBoolean(raw);
            case INTEGER -> Integer.parseInt(raw);
            case LONG -> Long.parseLong(raw);
            case DOUBLE -> Double.parseDouble(raw);
            case ENUM -> {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<? extends Enum> ec = (Class<? extends Enum>) desc.getEnumClass();
                if (ec == null) {
                    throw new IllegalArgumentException("Enum class missing");
                }
                yield Enum.valueOf(ec, raw);
            }
            default -> throw new IllegalArgumentException("List / complex types unsupported from command token; use Banira GUI");
        };
    }
}
