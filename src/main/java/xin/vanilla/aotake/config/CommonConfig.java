package xin.vanilla.aotake.config;

import net.minecraftforge.common.ForgeConfigSpec;
import xin.vanilla.aotake.AotakeSweep;

import java.util.ArrayList;
import java.util.List;

public class CommonConfig {

    public static final ForgeConfigSpec COMMON_CONFIG;


    // region 基础设置

    /**
     * 垃圾箱页数限制
     */
    public static final ForgeConfigSpec.IntValue DUSTBIN_PAGE_LIMIT;

    /**
     * 打扫前提示序列
     */
    public static final ForgeConfigSpec.ConfigValue<List<Integer>> SWEEP_WARNING_SECOND;

    /**
     * 打扫前提示内容
     */
    public static final ForgeConfigSpec.ConfigValue<List<String>> SWEEP_WARNING_CONTENT;

    // endregion 基础设置


    // region 自定义指令

    /**
     * 命令前缀
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_PREFIX;

    /**
     * 设置语言
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_LANGUAGE;

    /**
     * 设置虚拟权限
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_VIRTUAL_OP;

    /**
     * 打开垃圾箱
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_DUSTBIN_OPEN;

    /**
     * 清空垃圾箱
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_DUSTBIN_CLEAR;

    /**
     * 将垃圾箱物品掉落到世界
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_DUSTBIN_DROP;

    /**
     * 清空缓存
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_CACHE_CLEAR;

    /**
     * 将缓存内物品掉落至世界
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_CACHE_DROP;

    /**
     * 触发扫地
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_SWEEP;

    /**
     * 清除掉落物
     */
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_CLEAR_DROP;

    // endregion 自定义指令


    // region 简化指令

    /**
     * 设置语言
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_LANGUAGE;

    /**
     * 设置虚拟权限
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_VIRTUAL_OP;

    /**
     * 打开垃圾箱
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_DUSTBIN_OPEN;

    /**
     * 清空垃圾箱
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_DUSTBIN_CLEAR;

    /**
     * 将垃圾箱物品掉落到世界
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_DUSTBIN_DROP;

    /**
     * 清空缓存
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_CACHE_CLEAR;

    /**
     * 将缓存内物品掉落至世界
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_CACHE_DROP;

    /**
     * 触发扫地
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_SWEEP;

    /**
     * 清除掉落物
     */
    public static final ForgeConfigSpec.BooleanValue CONCISE_CLEAR_DROP;

    // endregion 简化指令


