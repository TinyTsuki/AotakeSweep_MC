package xin.vanilla.aotake.config;

import net.minecraftforge.common.ForgeConfigSpec;
import xin.vanilla.aotake.AotakeSweep;

public class ServerConfig {

    public static final ForgeConfigSpec SERVER_CONFIG;

    // region 基础设置

    /**
     * 命令前缀
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_PREFIX;

    /**
     * 帮助指令信息头部内容
     */
    public static final ForgeConfigSpec.ConfigValue<String> HELP_HEADER;

    /**
     * 帮助信息每页显示的数量
     */
    public static final ForgeConfigSpec.IntValue HELP_INFO_NUM_PER_PAGE;

    /**
     * 服务器默认语言
     */
    public static final ForgeConfigSpec.ConfigValue<String> DEFAULT_LANGUAGE;

    // endregion 基础设置


    static {
        ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
        // 定义服务器基础设置
        {
            SERVER_BUILDER.comment("Base Settings", "基础设置").push("common");

            // 命令前缀
            COMMAND_PREFIX = SERVER_BUILDER
                    .comment("The prefix of the command, please only use English characters and underscores, otherwise it may cause problems.",
                            "指令前缀，请仅使用英文字母及下划线，否则可能会出现问题。")
                    .define("commandPrefix", AotakeSweep.DEFAULT_COMMAND_PREFIX);

            // 帮助指令信息头部内容
            HELP_HEADER = SERVER_BUILDER
                    .comment("The header content of the help command.",
                            "帮助指令信息头部内容。")
                    .define("helpHeader", "-----==== Aotake Sweep Help (%d/%d) ====-----");

            // 帮助信息每页显示的数量
            HELP_INFO_NUM_PER_PAGE = SERVER_BUILDER
                    .comment("The number of help information displayed per page.",
                            "每页显示的帮助信息数量。")
                    .defineInRange("helpInfoNumPerPage", 5, 1, 9999);

            // 服务器默认语言
            DEFAULT_LANGUAGE = SERVER_BUILDER
                    .comment("The default language of the server."
                            , "服务器默认语言。")
                    .define("defaultLanguage", "en_us");

            SERVER_BUILDER.pop();
        }

        SERVER_CONFIG = SERVER_BUILDER.build();
    }


    /**
     * 重置服务器配置文件
     */
    public static void resetConfig() {
        COMMAND_PREFIX.set(AotakeSweep.DEFAULT_COMMAND_PREFIX);
        HELP_HEADER.set("-----==== Aotake Sweep Help (%d/%d) ====-----");
        HELP_INFO_NUM_PER_PAGE.set(5);
        DEFAULT_LANGUAGE.set("en_us");
    }
}
