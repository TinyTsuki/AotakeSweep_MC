package xin.vanilla.aotake.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.enums.EnumI18nType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor
@Accessors(chain = true)
public class Component implements Cloneable, Serializable {

    // region 属性定义
    /**
     * 文本
     */
    @Getter
    @Setter
    private String text = "";
    /**
     * i18n类型
     */
    @Getter
    @Setter
    private EnumI18nType i18nType = EnumI18nType.PLAIN;

    /**
     * 子组件
     */
    private List<Component> children = new ArrayList<>();

    /**
     * 翻译组件参数
     */
    private List<Component> args = new ArrayList<>();

    /**
     * 原始组件
     */
    @Getter
    @Setter
    private Object original = null;

    // region 样式属性

    /**
     * 语言代码
     */
    @Setter
    private String languageCode;
    /**
     * 文本颜色
     */
    @Getter
    private xin.vanilla.aotake.data.Color color = xin.vanilla.aotake.data.Color.white();
    /**
     * 文本背景色
     */
    @Getter
    private xin.vanilla.aotake.data.Color bgColor = xin.vanilla.aotake.data.Color.argb(0x00000000);
    /**
     * 是否有阴影
     */
    @Setter
    private Boolean shadow;
    /**
     * 是否粗体
     */
    @Setter
    private Boolean bold;
    /**
     * 是否斜体
     */
    @Setter
    private Boolean italic;
    /**
     * 是否下划线
     */
    @Setter
    private Boolean underlined;
    /**
     * 是否中划线
     */
    @Setter
    private Boolean strikethrough;
    /**
     * 是否混淆
     */
    @Setter
    private Boolean obfuscated;
    /**
     * 点击事件
     */
    @Setter
    @Getter
    private ClickEvent clickEvent;
    /**
     * 悬停事件
     */
    @Setter
    @Getter
    private HoverEvent hoverEvent;

    // endregion 样式属性

    // endregion 属性定义

    public Component(String text) {
        this.text = text;
    }

    public Component(String text, EnumI18nType i18nType) {
        this.text = text;
        this.i18nType = i18nType;
    }

    public Component setColor(xin.vanilla.aotake.data.Color color) {
        this.color = color;
        return this;
    }

    public Component setColor(int rgb) {
        this.color = xin.vanilla.aotake.data.Color.rgb(rgb);
        return this;
    }

    public Component setBgColor(xin.vanilla.aotake.data.Color color) {
        this.bgColor = color;
        return this;
    }

    public Component setBgColor(int rgb) {
        this.bgColor = xin.vanilla.aotake.data.Color.rgb(rgb);
        return this;
    }

    // region NonNull Getter

    /**
     * 内容是否为空
     */
    public boolean isEmpty() {
        return StringUtils.isNullOrEmptyEx(this.getText())
                && this.getOriginal() == null
                && this.getChildren().isEmpty()
                && this.getArgs().isEmpty();
    }

