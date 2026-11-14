package xin.vanilla.aotake.util;

public class CompatNarcissus {
    public static String getTpCommand() {
        try {
            return xin.vanilla.narcissus.util.NarcissusUtils
                    .getCommand(xin.vanilla.narcissus.enums.EnumCommandType.TP_COORDINATE);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}
