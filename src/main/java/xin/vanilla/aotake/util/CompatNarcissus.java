package xin.vanilla.aotake.util;

public class CompatNarcissus {
    private CompatNarcissus() {
    }

    /** Narcissus 是可选依赖，避免其类缺失时阻断 Aotake 编译或类加载。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String getTpCommand() {
        try {
            Class<? extends Enum> type = (Class<? extends Enum>) Class.forName("xin.vanilla.narcissus.enums.EnumCommandType");
            Object coordinate = Enum.valueOf(type, "TP_COORDINATE");
            Class<?> utils = Class.forName("xin.vanilla.narcissus.util.NarcissusUtils");
            return (String) utils.getMethod("getCommand", type).invoke(null, coordinate);
        } catch (ReflectiveOperationException ignored) {
            return "narcissus tp";
        }
    }
}