    static {
        ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

        // 定义基础设置
        {
            SERVER_BUILDER.comment("Base Settings", "基础设置").push("common");

            // 垃圾箱页数限制
            DUSTBIN_PAGE_LIMIT = SERVER_BUILDER
                    .comment("The maximum number of pages in the dustbin."
                            , "垃圾箱页数限制。")
                    .defineInRange("dustbinPageLimit", 1, 1, 16 * 16 * 16 * 16);

            // 打扫前的提示
            SWEEP_WARNING_SECOND = SERVER_BUILDER
                    .comment("A warning will be issued the specified number of seconds before the cleanup."
                            , "将会在打扫前的以下指定秒数进行提示。")
                    .define("sweepWarningSecond", new ArrayList<Integer>() {{
                        add(-1);
                        add(0);
                        add(1);
                        add(2);
                        add(3);
                        add(4);
                        add(5);
                        add(10);
                        add(30);
                        add(60);
                    }});

            // 打扫前提示内容
            SWEEP_WARNING_CONTENT = SERVER_BUILDER
                    .comment("The content of the warning before the cleanup."
                            , "打扫前提示内容，配合`sweepWarningSecond`使用，留空将使用内置提示。",
                            "[entityCount], [itemCount], [recycledItemCount], [recycledEntityCount]")
                    .define("sweepWarningContent", new ArrayList<String>() {{
                        add("§r§e香草酱什么也没吃到，失落地离开了。");
                        add("§r§e香草酱吃掉了[itemCount]个物品与[entityCount]个实体，并满意地离开了。");
                        add("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
                        add("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
                        add("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
                        add("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
                        add("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
                        add("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。");
                        add("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。");
                        add("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。");
                    }});

            SERVER_BUILDER.pop();
        }


        // 定义自定义指令配置
        {
            SERVER_BUILDER.comment("Custom Command Settings, don't add prefix '/'", "自定义指令，请勿添加前缀'/'").push("command");

            // 命令前缀
            COMMAND_PREFIX = SERVER_BUILDER
                    .comment("The prefix of the command, please only use English characters and underscores, otherwise it may cause problems.",
                            "指令前缀，请仅使用英文字母及下划线，否则可能会出现问题。")
                    .define("commandPrefix", AotakeSweep.DEFAULT_COMMAND_PREFIX);

            // 设置语言
            COMMAND_LANGUAGE = SERVER_BUILDER
                    .comment("This command is used to set the language."
                            , "设置语言的指令。")
                    .define("commandLanguage", "language");


            // 设置虚拟权限
            COMMAND_VIRTUAL_OP = SERVER_BUILDER
                    .comment("The command to set virtual permission."
                            , "设置虚拟权限的指令。")
                    .define("commandVirtualOp", "opv");

            // 打开垃圾箱
            COMMAND_DUSTBIN_OPEN = SERVER_BUILDER
                    .comment("The command to open the dustbin."
                            , "打开垃圾箱的指令。")
                    .define("commandDustbinOpen", "dustbin");

            // 清空垃圾箱
            COMMAND_DUSTBIN_CLEAR = SERVER_BUILDER
                    .comment("The command to clear the dustbin."
                            , "清空垃圾箱的指令。")
                    .define("commandDustbinClear", "cleardustbin");

            // 将垃圾箱物品掉落到世界
            COMMAND_DUSTBIN_DROP = SERVER_BUILDER
                    .comment("The command to drop dustbin items into the world."
                            , "将垃圾箱物品掉落到世界的指令。")
                    .define("commandDustbinDrop", "dropdustbin");

            // 清空缓存
            COMMAND_CACHE_CLEAR = SERVER_BUILDER
                    .comment("The command to clear the cache."
                            , "清空缓存的指令。")
                    .define("commandCacheClear", "clearcache");

            // 将缓存内物品掉落至世界
            COMMAND_CACHE_DROP = SERVER_BUILDER
                    .comment("The command to drop cache items into the world."
                            , "将缓存内物品掉落至世界的指令。")
                    .define("commandCacheDrop", "dropcache");

            // 触发扫地
            COMMAND_SWEEP = SERVER_BUILDER
                    .comment("The command to trigger sweeping."
                            , "触发扫地的指令。")
                    .define("commandSweep", "sweep");

            // 清除掉落物
            COMMAND_CLEAR_DROP = SERVER_BUILDER
                    .comment("The command to clear dropped items."
                            , "清除掉落物的指令。")
                    .define("commandClearDrop", "killitem");

            SERVER_BUILDER.pop();
        }


        // 定义简化指令
        {
            SERVER_BUILDER.comment("Concise Command Settings", "简化指令").push("concise");

            CONCISE_LANGUAGE = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Set the language' command.",
                            "是否启用无前缀版本的 '设置语言' 指令。")
                    .define("conciseLanguage", false);

            CONCISE_VIRTUAL_OP = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Set virtual permission' command.",
                            "是否启用无前缀版本的 '设置虚拟权限' 指令。")
                    .define("conciseVirtualOp", false);

            CONCISE_DUSTBIN_OPEN = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Open dustbin' command.",
                            "是否启用无前缀版本的 '打开垃圾箱' 指令。")
                    .define("conciseDustbinOpen", false);

            CONCISE_DUSTBIN_CLEAR = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Clear dustbin' command.",
                            "是否启用无前缀版本的 '清空垃圾箱' 指令。")
                    .define("conciseDustbinClear", false);

            CONCISE_DUSTBIN_DROP = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Drop dustbin items' command.",
                            "是否启用无前缀版本的 '将垃圾箱物品掉落到世界' 指令。")
                    .define("conciseDustbinDrop", false);

            CONCISE_CACHE_CLEAR = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Clear cache' command.",
                            "是否启用无前缀版本的 '清空缓存' 指令。")
                    .define("conciseCacheClear", false);

            CONCISE_CACHE_DROP = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Drop cache items' command.",
                            "是否启用无前缀版本的 '将缓存内物品掉落至世界' 指令。")
                    .define("conciseCacheDrop", false);

