package xin.vanilla.aotake.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumChunkCheckMode;
import xin.vanilla.aotake.enums.EnumOverflowMode;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ServerConfig {

    public static final ModConfigSpec SERVER_CONFIG;

    // region 基础设置

    /**
     * 帮助指令信息头部内容
     */
    public static final ModConfigSpec.ConfigValue<String> HELP_HEADER;

    /**
     * 帮助信息每页显示的数量
     */
    public static final ModConfigSpec.IntValue HELP_INFO_NUM_PER_PAGE;

    /**
     * 服务器默认语言
     */
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_LANGUAGE;

    /**
     * 扫地间隔(毫秒)
     */
    public static final ModConfigSpec.LongValue SWEEP_INTERVAL;

    /**
     * 自清洁间隔
     */
    public static final ModConfigSpec.LongValue SELF_CLEAN_INTERVAL;

    /**
     * 区块实体过多检测间隔(毫秒)
     */
    public static final ModConfigSpec.LongValue CHUNK_CHECK_INTERVAL;

    /**
     * 区块实体过多检测阈值
     */
    public static final ModConfigSpec.IntValue CHUNK_CHECK_LIMIT;

    /**
     * 区块实体清理时保留的实体数量
     */
    public static final ModConfigSpec.IntValue CHUNK_CHECK_RETAIN;

    /**
     * 区块实体过多提示
     */
    public static final ModConfigSpec.BooleanValue CHUNK_CHECK_NOTICE;

    /**
     * 区块实体过多清理模式
     */
    public static final ModConfigSpec.ConfigValue<String> CHUNK_CHECK_CLEAN_MODE;

    /**
     * 使用以下物品捕获被清理的实体
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CATCH_ITEM;

    /**
     * 允许被清理的实体
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> JUNK_ENTITY;

    /**
     * 清理时允许被捕获的实体
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CATCH_ENTITY;

    /**
     * 物品清理白名单
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_WHITELIST;

    /**
     * 物品清理黑名单
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

    /**
     * 黑白名单物品超过指定数量也进行清理
     */
    public static final ModConfigSpec.IntValue ITEM_WHITE_BLACK_LIST_ENTITY_LIMIT;

    /**
     * 仅清理不回收的物品
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_REDLIST;

    /**
     * 是否允许玩家使用物品捕获实体
     */
    public static final ModConfigSpec.ConfigValue<Boolean> ALLOW_CATCH_ENTITY;

    /**
     * 仅清理掉落超过指定tick的物品
     */
    public static final ModConfigSpec.IntValue SWEEP_ITEM_AGE;

    /**
     * 垃圾箱自清洁方式
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SELF_CLEAN_MODE;

    /**
     * 垃圾箱溢出时的处理方式
     */
    public static final ModConfigSpec.ConfigValue<String> DUSTBIN_OVERFLOW_MODE;

    /**
     * 贪婪模式
     */
    public static final ModConfigSpec.BooleanValue GREEDY_MODE;

    /**
     * 实体清理NBT白名单
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_NBT_WHITELIST_RAW;
    public static List<KeyValue<String, String>> ENTITY_NBT_WHITELIST;

    /**
     * 实体清理NBT黑名单
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_NBT_BLACKLIST_RAW;
    public static List<KeyValue<String, String>> ENTITY_NBT_BLACKLIST;

    /**
     * 实体清理NBT黑白名单超过指定数量也进行清理
     */
    public static final ModConfigSpec.IntValue NBT_WHITE_BLACK_LIST_ENTITY_LIMIT;

    // endregion 基础设置


    // region 指令权限

    /**
     * 设置虚拟权限指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_VIRTUAL_OP;

    /**
     * 打开垃圾箱指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_DUSTBIN_OPEN;

    /**
     * 为他人打开垃圾箱指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_DUSTBIN_OPEN_OTHER;

    /**
     * 清空垃圾箱指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_DUSTBIN_CLEAR;

    /**
     * 将垃圾箱物品掉落到世界指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_DUSTBIN_DROP;

    /**
     * 清空缓存指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_CACHE_CLEAR;

    /**
     * 将缓存内物品掉落至世界指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_CACHE_DROP;

    /**
     * 触发扫地指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_SWEEP;

    /**
     * 清除掉落物指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_CLEAR_DROP;

    /**
     * 延迟本次清理指令所需的权限等级
     */
    public static final ModConfigSpec.IntValue PERMISSION_DELAY_SWEEP;

    // endregion 指令权限


    static {
        ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();
        // 定义服务器基础设置
        {
            SERVER_BUILDER.comment("Base Settings", "基础设置").push("common");

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

            // 扫地间隔(毫秒)
            SWEEP_INTERVAL = SERVER_BUILDER
                    .comment("The interval of sweeping (in milliseconds)."
                            , "扫地间隔(毫秒)。")
                    .defineInRange("sweepInterval", 10 * 60 * 1000, 0L, 7 * 24 * 60 * 60 * 1000);

            // 自清洁间隔(毫秒)
            SELF_CLEAN_INTERVAL = SERVER_BUILDER
                    .comment("The interval of self-cleaning (in milliseconds)."
                            , "自清洁间隔(毫秒)。")
                    .defineInRange("selfCleanInterval", 60 * 60 * 1000, 0L, 7 * 24 * 60 * 60 * 1000);

            // 区块实体过多检测间隔(毫秒)
            CHUNK_CHECK_INTERVAL = SERVER_BUILDER
                    .comment("The interval for detecting excessive entities in a chunk (in milliseconds), 0 to disable."
                            , "区块实体过多检测间隔(毫秒)，0为禁用。")
                    .defineInRange("chunkCheckInterval", 5 * 1000, 0L, 7 * 24 * 60 * 60 * 1000);

            // 区块实体过多检测阈值
            CHUNK_CHECK_LIMIT = SERVER_BUILDER
                    .comment("The threshold for detecting excessive entities in a chunk."
                            , "区块实体过多检测阈值。")
                    .defineInRange("chunkCheckLimit", 250, 1, Integer.MAX_VALUE);

            // 区块实体过多检测保留的实体数量
            CHUNK_CHECK_RETAIN = SERVER_BUILDER
                    .comment("Entities retained during cleanup (Default: half of threshold to prevent excessive loss)."
                            , "区块实体过多检测清理时保留的实体数量，默认保留检测阈值的一半，避免全部清理而导致损失过大。")
                    .defineInRange("chunkCheckRetain", 125, 1, Integer.MAX_VALUE);

            // 区块实体过多提示
            CHUNK_CHECK_NOTICE = SERVER_BUILDER
                    .comment("Show warning when too many entities in a chunk."
                            , "区块内实体过多时的是否进行提示。")
                    .define("chunkCheckNotice", true);

            // 区块实体过多清理模式
            CHUNK_CHECK_CLEAN_MODE = SERVER_BUILDER
                    .comment("The cleanup modes for detecting excessive entities in a chunk."
                            , "NONE: Do not perform cleanup"
                            , "DEFAULT: Clean up only non-whitelisted items and entities"
                            , "ALL: Clean up all items and entities in the chunk"
                            , "区块内实体过多时的清理模式。"
                            , "NONE：不进行清理"
                            , "DEFAULT：仅清理非白名单物品与实体"
                            , "ALL：清理区块内所有物品与实体")
                    .define("chunkCheckCleanMode", EnumChunkCheckMode.DEFAULT.name());

            // 使用以下物品捕获被清理的实体
            CATCH_ITEM = SERVER_BUILDER
                    .comment("The item used to capture the entity being cleaned up."
                            , "使用以下物品捕获被清理的实体。")
                    .defineListAllowEmpty("catchItem", new ArrayList<>() {{
                        add(BuiltInRegistries.ITEM.getKey(Items.SNOWBALL).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.GLASS_BOTTLE).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_13).toString());
                    }}, o -> o instanceof String);

            // 允许被清理的实体
            JUNK_ENTITY = SERVER_BUILDER
                    .comment("The entity that can be cleaned up."
                            , "允许被清理的实体。")
                    .defineListAllowEmpty("junkEntity", new ArrayList<>() {{
                                add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ARROW).toString());
                                add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.SPECTRAL_ARROW).toString());
                            }}, o -> o instanceof String
                    );

            // 清理时允许被捕获的实体
            CATCH_ENTITY = SERVER_BUILDER
                    .comment("The entity that can be captured when cleaned up."
                            , "清理时允许被捕获的实体。")
                    .defineListAllowEmpty("catchEntity", new ArrayList<>()
                            , o -> o instanceof String);

            // 物品清理白名单
            ITEM_WHITELIST = SERVER_BUILDER
                    .comment("The item whitelist for cleaning up items, the following items will not be cleaned or recycled."
                            , "物品清理白名单，以下物品不会被清理与回收。")
                    .defineListAllowEmpty("itemWhitelist", new ArrayList<>()
                            , o -> o instanceof String);

            // 白名单物品超过指定数量也进行清理
            ITEM_WHITE_BLACK_LIST_ENTITY_LIMIT = SERVER_BUILDER
                    .comment("Even items on the whitelist or not included in the blacklist will be cleared if their quantity on the server exceeds the specified limit."
                            , "即使是白名单内的物品，或不是黑名单中的物品，只要在服务器中数量超过指定上限，也会被清理。")
                    .defineInRange("itemWhiteBlackListEntityLimit", 250, 1, Integer.MAX_VALUE);

            // 物品清理黑名单
            ITEM_BLACKLIST = SERVER_BUILDER
                    .comment("The item blacklist for cleaning up items, if this list is not empty, only the following items will be cleaned and recycled, items outside the list will not be cleaned or recycled."
                            , "物品清理黑名单，若该名单不为空，则将只会清理并回收以下物品，名单外的物品将不会被清理与回收。")
                    .defineListAllowEmpty("itemBlacklist", new ArrayList<>()
                            , o -> o instanceof String);

            // 仅清理不回收的物品
            ITEM_REDLIST = SERVER_BUILDER
                    .comment("The item redlist for cleaning up items, the following items will only be cleaned and not recycled."
                            , "物品清理红名单，以下物品将只会被清理而不会被回收。")
                    .defineListAllowEmpty("itemRedlist", new ArrayList<>()
                            , o -> o instanceof String);

            // 是否允许玩家使用物品捕获实体
            ALLOW_CATCH_ENTITY = SERVER_BUILDER
                    .comment("Whether to allow players to use items to capture entities."
                            , "是否允许玩家使用物品捕获实体。")
                    .define("allowCatchEntity", false);

            // 仅清理掉落超过指定tick的物品
            SWEEP_ITEM_AGE = SERVER_BUILDER
                    .comment("Only clean up items that have been dropped for more than the specified ticks. Note: If a chunk is not loaded, dropped items will not tick, which may cause items to accumulate continuously."
                            , "仅清理掉落超过指定tick的物品。注意：若区块未被加载，掉落物的tick不会增加，从而导致物品越堆越多。")
                    .defineInRange("sweepItemDelay", 5, 0, 24 * 60 * 60 * 20);

            // 垃圾箱自清洁模式
            SELF_CLEAN_MODE = SERVER_BUILDER
                    .comment("The self-cleaning mode of the dustbin."
                            , "NONE: No self-cleaning mode enabled;"
                            , "SWEEP_CLEAR: Clear the dustbin before sweeping;"
                            , "SWEEP_DELETE: Randomly delete items in the dustbin during sweeping;"
                            , "SCHEDULED_CLEAR: Scheduled clearing of the dustbin;"
                            , "SCHEDULED_DELETE: Scheduled random deletion of items in the dustbin."
                            , "垃圾箱自清洁模式。"
                            , "NONE：不启用自清洁模式；"
                            , "SWEEP_CLEAR：在扫地前清空垃圾箱；"
                            , "SWEEP_DELETE：在扫地时随机删除垃圾箱内物品；"
                            , "SCHEDULED_CLEAR：定时清空垃圾箱；"
                            , "SCHEDULED_DELETE：定时随机删除垃圾箱内物品。")
                    .defineListAllowEmpty("selfCleanMode", new ArrayList<>() {{
                        add(EnumSelfCleanMode.NONE.name());
                    }}, o -> o instanceof String);

            // 垃圾箱溢出时的处理方式
            DUSTBIN_OVERFLOW_MODE = SERVER_BUILDER
                    .comment("The handling method when the dustbin overflows."
                            , "KEEP: Store to cache and fill in empty space in the dustbin when opening it;"
                            , "REMOVE: Remove the overflowing items;"
                            , "REPLACE: Randomly replace items in the dustbin with overflowing items."
                            , "垃圾箱溢出时的处理方式。"
                            , "KEEP：储存至缓存，并在打开垃圾箱时填充至垃圾箱的空位；"
                            , "REMOVE：移除溢出物品；"
                            , "REPLACE：将垃圾箱中的物品随机替换为溢出的物品。")
                    .define("dustbinOverflowMode", EnumOverflowMode.KEEP.name());

            // 贪婪模式
            GREEDY_MODE = SERVER_BUILDER
                    .comment("Enable this to treat all ItemEntity and its subclasses as dropped items; otherwise, only entities with EntityType.ITEM will be treated as dropped items."
                            , "开启后会将所有ItemEntity及其子类都视为掉落物，否则只会将EntityType为ITEM的视为掉落物。")
                    .define("greedyMode", false);

            // 实体清理NBT白名单
            ENTITY_NBT_WHITELIST_RAW = SERVER_BUILDER
                    .comment("The NBT whitelist for cleaning up entities, entities with the following NBT values will not be cleaned or recycled. Examples: [\"CreateData.Processing.Time -> value > 0\"]"
                            , "'->' left side is an NBT path expression, similar to JsonPath; the right side of '->' is an NBT value expression used to check if the condition is met. The 'value' keyword represents the NBT value and cannot be omitted."
                            , "Examples of NBT value expressions: value == 'string', sqrt(value) == 123, log(value) <= 2.5, pow(value, 2) != cos(value), sin(value) > 5, abs(value) >= 10"
                            , "实体清理NBT白名单，包含以下NBT值的实体不会被清理。例子：[\"CreateData.Processing.Time -> value > 0\"]"
                            , "'->'左边为NBT路径表达式，类似于JsonPath；'->'右边为NBT值表达式，用于判断是否满足条件，其中'value'代表NBT值，不可省略。"
                            , "NBT值表达式例子： value == '字符串'、sqrt(value) == 123、log(value) <= 2.5、pow(value, 2) != cos(value)、sin(value) > 5、abs(value) >= 10")
                    .defineList("entityNbtWhitelist", new ArrayList<>()
                            , o -> o instanceof String);

            // 实体清理NBT黑名单
            ENTITY_NBT_BLACKLIST_RAW = SERVER_BUILDER
                    .comment("The NBT blacklist for cleaning up entities, if this list is not empty, only the following NBT values will be cleaned and recycled, entities with NBT values outside the list will not be cleaned or recycled."
                            , "'->' left side is an NBT path expression, similar to JsonPath; the right side of '->' is an NBT value expression used to check if the condition is met. The 'value' keyword represents the NBT value and cannot be omitted."
                            , "Examples of NBT value expressions: value == 'string', sqrt(value) == 123, log(value) <= 2.5, pow(value, 2) != cos(value), sin(value) > 5, abs(value) >= 10"
                            , "实体清理NBT黑名单，若该名单不为空，则将只会清理并回收以下NBT值的实体，名单外的实体将不会被清理。"
                            , "'->'左边为NBT路径表达式，类似于JsonPath；'->'右边为NBT值表达式，用于判断是否满足条件，其中'value'代表NBT值，不可省略。"
                            , "NBT值表达式例子： value == '字符串'、sqrt(value) == 123、log(value) <= 2.5、pow(value, 2) != cos(value)、sin(value) > 5、abs(value) >= 10")
                    .defineList("entityNbtBlacklist", new ArrayList<>()
                            , o -> o instanceof String);

            // 黑白名单实体超过指定数量也进行清理
            NBT_WHITE_BLACK_LIST_ENTITY_LIMIT = SERVER_BUILDER
                    .comment("Even entities on the whitelist or not included in the blacklist will be cleared if their quantity on the server exceeds the specified limit."
                            , "即使是NBT白名单内的实体，或不是NBT黑名单中的实体，只要在服务器中数量超过指定上限，也会被清理。")
                    .defineInRange("nbtWhiteBlackListEntityLimit", 250, 1, Integer.MAX_VALUE);

            SERVER_BUILDER.pop();
        }


        // 定义指令权限
        {
            SERVER_BUILDER.comment("Command Permission", "指令权限").push("permission");

            PERMISSION_VIRTUAL_OP = SERVER_BUILDER
                    .comment("The permission level required to use the 'Set virtual permission' command, and also used as the permission level for modifying server configuration."
                            , "设置虚拟权限指令所需的权限等级，同时用于控制使用'修改服务器配置指令'的权限。")
                    .defineInRange("permissionVirtualOp", 4, 0, 4);

            PERMISSION_DUSTBIN_OPEN = SERVER_BUILDER
                    .comment("The permission level required to use the 'Open dustbin' command."
                            , "打开垃圾箱指令所需的权限等级。")
                    .defineInRange("permissionDustbinOpen", 0, 0, 4);

            PERMISSION_DUSTBIN_OPEN_OTHER = SERVER_BUILDER
                    .comment("The permission level required to use the 'Open dustbin for others' command."
                            , "为他人打开垃圾箱指令所需的权限等级。")
                    .defineInRange("permissionDustbinOpenOther", 2, 0, 4);

            PERMISSION_DUSTBIN_CLEAR = SERVER_BUILDER
                    .comment("The permission level required to use the 'Clear dustbin' command."
                            , "清空垃圾箱指令所需的权限等级。")
                    .defineInRange("permissionDustbinClear", 1, 0, 4);

            PERMISSION_DUSTBIN_DROP = SERVER_BUILDER
                    .comment("The permission level required to use the 'Drop dustbin items' command."
                            , "将垃圾箱物品掉落到世界指令所需的权限等级。")
                    .defineInRange("permissionDustbinDrop", 1, 0, 4);

            PERMISSION_CACHE_CLEAR = SERVER_BUILDER
                    .comment("The permission level required to use the 'Clear cache' command."
                            , "清空缓存指令所需的权限等级。")
                    .defineInRange("permissionCacheClear", 1, 0, 4);

            PERMISSION_CACHE_DROP = SERVER_BUILDER
                    .comment("The permission level required to use the 'Drop cache items' command."
                            , "将缓存内物品掉落至世界指令所需的权限等级。")
                    .defineInRange("permissionCacheDrop", 1, 0, 4);

            PERMISSION_SWEEP = SERVER_BUILDER
                    .comment("The permission level required to use the 'Trigger sweep' command."
                            , "触发扫地指令所需的权限等级。")
                    .defineInRange("permissionSweep", 0, 0, 4);

            PERMISSION_CLEAR_DROP = SERVER_BUILDER
                    .comment("The permission level required to use the 'Clear dropped items' command."
                            , "清除掉落物指令所需的权限等级。")
                    .defineInRange("permissionClearDrop", 1, 0, 4);

            PERMISSION_DELAY_SWEEP = SERVER_BUILDER
                    .comment("The permission level required to use the 'Delay sweep' command."
                            , "延迟本次清理指令所需的权限等级。")
                    .defineInRange("permissionDelaySweep", 1, 0, 4);

            SERVER_BUILDER.pop();
        }


        SERVER_CONFIG = SERVER_BUILDER.build();
    }


    /**
     * 重置服务器配置文件
     */
    public static void resetConfig() {
        HELP_HEADER.set("-----==== Aotake Sweep Help (%d/%d) ====-----");
        HELP_INFO_NUM_PER_PAGE.set(5);
        DEFAULT_LANGUAGE.set("en_us");
        SWEEP_INTERVAL.set(10 * 60 * 1000L);
        SELF_CLEAN_INTERVAL.set(60 * 60 * 1000L);
        CHUNK_CHECK_INTERVAL.set(5 * 1000L);
        CHUNK_CHECK_LIMIT.set(250);
        CHUNK_CHECK_RETAIN.set(125);
        CHUNK_CHECK_NOTICE.set(true);
        CHUNK_CHECK_CLEAN_MODE.set(EnumChunkCheckMode.DEFAULT.name());
        CATCH_ITEM.set(new ArrayList<>() {{
            add(BuiltInRegistries.ITEM.getKey(Items.SNOWBALL).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.GLASS_BOTTLE).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_13).toString());
        }});
        JUNK_ENTITY.set(new ArrayList<>() {{
            add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ARROW).toString());
            add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.SPECTRAL_ARROW).toString());
        }});
        CATCH_ENTITY.set(new ArrayList<>() {{
            add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.EXPERIENCE_ORB).toString());
        }});
        ITEM_WHITELIST.set(new ArrayList<>());
        ITEM_WHITE_BLACK_LIST_ENTITY_LIMIT.set(250);
        ITEM_BLACKLIST.set(new ArrayList<>());
        ITEM_REDLIST.set(new ArrayList<>());
        ALLOW_CATCH_ENTITY.set(false);
        SWEEP_ITEM_AGE.set(5);
        SELF_CLEAN_MODE.set(new ArrayList<>() {{
            add(EnumSelfCleanMode.NONE.name());
        }});
        DUSTBIN_OVERFLOW_MODE.set(EnumOverflowMode.KEEP.name());
        GREEDY_MODE.set(false);
        ENTITY_NBT_WHITELIST_RAW.set(new ArrayList<>() {{
            add("CreateData.Processing.Time -> value > 0");
        }});
        ENTITY_NBT_BLACKLIST_RAW.set(new ArrayList<>());
        NBT_WHITE_BLACK_LIST_ENTITY_LIMIT.set(250);

        PERMISSION_VIRTUAL_OP.set(4);
        PERMISSION_DUSTBIN_OPEN.set(0);
        PERMISSION_DUSTBIN_OPEN_OTHER.set(2);
        PERMISSION_DUSTBIN_CLEAR.set(1);
        PERMISSION_DUSTBIN_DROP.set(1);
        PERMISSION_CACHE_CLEAR.set(1);
        PERMISSION_CACHE_DROP.set(1);
        PERMISSION_SWEEP.set(0);
        PERMISSION_CLEAR_DROP.set(1);
        PERMISSION_DELAY_SWEEP.set(1);

        SERVER_CONFIG.save();
    }

    public static void resetConfigWithMode1() {
        resetConfig();

        SERVER_CONFIG.save();
    }

    public static void resetConfigWithMode2() {
        resetConfig();

        SERVER_CONFIG.save();
    }

    public static void bake() {
        ENTITY_NBT_WHITELIST = ENTITY_NBT_WHITELIST_RAW.get().stream()
                .map(s -> {
                    String[] parts = s.split("->", 2);
                    if (parts.length == 2) {
                        return new KeyValue<>(parts[0].trim(), parts[1].trim());
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ENTITY_NBT_BLACKLIST = ENTITY_NBT_BLACKLIST_RAW.get().stream()
                .map(s -> {
                    String[] parts = s.split("->", 2);
                    if (parts.length == 2) {
                        return new KeyValue<>(parts[0].trim(), parts[1].trim());
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
