package xin.vanilla.aotake.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import xin.vanilla.aotake.enums.EnumOverflowMode;
import xin.vanilla.aotake.enums.EnumSelfCleanMode;

import java.util.ArrayList;
import java.util.List;

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
    public static final ModConfigSpec.ConfigValue<EnumOverflowMode> DUSTBIN_OVERFLOW_MODE;

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
                    .comment("The interval for checking too many entities in chunks (in milliseconds), 0 to disable."
                            , "区块实体过多检测间隔(毫秒)，0为禁用。")
                    .defineInRange("chunkCheckInterval", 5 * 1000, 0L, 7 * 24 * 60 * 60 * 1000);

            // 区块实体过多检测阈值
            CHUNK_CHECK_LIMIT = SERVER_BUILDER
                    .comment("The threshold for checking too many entities in chunks."
                            , "区块实体过多检测阈值。")
                    .defineInRange("chunkCheckLimit", 250, 1, Integer.MAX_VALUE);

            // 使用以下物品捕获被清理的实体
            CATCH_ITEM = SERVER_BUILDER
                    .comment("The item used to capture the entity being cleaned up."
                            , "使用以下物品捕获被清理的实体。")
                    .defineListAllowEmpty("catchItem", new ArrayList<>() {{
                        add(BuiltInRegistries.ITEM.getKey(Items.SNOWBALL).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.GLASS_BOTTLE).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_13).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_CAT).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_BLOCKS).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_CHIRP).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_FAR).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_MALL).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_MELLOHI).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_STAL).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_STRAD).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_WARD).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_WAIT).toString());
                        add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_PIGSTEP).toString());
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
                    .defineListAllowEmpty("catchEntity", new ArrayList<>() {{
                        add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.EXPERIENCE_ORB).toString());
                    }}, o -> o instanceof String);

            // 物品清理白名单
            ITEM_WHITELIST = SERVER_BUILDER
                    .comment("The item whitelist for cleaning up items, the following items will not be cleaned or recycled."
                            , "物品清理白名单，以下物品不会被清理与回收。")
                    .defineListAllowEmpty("itemWhitelist", new ArrayList<>() {{
                    }}, o -> o instanceof String);

            // 物品清理黑名单
            ITEM_BLACKLIST = SERVER_BUILDER
                    .comment("The item blacklist for cleaning up items, if this list is not empty, only the following items will be cleaned and recycled, items outside the list will not be cleaned or recycled."
                            , "物品清理黑名单，若该名单不为空，则将只会清理并回收以下物品，名单外的物品将不会被清理与回收。")
                    .defineListAllowEmpty("itemBlacklist", new ArrayList<>() {{
                    }}, o -> o instanceof String);

            // 仅清理不回收的物品
            ITEM_REDLIST = SERVER_BUILDER
                    .comment("The item redlist for cleaning up items, the following items will only be cleaned and not recycled."
                            , "物品清理红名单，以下物品将只会被清理而不会被回收。")
                    .defineListAllowEmpty("itemRedlist", new ArrayList<>() {{
                    }}, o -> o instanceof String);

            // 是否允许玩家使用物品捕获实体
            ALLOW_CATCH_ENTITY = SERVER_BUILDER
                    .comment("Whether to allow players to use items to capture entities."
                            , "是否允许玩家使用物品捕获实体。")
                    .define("allowCatchEntity", false);

            // 仅清理掉落超过指定tick的物品
            SWEEP_ITEM_AGE = SERVER_BUILDER
                    .comment("Only clean up items that have been dropped for more than the specified ticks."
                            , "仅清理掉落超过指定tick的物品。")
                    .defineInRange("sweepItemDelay", 200, 0, 24 * 60 * 60 * 20);

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
                    .define("dustbinOverflowMode", EnumOverflowMode.KEEP);

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
        CATCH_ITEM.set(new ArrayList<>() {{
            add(BuiltInRegistries.ITEM.getKey(Items.SNOWBALL).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.GLASS_BOTTLE).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_13).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_CAT).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_BLOCKS).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_CHIRP).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_FAR).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_MALL).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_MELLOHI).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_STAL).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_STRAD).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_WARD).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_WAIT).toString());
            add(BuiltInRegistries.ITEM.getKey(Items.MUSIC_DISC_PIGSTEP).toString());
        }});
        JUNK_ENTITY.set(new ArrayList<>() {{
            add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ARROW).toString());
            add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.SPECTRAL_ARROW).toString());
        }});
        CATCH_ENTITY.set(new ArrayList<>() {{
            add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.EXPERIENCE_ORB).toString());
        }});
        ITEM_WHITELIST.set(new ArrayList<>());
        ITEM_BLACKLIST.set(new ArrayList<>());
        ITEM_REDLIST.set(new ArrayList<>());
        ALLOW_CATCH_ENTITY.set(false);
        SWEEP_ITEM_AGE.set(200);
        SELF_CLEAN_MODE.set(new ArrayList<>() {{
            add(EnumSelfCleanMode.NONE.name());
        }});
        DUSTBIN_OVERFLOW_MODE.set(EnumOverflowMode.KEEP);

        PERMISSION_VIRTUAL_OP.set(4);
        PERMISSION_DUSTBIN_OPEN.set(0);
        PERMISSION_DUSTBIN_CLEAR.set(1);
        PERMISSION_DUSTBIN_DROP.set(1);
        PERMISSION_CACHE_CLEAR.set(1);
        PERMISSION_CACHE_DROP.set(1);
        PERMISSION_SWEEP.set(0);
        PERMISSION_CLEAR_DROP.set(1);

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
}