    /**
     * 获取语言代码
     */
    public @NonNull String getLanguageCode() {
        String language = this.languageCode;
        if (StringUtils.isNullOrEmpty(language)) {
            try {
                language = ServerConfig.SERVER_CONFIG.defaultLanguage();
            } catch (Exception e) {
                if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                    language = AotakeUtils.getClientLanguage();
                } else {
                    language = "en_us";
                }
            }
        }
        return language;
    }

    /**
     * 是否有阴影
     */
    public boolean isShadow() {
        return this.shadow != null && this.shadow;
    }

    /**
     * 是否粗体
     */
    public boolean isBold() {
        return this.bold != null && this.bold;
    }

    /**
     * 是否斜体
     */
    public boolean isItalic() {
        return this.italic != null && this.italic;
    }

    /**
     * 是否下划线
     */
    public boolean isUnderlined() {
        return this.underlined != null && this.underlined;
    }

    /**
     * 是否中划线
     */
    public boolean isStrikethrough() {
        return this.strikethrough != null && this.strikethrough;
    }

    /**
     * 是否混淆
     */
    public boolean isObfuscated() {
        return this.obfuscated != null && this.obfuscated;
    }

    // endregion NonNull Getter

    // region 样式元素是否为空(用于父组件样式传递)

    /**
     * 语言代码是否为空
     */
    public boolean isLanguageCodeEmpty() {
        return this.languageCode == null;
    }

    /**
     * 阴影状态是否为空
     */
    public boolean isShadowEmpty() {
        return this.shadow == null;
    }

    /**
     * 粗体状态是否为空
     */
    public boolean isBoldEmpty() {
        return this.bold == null;
    }

    /**
     * 斜体状态是否为空
     */
    public boolean isItalicEmpty() {
        return this.italic == null;
    }

    /**
     * 下划线状态是否为空
     */
    public boolean isUnderlinedEmpty() {
        return this.underlined == null;
    }

    /**
     * 中划线状态是否为空
     */
    public boolean isStrikethroughEmpty() {
        return this.strikethrough == null;
    }

    /**
     * 混淆状态是否为空
     */
    public boolean isObfuscatedEmpty() {
        return this.obfuscated == null;
    }

    // endregion 样式元素是否为空(用于父组件样式传递)

    private Component setChildren(List<Component> children) {
        this.children = children;
        return this;
    }

    private Component setArgs(List<Component> args) {
        this.args = args;
        return this;
    }

    public Component clone() {
        try {
            Component component = (Component) super.clone();
            component.setText(this.text)
                    .setI18nType(this.i18nType)
                    .setLanguageCode(this.languageCode)
                    .setColor(this.color)
                    .setBgColor(this.bgColor)
                    .setShadow(this.shadow)
                    .setBold(this.bold)
                    .setItalic(this.italic)
                    .setUnderlined(this.underlined)
                    .setStrikethrough(this.strikethrough)
                    .setObfuscated(this.obfuscated)
                    .setClickEvent(this.clickEvent)
                    .setHoverEvent(this.hoverEvent);

            if (CollectionUtils.isNotNullOrEmpty(this.getChildren())) {
                List<Component> clonedChildren = new ArrayList<>(this.getChildren().size());
                for (Component child : this.getChildren()) {
                    clonedChildren.add(child != null ? child.clone() : null);
                }
                component.setChildren(clonedChildren);
            } else {
                component.setChildren(null);
            }

            if (CollectionUtils.isNotNullOrEmpty(this.getArgs())) {
                List<Component> clonedArgs = new ArrayList<>(this.getArgs().size());
                for (Component arg : this.getArgs()) {
                    clonedArgs.add(arg != null ? arg.clone() : null);
                }
                component.setArgs(clonedArgs);
            } else {
                component.setArgs(null);
            }

            return component;
        } catch (CloneNotSupportedException e) {
            return empty();
        }
    }

    public Component append(Object... objs) {
        return this.appendIndex(this.getChildren().size(), objs);
    }

    public Component appendIndex(int index, Object... objs) {
        for (int i = 0; i < objs.length; i++) {
            Object obj = objs[i];
            if (obj instanceof Component) {
                this.getChildren().add(index + i, ((Component) obj).withStyle(this));
            } else {
                this.getChildren().add(index + i, new Component(obj.toString()).withStyle(this));
            }
        }
        return this;
    }

    public Component appendArg(Object... objs) {
        return this.appendArg(this.getArgs().size(), objs);
    }

    public Component appendArg(int index, Object... objs) {
        for (int i = 0; i < objs.length; i++) {
            Object obj = objs[i];
            if (obj instanceof Component) {
                this.getArgs().add(index + i, ((Component) obj).withStyle(this));
            } else {
                this.getArgs().add(index + i, new Component(obj.toString()).withStyle(this));
            }
        }
        return this;
    }

    public List<Component> getChildren() {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        return this.children;
    }

    public List<Component> getArgs() {
        if (this.args == null) {
            this.args = new ArrayList<>();
        }
        return this.args;
    }

    public Component clearChildren() {
        if (CollectionUtils.isNotNullOrEmpty(this.children)) {
            this.children = new ArrayList<>();
        }
        return this;
    }

    public Component clearArgs() {
        if (CollectionUtils.isNotNullOrEmpty(this.args)) {
            this.args = new ArrayList<>();
        }
        return this;
    }

    /**
     * 将另一个组件的样式应用到当前组件
     */
    public Component withStyle(Component component) {
        if (this.isLanguageCodeEmpty() && !component.isLanguageCodeEmpty()) {
            this.setLanguageCode(component.getLanguageCode());
        }
        if ((this.getColor().isEmpty()) && !component.getColor().isEmpty()) {
            this.setColor(component.getColor());
        }
        if ((this.getBgColor().isEmpty()) && !component.getBgColor().isEmpty()) {
            this.setBgColor(component.getBgColor());
        }
        if (this.isShadowEmpty() && !component.isShadowEmpty()) {
            this.setShadow(component.isShadow());
        }
        if (this.isBoldEmpty() && !component.isBoldEmpty()) {
            this.setBold(component.isBold());
        }
        if (this.isItalicEmpty() && !component.isItalicEmpty()) {
            this.setItalic(component.isItalic());
        }
        if (this.isUnderlinedEmpty() && !component.isUnderlinedEmpty()) {
            this.setUnderlined(component.isUnderlined());
        }
        if (this.isStrikethroughEmpty() && !component.isStrikethroughEmpty()) {
            this.setStrikethrough(component.isStrikethrough());
        }
        if (this.isObfuscatedEmpty() && !component.isObfuscatedEmpty()) {
            this.setObfuscated(component.isObfuscated());
        }
        if (this.clickEvent == null && component.clickEvent != null) {
            this.clickEvent = component.clickEvent;
        }
        if (this.hoverEvent == null && component.hoverEvent != null) {
            this.hoverEvent = component.hoverEvent;
        }
        return this;
    }

    public Style getStyle() {
        Style style = Style.EMPTY;
        if (!this.getColor().isEmpty() && this.getColor().color() != 0xFFFFFF)
            style = style.withColor(TextColor.fromRgb(getColor().rgb()));
        style = style.withUnderlined(this.isUnderlined())
                .withStrikethrough(this.isStrikethrough())
                .withObfuscated(this.isObfuscated())
                .withBold(this.isBold())
                .withItalic(this.isItalic())
                .withClickEvent(this.clickEvent)
                .withHoverEvent(this.hoverEvent);
        return style;
    }

    /**
     * 获取文本
     */
    public String toString() {
        return this.getString(this.getLanguageCode(), false, true);
    }

    /**
     * 获取文本
     *
     * @param igStyle 是否忽略样式
     */
    public String toString(boolean igStyle) {
        return this.getString(this.getLanguageCode(), igStyle, true);
    }

    /**
     * 获取指定语言文本
     *
     * @param languageCode 语言代码
     */
    public String getString(String languageCode) {
        return this.getString(languageCode, false, true);
    }

    /**
     * 获取指定语言文本
     *
     * @param languageCode 语言代码
     * @param igStyle      是否忽略样式
     * @param igColor      是否忽略颜色
     */
    public String getString(String languageCode, boolean igStyle, boolean igColor) {
        StringBuilder result = new StringBuilder();
        String colorStr = this.getColor().isEmpty() ? "§f" : StringUtils.argbToMinecraftColorString(getColor().rgb());
        igColor = igColor && colorStr.equalsIgnoreCase("§f");
        // 如果颜色值为透明，则不显示内容，所以返回空文本
        if (!this.getColor().isEmpty()) {
            if (!igStyle) {
                if (!igColor) {
                    result.append(colorStr);
                }
                // 添加样式：粗体
                if (isBold()) {
                    result.append("§l");
                }
                // 添加样式：斜体
                if (isItalic()) {
                    result.append("§o");
                }
                // 添加样式：下划线
                if (isUnderlined()) {
                    result.append("§n");
                }
                // 添加样式：中划线
                if (isStrikethrough()) {
                    result.append("§m");
                }
                // 添加样式：混淆
                if (isObfuscated()) {
                    result.append("§k");
                }
            }
            if (this.i18nType == EnumI18nType.PLAIN) {
                result.append(this.text);
            } else if (i18nType == EnumI18nType.ORIGINAL) {
                result.append(((net.minecraft.network.chat.Component) this.original).getString());
            } else {
                result.append(I18nUtils.getTranslation(I18nUtils.getKey(this.i18nType, this.text), languageCode));
            }
        }
        boolean finalIgColor = igColor;
        this.getChildren().forEach(component -> result.append(component.getString(languageCode, igStyle, finalIgColor)));
        return StringUtils.format(result.toString(), this.getArgs().stream().map(component -> component.getString(languageCode, igStyle, finalIgColor)).toArray());
    }

    /**
     * 获取文本组件
     */
    public net.minecraft.network.chat.Component toTextComponent() {
        return this.toTextComponent(this.getLanguageCode());
    }

    /**
     * 获取文本组件
     *
     * @param languageCode 语言代码
     */
    public net.minecraft.network.chat.Component toTextComponent(String languageCode) {
        List<MutableComponent> components = new ArrayList<>();
        if (this.i18nType == EnumI18nType.ORIGINAL) {
            components.add((MutableComponent) this.original);
        } else {
            // 如果颜色值为null则说明为透明，则不显示内容，所以返回空文本组件
            if (!this.getColor().isEmpty()) {
                if (this.i18nType != EnumI18nType.PLAIN) {
                    String text = I18nUtils.getTranslation(I18nUtils.getKey(this.i18nType, this.text), languageCode);
                    String[] split = text.split(StringUtils.FORMAT_REGEX, -1);
                    for (String s : split) {
                        components.add(net.minecraft.network.chat.Component.literal(s).withStyle(this.getStyle()));
                    }
                    Pattern pattern = Pattern.compile(StringUtils.FORMAT_REGEX);
                    Matcher matcher = pattern.matcher(text);
                    int i = 0;
                    while (matcher.find()) {
                        String placeholder = matcher.group();
                        int index = placeholder.contains("$") ? StringUtils.toInt(placeholder.split("\\$")[0].substring(1)) - 1 : -1;
                        if (index == -1) {
                            index = i;
                        }
                        Component formattedArg = new Component(placeholder).withStyle(this);
                        if (index < this.getArgs().size()) {
                            if (this.getArgs().get(index) == null) {
                                formattedArg = new Component();
                            } else {
                                Component argComponent = this.getArgs().get(index);
                                if (argComponent.getI18nType() != EnumI18nType.PLAIN) {
                                    // 语言代码传递
                                    if (argComponent.isLanguageCodeEmpty()) {
                                        argComponent.setLanguageCode(languageCode);
                                    }
                                    try {
                                        // 颜色代码传递
                                        String colorCode = split[i].replaceAll("^.*?((?:§[\\da-fA-FKLMNORklmnor])*)$", "$1");
                                        formattedArg = new Component(String.format(placeholder.replaceAll("^%\\d+\\$", "%"), colorCode + argComponent)).withStyle(argComponent);
                                    } catch (Exception e) {
                                        // 颜色传递
                                        if (argComponent.getColor().isEmpty()) {
                                            argComponent.setColor(this.color);
                                        }
                                        formattedArg = argComponent;
                                    }
                                } else {
                                    // 颜色传递
                                    if (argComponent.getColor().isEmpty()) {
                                        argComponent.setColor(this.color);
                                    }
                                    formattedArg = argComponent;
                                }
                            }
                        }
                        if (components.size() > i) {
                            components.get(i).append(formattedArg.toTextComponent());
                        }
                        i++;
                    }
                } else {
                    components.add(net.minecraft.network.chat.Component.literal(this.text).withStyle(this.getStyle()));
                }
            }
        }
        components.addAll(this.getChildren().stream().map(component -> (MutableComponent) component.toTextComponent(languageCode)).collect(Collectors.toList()));
        if (components.isEmpty()) {
            components.add(net.minecraft.network.chat.Component.literal(""));
        }
        MutableComponent result = components.get(0);
        for (int j = 1; j < components.size(); j++) {
            result.append(components.get(j));
        }
        return result.withStyle(this.getStyle());
    }

    /**
     * 获取翻译文本组件
     */
    public net.minecraft.network.chat.Component toTranslatedTextComponent() {
        MutableComponent result = net.minecraft.network.chat.Component.translatable("");
        if (!this.getColor().isEmpty() || !this.getBgColor().isEmpty()) {
            if (this.i18nType != EnumI18nType.PLAIN) {
                Object[] objects = this.getArgs().stream().map(component -> {
                    if (component.i18nType == EnumI18nType.PLAIN) {
                        return component.toTextComponent();
                    } else {
                        return component.toTranslatedTextComponent();
                    }
                }).toArray();
                if (CollectionUtils.isNotNullOrEmpty(objects)) {
                    result = net.minecraft.network.chat.Component.translatable(I18nUtils.getKey(this.i18nType, this.text), objects);
                } else {
                    result = net.minecraft.network.chat.Component.translatable(I18nUtils.getKey(this.i18nType, this.text));
                }
            } else {
                result = net.minecraft.network.chat.Component.literal(this.text).withStyle(this.getStyle());
            }
        }
        for (Component child : this.getChildren()) {
            result.append(child.toTranslatedTextComponent());
        }
        return result;
    }

    /**
     * 获取聊天文本组件
     *
     * @return 格式化颜色后的文本组件
     */
    public net.minecraft.network.chat.Component toChatComponent() {
        return this.toChatComponent(this.getLanguageCode());
    }

    /**
     * 获取聊天文本组件
     *
     * @return 格式化颜色后的文本组件
     */
    public net.minecraft.network.chat.Component toChatComponent(String languageCode) {
        return rewriteColor(this.toTextComponent(languageCode));
    }

    // 😵‍💫
    public static net.minecraft.network.chat.Component rewriteColor(net.minecraft.network.chat.Component component) {
        if (component instanceof MutableComponent) {
            TextColor color = component.getStyle().getColor();
            if (color != null && color.serialize().startsWith("#")) {
                Style style = component.getStyle().withColor(TextColor.parseColor(StringUtils.argbToMinecraftColor(StringUtils.argbToHex(color.serialize())).name().toLowerCase()));
                ((MutableComponent) component).setStyle(style);
            }
        }
        for (net.minecraft.network.chat.Component sibling : component.getSiblings()) {
            rewriteColor(sibling);
        }
        return component;
    }

    /**
     * 获取空文本组件
     */
    public static Component empty() {
        return new Component();
    }

    /**
     * 获取原始组件
     */
    public static Component original(Object original) {
        return empty().setOriginal(original).setI18nType(EnumI18nType.ORIGINAL);
    }

    /**
     * 获取文本组件
     *
     * @param text 文本
     */
    public static Component literal(String text) {
        return new Component().setText(text);
    }

    /**
     * 获取翻译文本组件
     *
     * @param key  翻译键
     * @param args 参数
     */
    public static Component translatable(String key, Object... args) {
        return new Component(key, EnumI18nType.NONE).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param type 翻译类型
     * @param key  翻译键
     * @param args 参数
     */
    public static Component translatable(EnumI18nType type, String key, Object... args) {
        return new Component(key, type).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param key  翻译键
     * @param args 参数
     */
    public static Component translatableClient(String key, Object... args) {
        return new Component(key, EnumI18nType.NONE).setLanguageCode(AotakeUtils.getClientLanguage()).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param type 翻译类型
     * @param key  翻译键
     * @param args 参数
     */
    public static Component translatableClient(EnumI18nType type, String key, Object... args) {
        return new Component(key, type).setLanguageCode(AotakeUtils.getClientLanguage()).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param languageCode 语言代码
     * @param type         翻译类型
     * @param key          翻译键
     * @param args         参数
     */
    public static Component translatable(String languageCode, EnumI18nType type, String key, Object... args) {
        return new Component(key, type).setLanguageCode(languageCode).appendArg(args);
    }

    /**
     * 获取翻译文本组件
     *
     * @param player 玩家
     * @param type   翻译类型
     * @param key    翻译键
     * @param args   参数
     */
    public static Component translatable(ServerPlayer player, EnumI18nType type, String key, Object... args) {
        return new Component(key, type).setLanguageCode(AotakeUtils.getPlayerLanguage(player)).appendArg(args);
    }

    public static Component deserialize(JsonObject jsonObject) {
        Component result = new Component();
        result.setText(JsonUtils.getString(jsonObject, "text"));
        result.setI18nType(EnumI18nType.valueOf(JsonUtils.getString(jsonObject, "i18nType")));
        result.setLanguageCode(JsonUtils.getString(jsonObject, "languageCode"));
        result.setColor(xin.vanilla.aotake.data.Color.argb(JsonUtils.getInt(jsonObject, "color")));
        result.setBgColor(xin.vanilla.aotake.data.Color.argb(JsonUtils.getInt(jsonObject, "bgColor")));
        result.setShadow(JsonUtils.getBoolean(jsonObject, "shadow"));
        result.setBold(JsonUtils.getBoolean(jsonObject, "bold"));
        result.setItalic(JsonUtils.getBoolean(jsonObject, "italic"));
        result.setUnderlined(JsonUtils.getBoolean(jsonObject, "underlined"));
        result.setStrikethrough(JsonUtils.getBoolean(jsonObject, "strikethrough"));
        result.setObfuscated(JsonUtils.getBoolean(jsonObject, "obfuscated"));
        String clickAction = JsonUtils.getString(jsonObject, "clickEvent.action", "");
        String clickValue = JsonUtils.getString(jsonObject, "clickEvent.value", "");
        if (StringUtils.isNotNullOrEmpty(clickAction) && StringUtils.isNotNullOrEmpty(clickValue)) {
            result.setClickEvent(new ClickEvent(ClickEvent.Action.valueOf(clickAction), clickValue));
        }
        JsonObject hover = JsonUtils.getJsonObject(jsonObject, "hoverEvent", null);
        if (hover != null) {
            result.setHoverEvent(HoverEvent.deserialize(hover));
        }
        for (JsonElement childJson : JsonUtils.getJsonArray(jsonObject, "children", new JsonArray())) {
            result.getChildren().add(deserialize((JsonObject) childJson));
        }
        for (JsonElement argJson : JsonUtils.getJsonArray(jsonObject, "args", new JsonArray())) {
            result.getArgs().add(deserialize((JsonObject) argJson));
        }
        return result;
    }

    public static JsonObject serialize(Component component) {
        JsonObject result = new JsonObject();
        JsonUtils.set(result, "text", component.getText());
        JsonUtils.set(result, "i18nType", component.getI18nType().name());
        JsonUtils.set(result, "languageCode", component.getLanguageCode());
        JsonUtils.set(result, "color", component.getColor().argb());
        JsonUtils.set(result, "bgColor", component.getBgColor().argb());
        JsonUtils.set(result, "shadow", component.isShadow());
        JsonUtils.set(result, "bold", component.isBold());
        JsonUtils.set(result, "italic", component.isItalic());
        JsonUtils.set(result, "underlined", component.isUnderlined());
        JsonUtils.set(result, "strikethrough", component.isStrikethrough());
        JsonUtils.set(result, "obfuscated", component.isObfuscated());
        if (component.getClickEvent() != null) {
            JsonUtils.set(result, "clickEvent.action", component.getClickEvent().getAction().getName());
            JsonUtils.set(result, "clickEvent.value", component.getClickEvent().getValue());
        }
        if (component.getHoverEvent() != null) {
            JsonUtils.set(result, "hoverEvent", component.getHoverEvent().serialize());
        }
        JsonArray children = new JsonArray();
        for (Component child : component.getChildren()) {
            children.add(serialize(child));
        }
        JsonUtils.set(result, "children", children);
        JsonArray args = new JsonArray();
        for (Component arg : component.getArgs()) {
            args.add(serialize(arg));
        }
        JsonUtils.set(result, "args", args);
        return result;
    }

}
