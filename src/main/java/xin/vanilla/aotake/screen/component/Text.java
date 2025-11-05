package xin.vanilla.aotake.screen.component;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import xin.vanilla.aotake.data.Color;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.Component;

@Setter
@Accessors(chain = true)
@OnlyIn(Dist.CLIENT)
public class Text {
    /**
     * 矩阵栈
     */
    @Getter
    private GuiGraphics graphics;
    /**
     * 字体渲染器
     */
    private Font font;
    /**
     * 是否悬浮(需手动设置状态)
     */
    private boolean hovered;
    /**
     * 文本
     */
    private Component text = Component.empty().clone();
    /**
     * 文本对齐方式(仅多行绘制时)
     */
    private Align align = Align.LEFT;
    /**
     * 鼠标悬浮时文本
     */
    private Component hoverText = Component.empty().clone();
    /**
     * 鼠标悬浮时对齐方式(仅多行绘制时)
     */
    private Align hoverAlign = Align.LEFT;

    /**
     * 文字对齐方向(仅多行绘制时)
     */
    public enum Align {
        LEFT, CENTER, RIGHT
    }

    private Text() {
    }

    private Text(String text) {
        this.text = Component.literal(text);
        this.hoverText = Component.literal(text);
    }

    public Text(Component text) {
        this.text = text;
        this.hoverText = text.clone();
    }

    public static Text literal(String text) {
        return new Text(text);
    }

    public static Text empty() {
        return new Text().setText(Component.empty()).setHoverText(Component.empty());
    }

    public static Text translatable(EnumI18nType type, String key, Object... args) {
        return new Text(Component.translatableClient(type, key, args));
    }

    public Text copy() {
        return new Text()
                .setText(this.text.clone())
                .setHoverText(this.hoverText.clone())
                .setHovered(this.hovered)
                .setAlign(this.align)
                .setHoverAlign(this.hoverAlign)
                .setGraphics(this.graphics)
                .setFont(this.font);
    }

    public Text copyWithoutChildren() {
        return new Text()
                .setText(this.text.clone().clearChildren().clearArgs())
                .setHoverText(this.hoverText.clone().clearChildren().clearArgs())
                .setHovered(this.hovered)
                .setAlign(this.align)
                .setHoverAlign(this.hoverAlign)
                .setGraphics(this.graphics)
                .setFont(this.font);
    }

    public Font getFont() {
        return font == null ? Minecraft.getInstance().font : this.font;
    }

    public boolean isEmpty() {
        return (this.text == null || this.text.isEmpty()) && (this.hoverText == null || this.hoverText.isEmpty());
    }

    public boolean isColorEmpty() {
        return this.hovered ? this.hoverText.getColor().isEmpty() : this.text.getColor().isEmpty();
    }

    public int getColor() {
        return this.hovered ? this.hoverText.getColor().rgb() : this.text.getColor().rgb();
    }

    public int getColorArgb() {
        return this.hovered ? this.hoverText.getColor().argb() : this.text.getColor().argb();
    }

    public int getColorRgba() {
        return this.hovered ? this.hoverText.getColor().rgba() : this.text.getColor().rgba();
    }

    public boolean isBgColorEmpty() {
        return this.hovered ? this.hoverText.getBgColor().isEmpty() : this.text.getBgColor().isEmpty();
    }

    public int getBgColor() {
        return this.hovered ? this.hoverText.getBgColor().rgb() : this.text.getBgColor().rgb();
    }

    public int getBgColorArgb() {
        return this.hovered ? this.hoverText.getBgColor().argb() : this.text.getBgColor().argb();
    }

    public int getBgColorRgba() {
        return this.hovered ? this.hoverText.getBgColor().rgba() : this.text.getBgColor().rgba();
    }

    public String getContent() {
        return getContent(true);
    }

    /**
     * 获取文本内容, 忽略样式
     *
     * @param ignoreStyle 是否忽略样式
     */
    public String getContent(boolean ignoreStyle) {
        return this.hovered ? this.hoverText.getString(AotakeUtils.getClientLanguage(), ignoreStyle, true) : this.text.getString(AotakeUtils.getClientLanguage(), ignoreStyle, true);
    }

