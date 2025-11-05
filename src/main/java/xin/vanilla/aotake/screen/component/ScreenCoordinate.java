package xin.vanilla.aotake.screen.component;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import xin.vanilla.aotake.enums.EnumCoordinateType;
import xin.vanilla.aotake.enums.EnumSizeType;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ScreenCoordinate implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    private ScreenCoordinate parent;

    private double x;
    private double y;
    private EnumCoordinateType xType = EnumCoordinateType.ABSOLUTE;
    private EnumCoordinateType yType = EnumCoordinateType.ABSOLUTE;

    private double width;
    private double height;
    private EnumSizeType wType = EnumSizeType.ABSOLUTE;
    private EnumSizeType hType = EnumSizeType.ABSOLUTE;

    private double u0;
    private double v0;
    private int uWidth;
    private int vHeight;

    private int uvWidth;
    private int uvHeight;

    private String textureId = "";

    public ScreenCoordinate(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public ScreenCoordinate(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean hasParent() {
        return this.parent != null;
    }

    @Override
    public ScreenCoordinate clone() {
        try {
            ScreenCoordinate cloned = (ScreenCoordinate) super.clone();
            if (this.parent != null) {
                cloned.parent = this.parent.clone();
            }
            return cloned;
        } catch (Exception e) {
            return new ScreenCoordinate();
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (this.parent != null) {
            json.add("parent", this.parent.toJson());
        }
        json.addProperty("x", this.x);
        json.addProperty("y", this.y);
        json.addProperty("xType", this.xType.name());
        json.addProperty("yType", this.yType.name());

        json.addProperty("width", this.width);
        json.addProperty("height", this.height);
        json.addProperty("wType", this.wType.name());
        json.addProperty("hType", this.hType.name());

        json.addProperty("u0", this.u0);
        json.addProperty("v0", this.v0);
        json.addProperty("uWidth", this.uWidth);
        json.addProperty("vHeight", this.vHeight);

        json.addProperty("uvWidth", this.uvWidth);
        json.addProperty("uvHeight", this.uvHeight);

        json.addProperty("textureId", this.textureId);

        return json;
    }

}