            CONCISE_SWEEP = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Trigger sweep' command.",
                            "是否启用无前缀版本的 '触发扫地' 指令。")
                    .define("conciseSweep", false);

            CONCISE_CLEAR_DROP = SERVER_BUILDER
                    .comment("Enable or disable the concise version of the 'Clear dropped items' command.",
                            "是否启用无前缀版本的 '清除掉落物' 指令。")
                    .define("conciseClearDrop", true);

            SERVER_BUILDER.pop();

        }

        COMMON_CONFIG = SERVER_BUILDER.build();
    }


    /**
     * 重置服务器配置文件
     */
    public static void resetConfig() {
        DUSTBIN_PAGE_LIMIT.set(1);
        SWEEP_WARNING_SECOND.set(new ArrayList<Integer>() {{
            add(-1);
            add(0);
            add(1);
            add(2);
            add(3);
            add(4);
            add(5);
            add(10);
            add(30);
            add(60);
        }});
        SWEEP_WARNING_CONTENT.set(new ArrayList<String>() {{
            add("§r§e香草酱什么也没吃到，失落地离开了。");
            add("§r§e香草酱吃掉了[itemCount]个物品与[entityCount]个实体，并满意地离开了。");
            add("§r§e饥肠辘辘的香草酱将会在%s秒后到来！");
            add("§r§e饥肠辘辘的香草酱将会在%s秒后到来！");
            add("§r§e饥肠辘辘的香草酱将会在%s秒后到来！");
            add("§r§e饥肠辘辘的香草酱将会在%s秒后到来！");
            add("§r§e饥肠辘辘的香草酱将会在%s秒后到来！");
            add("§r§e饥肠辘辘的香草酱将会在%s秒后到来。");
            add("§r§e饥肠辘辘的香草酱将会在%s秒后到来。");
            add("§r§e饥肠辘辘的香草酱将会在%s秒后到来。");
        }});

        COMMAND_PREFIX.set(AotakeSweep.DEFAULT_COMMAND_PREFIX);
        COMMAND_LANGUAGE.set("language");
        COMMAND_VIRTUAL_OP.set("opv");
        COMMAND_DUSTBIN_OPEN.set("dustbin");
        COMMAND_DUSTBIN_CLEAR.set("cleardustbin");
        COMMAND_DUSTBIN_DROP.set("dropdustbin");
        COMMAND_CACHE_CLEAR.set("clearcache");
        COMMAND_CACHE_DROP.set("dropcache");
        COMMAND_SWEEP.set("sweep");
        COMMAND_CLEAR_DROP.set("killitem");

        CONCISE_LANGUAGE.set(false);
        CONCISE_VIRTUAL_OP.set(false);
        CONCISE_DUSTBIN_OPEN.set(false);
        CONCISE_DUSTBIN_CLEAR.set(false);
        CONCISE_DUSTBIN_DROP.set(false);
        CONCISE_CACHE_CLEAR.set(false);
        CONCISE_CACHE_DROP.set(false);
        CONCISE_SWEEP.set(false);
        CONCISE_CLEAR_DROP.set(true);

        COMMON_CONFIG.save();
    }

    public static void resetConfigWithMode1() {
        resetConfig();

        SWEEP_WARNING_SECOND.set(new ArrayList<Integer>() {{
            add(-1);
            add(0);
            add(1);
            add(2);
            add(3);
            add(4);
            add(5);
            add(10);
            add(30);
            add(60);
        }});
        SWEEP_WARNING_CONTENT.set(new ArrayList<String>() {{
            add("§r§e世界很干净。");
            add("§r§e清理了[itemCount]个物品与[entityCount]个实体。");
            add("§r§e清理将会在§r§e%s§r§e秒后开始！");
            add("§r§e清理将会在§r§e%s§r§e秒后开始！");
            add("§r§e清理将会在§r§e%s§r§e秒后开始！");
            add("§r§e清理将会在§r§e%s§r§e秒后开始！");
            add("§r§e清理将会在§r§e%s§r§e秒后开始！");
            add("§r§e清理将会在§r§e%s§r§e秒后开始。");
            add("§r§e清理将会在§r§e%s§r§e秒后开始。");
            add("§r§e清理将会在§r§e%s§r§e秒后开始。");
        }});

        COMMON_CONFIG.save();
    }

    public static void resetConfigWithMode2() {
        resetConfig();

        SWEEP_WARNING_SECOND.set(new ArrayList<Integer>() {{
            add(-1);
            add(0);
            add(1);
            add(2);
            add(3);
            add(4);
            add(5);
            add(10);
            add(30);
            add(60);
        }});
        SWEEP_WARNING_CONTENT.set(new ArrayList<String>() {{
            add("§r§eCleaned up nothing.");
            add("§r§eCleaned up [itemCount] items and [entityCount] entities.");
            add("§r§eThe cleanup will start in §r§e%s§r§e seconds!");
            add("§r§eThe cleanup will start in §r§e%s§r§e seconds!");
            add("§r§eThe cleanup will start in §r§e%s§r§e seconds!");
            add("§r§eThe cleanup will start in §r§e%s§r§e seconds!");
            add("§r§eThe cleanup will start in §r§e%s§r§e seconds!");
            add("§r§eThe cleanup will start in §r§e%s§r§e seconds.");
            add("§r§eThe cleanup will start in §r§e%s§r§e seconds.");
            add("§r§eThe cleanup will start in §r§e%s§r§e seconds.");
        }});

        COMMON_CONFIG.save();
    }
}