    public boolean isShadow() {
        return this.hovered ? this.hoverText.isShadow() : this.text.isShadow();
    }

    public boolean isBold() {
        return this.hovered ? this.hoverText.isBold() : this.text.isBold();
    }

    public boolean isItalic() {
        return this.hovered ? this.hoverText.isItalic() : this.text.isItalic();
    }

    public boolean isUnderlined() {
        return this.hovered ? this.hoverText.isUnderlined() : this.text.isUnderlined();
    }

    public boolean isStrikethrough() {
        return this.hovered ? this.hoverText.isStrikethrough() : this.text.isStrikethrough();
    }

    public boolean isObfuscated() {
        return this.hovered ? this.hoverText.isObfuscated() : this.text.isObfuscated();
    }

    public Align getAlign() {
        return this.hovered ? this.hoverAlign : this.align;
    }

    public Text setColor(Color color) {
        this.text.setColor(color);
        this.hoverText.setColor(color);
        return this;
    }

    public Text setColor(int rgb) {
        Color color = Color.rgb(rgb);
        this.text.setColor(color);
        this.hoverText.setColor(color);
        return this;
    }

    public Text setBgColor(Color bgColor) {
        this.text.setBgColor(bgColor);
        this.hoverText.setBgColor(bgColor);
        return this;
    }

    public Text setBgColor(int rgb) {
        Color color = Color.rgb(rgb);
        this.text.setBgColor(color);
        this.hoverText.setBgColor(color);
        return this;
    }

    public Text setText(String text) {
        this.text.setI18nType(EnumI18nType.PLAIN).setText(text);
        this.hoverText.setI18nType(EnumI18nType.PLAIN).setText(text);
        return this;
    }

    public Text setText(Component text) {
        this.text = text;
        this.hoverText = text.clone();
        return this;
    }

    public Text setHoverText(String text) {
        this.hoverText.setI18nType(EnumI18nType.PLAIN).setText(text);
        return this;
    }

    public Text setHoverText(Component text) {
        this.hoverText = text;
        return this;
    }

    public Text setShadow(boolean shadow) {
        this.text.setShadow(shadow);
        this.hoverText.setShadow(shadow);
        return this;
    }

    public Text setBold(boolean bold) {
        this.text.setBold(bold);
        this.hoverText.setBold(bold);
        return this;
    }

    public Text setItalic(boolean italic) {
        this.text.setItalic(italic);
        this.hoverText.setItalic(italic);
        return this;
    }

    public Text setUnderlined(boolean underlined) {
        this.text.setUnderlined(underlined);
        this.hoverText.setUnderlined(underlined);
        return this;
    }

    public Text setStrikethrough(boolean strikethrough) {
        this.text.setStrikethrough(strikethrough);
        this.hoverText.setStrikethrough(strikethrough);
        return this;
    }

    public Text setObfuscated(boolean obfuscated) {
        this.text.setObfuscated(obfuscated);
        this.hoverText.setObfuscated(obfuscated);
        return this;
    }

    public Text setAlign(Align align) {
        this.align = align;
        this.hoverAlign = align;
        return this;
    }

    public Text withStyle(Text text) {
        this.text.withStyle(text.text);
        this.hoverText.withStyle(text.hoverText);
        return this;
    }

    public Component toComponent() {
        return this.hovered ? this.hoverText : this.text;
    }

    public static Color getTextComponentColor(net.minecraft.network.chat.Component textComponent) {
        return getTextComponentColor(textComponent, Color.white());
    }

    public static Color getTextComponentColor(net.minecraft.network.chat.Component textComponent, Color defaultColor) {
        return textComponent.getStyle().getColor() == null ? defaultColor : Color.rgb(textComponent.getStyle().getColor().getValue());
    }

    public static Text fromTextComponent(net.minecraft.network.chat.Component component) {
        return Text.literal(component.getString())
                .setColor(getTextComponentColor(component))
                .setBold(component.getStyle().isBold())
                .setItalic(component.getStyle().isItalic())
                .setUnderlined(component.getStyle().isUnderlined())
                .setStrikethrough(component.getStyle().isStrikethrough())
                .setObfuscated(component.getStyle().isObfuscated());
    }
}
