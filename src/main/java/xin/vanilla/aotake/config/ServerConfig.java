package xin.vanilla.aotake.config;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import xin.vanilla.aotake.enums.*;

import java.util.ArrayList;
import java.util.List;

public class ServerConfig {

    public static final ForgeConfigSpec SERVER_CONFIG;

    // region 基础设置

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


    /**
     * 扫地间隔(毫秒)
     */
    public static final ForgeConfigSpec.LongValue SWEEP_INTERVAL;


    /**
     * 实体名单
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_LIST;

    /**
     * 实体名单应用模式
     */
    public static final ForgeConfigSpec.ConfigValue<String> ENTITY_LIST_MODE;

    /**
     * 黑白名单实体超过指定数量也进行清理
     */
    public static final ForgeConfigSpec.IntValue ENTITY_LIST_LIMIT;

    /**
     * 仅清理不回收的实体
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_REDLIST;


    /**
     * 区块实体过多检测间隔(毫秒)
     */
    public static final ForgeConfigSpec.LongValue CHUNK_CHECK_INTERVAL;

    /**
     * 区块实体过多检测阈值
     */
    public static final ForgeConfigSpec.IntValue CHUNK_CHECK_LIMIT;

    /**
     * 区块实体清理时保留的实体比例
     */
    public static final ForgeConfigSpec.DoubleValue CHUNK_CHECK_RETAIN;

    /**
     * 区块实体过多提示
     */
    public static final ForgeConfigSpec.BooleanValue CHUNK_CHECK_NOTICE;

    /**
     * 区块实体过多检测模式
     */
    public static final ForgeConfigSpec.ConfigValue<String> CHUNK_CHECK_MODE;

    /**
     * 区块检测实体名单
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHUNK_CHECK_ENTITY_LIST;

    /**
     * 区块检测实体名单应用模式
     */
    public static final ForgeConfigSpec.ConfigValue<String> CHUNK_CHECK_ENTITY_LIST_MODE;

    /**
     * 只检测不清理
     */
    public static final ForgeConfigSpec.BooleanValue CHUNK_CHECK_ONLY_NOTICE;


    /**
     * 清理时允许被捕获的实体
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CATCH_ENTITY;

    /**
     * 是否允许玩家使用物品捕获实体
     */
    public static final ForgeConfigSpec.ConfigValue<Boolean> ALLOW_CATCH_ENTITY;

    /**
     * 使用以下物品捕获被清理的实体
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CATCH_ITEM;


    /**
     * 自清洁间隔
     */
    public static final ForgeConfigSpec.LongValue SELF_CLEAN_INTERVAL;

    /**
     * 垃圾箱自清洁方式
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SELF_CLEAN_MODE;

    /**
     * 垃圾箱溢出时的处理方式
     */
    public static final ForgeConfigSpec.ConfigValue<String> DUSTBIN_OVERFLOW_MODE;

    /**
     * 垃圾箱持久化
     */
    public static final ForgeConfigSpec.BooleanValue DUSTBIN_PERSISTENT;


    /**
     * 垃圾箱方块位置
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DUSTBIN_BLOCK_POSITIONS;
    /**
     * 垃圾箱应用模式
     */
    public static final ForgeConfigSpec.ConfigValue<String> DUSTBIN_MODE;


    /**
     * 每tick清理实体上限
     */
    public static final ForgeConfigSpec.IntValue SWEEP_ENTITY_LIMIT;

    /**
     * 每批次清理间隔tick
     */
    public static final ForgeConfigSpec.IntValue SWEEP_ENTITY_INTERVAL;

    /**
     * 每次清理的批次上限
     */
    public static final ForgeConfigSpec.IntValue SWEEP_BATCH_LIMIT;


    // endregion 基础设置


    // region 指令权限

    /**
     * 设置虚拟权限指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_VIRTUAL_OP;

    /**
     * 打开垃圾箱指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_DUSTBIN_OPEN;

    /**
     * 为他人打开垃圾箱指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_DUSTBIN_OPEN_OTHER;

    /**
     * 清空垃圾箱指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_DUSTBIN_CLEAR;

    /**
     * 将垃圾箱物品掉落到世界指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_DUSTBIN_DROP;

    /**
     * 清空缓存指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_CACHE_CLEAR;

    /**
     * 将缓存内物品掉落至世界指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_CACHE_DROP;

    /**
     * 触发扫地指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_SWEEP;

    /**
     * 清除掉落物指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_CLEAR_DROP;

    /**
     * 延迟本次清理指令所需的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_DELAY_SWEEP;

    /**
     * 使用物品捕获玩家的权限等级
     */
    public static final ForgeConfigSpec.IntValue PERMISSION_CATCH_PLAYER;

    // endregion 指令权限


    static {
        ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

        // 定义服务器基础设置
        {
            SERVER_BUILDER.comment("Base Settings", "基础设置").push("base");

            // 通用基础配置
            {
                SERVER_BUILDER.comment("Common Settings", "通用配置").push("common");

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

            // 定时清理
            {
                SERVER_BUILDER.comment("Regularly Sweep", "定时清理").push("sweep");

                // 扫地间隔(毫秒)
                SWEEP_INTERVAL = SERVER_BUILDER
                        .comment("The interval of sweeping (in milliseconds)."
                                , "扫地间隔(毫秒)。")
                        .defineInRange("sweepInterval", 10 * 60 * 1000, 0L, 7 * 24 * 60 * 60 * 1000);

                // 实体名单
                ENTITY_LIST = SERVER_BUILDER
                        .comment("The entity list, the following entities will be cleaned up according to the entityListMode."
                                , "实体名单，与配置 entityListMode 共同决定列表中的实体是否清理。")
                        .defineList("entityList", new ArrayList<String>() {{
                                    add(EntityType.ARROW.getRegistryName().toString());
                                    add(EntityType.SPECTRAL_ARROW.getRegistryName().toString());
                                    add(EntityType.EXPERIENCE_ORB.getRegistryName().toString());
                                    add("tick, clazz, itemClazz, createProcessing = [CreateData.Processing.Time]" +
                                            " -> " +
                                            "tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)");
                                }}, o -> o instanceof String
                        );

                // 实体名单应用模式
                ENTITY_LIST_MODE = SERVER_BUILDER
                        .comment("The application mode of the entity list:"
                                , "BLACK: Blacklist, only the entities listed will be cleaned up;"
                                , "WHITE: Whitelist, all entities except those listed will be cleaned up."
                                , "实体名单应用模式："
                                , "BLACK：黑名单，仅会清理列表中列出的实体；"
                                , "WHITE：白名单，将会清理列表中未列出的所有实体。")
                        .define("entityListMode", EnumListType.BLACK.name(), EnumListType::isValid);

                // 实体名单超过指定数量也进行清理
                ENTITY_LIST_LIMIT = SERVER_BUILDER
                        .comment("Even if an entity is on the whitelist or not on the blacklist, it will still be removed if the total number of that entity type on the server exceeds the specified limit."
                                , "即使是白名单内的实体，或不是黑名单中的实体，该实体类型只要在服务器中数量超过指定上限，也会被清理。")
                        .defineInRange("entityListLimit", 250, 1, Integer.MAX_VALUE);

                // 仅清理不回收的实体
                ENTITY_REDLIST = SERVER_BUILDER
                        .comment("The entity redlist for cleaning up items, the following items will only be cleaned and not recycled."
                                , "实体清理红名单，以下实体将只会被清理而不会被回收。")
                        .defineList("entityRedlist", new ArrayList<>()
                                , o -> o instanceof String);

                SERVER_BUILDER.pop();
            }

            // 区块实体过多检测
            {
                SERVER_BUILDER.comment("Chunk Check", "区块实体过多检测").push("chunk");

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

                // 区块实体过多检测保留的实体比例
                CHUNK_CHECK_RETAIN = SERVER_BUILDER
                        .comment("The ratio of entities to retain during entity overload cleanup in a chunk. "
                                , "By default, half of the detection threshold is retained to prevent excessive loss from clearing everything. "
                                , "The retention behavior is affected by chunkCheckMode."
                                , "区块实体过多检测清理时保留的实体比例，默认保留检测阈值的一半，避免全部清理而导致损失过大，保留方式受chunkCheckMode影响。")
                        .defineInRange("chunkCheckRetain", 0.5, 0, 1);

                // 区块实体过多提示
                CHUNK_CHECK_NOTICE = SERVER_BUILDER
                        .comment("Show warning when too many entities in a chunk."
                                , "区块内实体过多时的是否进行提示。")
                        .define("chunkCheckNotice", true);

                // 区块实体过多检测模式
                CHUNK_CHECK_MODE = SERVER_BUILDER
                        .comment("The check mode for detecting excessive entities in a chunk:"
                                , "DEFAULT: Cleanup is triggered when the total number of entities in the chunk exceeds the threshold;"
                                , "ADVANCED: Cleanup is triggered when a specific type of entity in the chunk exceeds the threshold."
                                , "区块内实体过多检测模式："
                                , "DEFAULT：区块内所有实体超过阈值触发清理；"
                                , "ADVANCED：区块内某个类型实体超过阈值触发清理。")
                        .define("chunkCheckMode", EnumChunkCheckMode.ADVANCED.name(), EnumChunkCheckMode::isValid);

                // 区块检测实体名单
                CHUNK_CHECK_ENTITY_LIST = SERVER_BUILDER
                        .comment("The entity list of chunk check, the following entities will be cleaned up according to the chunkCheckEntityListMode."
                                , "区块检测实体名单，与配置 chunkCheckEntityListMode 共同决定列表中的实体是否清理。")
                        .defineList("chunkCheckEntityList", new ArrayList<String>() {{
                                    add("customName, hasOwner, createProcessing = [CreateData.Processing.Time]" +
                                            " -> " +
                                            "customName != null || hasOwner || createProcessing > 0");
                                }}, o -> o instanceof String
                        );

                // 区块检测实体名单应用模式
                CHUNK_CHECK_ENTITY_LIST_MODE = SERVER_BUILDER
                        .comment("The application mode of the entity list of chunk check:"
                                , "BLACK: Blacklist, only the entities listed will be cleaned up;"
                                , "WHITE: Whitelist, all entities except those listed will be cleaned up."
                                , "区块检测实体名单应用模式："
                                , "BLACK：黑名单，仅会清理列表中列出的实体；"
                                , "WHITE：白名单，将会清理列表中未列出的所有实体。")
                        .define("chunkCheckEntityListMode", EnumListType.WHITE.name(), EnumListType::isValid);

                // 只检测不清理
                CHUNK_CHECK_ONLY_NOTICE = SERVER_BUILDER
                        .comment("Only notice when too many entities in a chunk."
                                , "区块内实体过多时的是否仅进行提示。")
                        .define("chunkCheckOnlyNotice", false);

                SERVER_BUILDER.pop();
            }

            // 实体捕获
            {
                SERVER_BUILDER.comment("Catch Entity", "实体捕获").push("catch");

                // 清理时允许被捕获的实体
                CATCH_ENTITY = SERVER_BUILDER
                        .comment("The entity that can be captured when cleaned up."
                                , "清理时允许被捕获的实体。")
                        .defineList("catchEntity", new ArrayList<>()
                                , o -> o instanceof String);

                // 是否允许玩家使用物品捕获实体
                ALLOW_CATCH_ENTITY = SERVER_BUILDER
                        .comment("Whether to allow players to use items to capture entities."
                                , "是否允许玩家使用物品捕获实体。")
                        .define("allowCatchEntity", false);

                // 使用以下物品捕获被清理的实体
                CATCH_ITEM = SERVER_BUILDER
                        .comment("The item used to capture the entity being cleaned up."
                                , "使用以下物品捕获被清理的实体。")
                        .defineList("catchItem", new ArrayList<String>() {{
                            add(Items.SNOWBALL.getRegistryName().toString());
                            add(Items.GLASS_BOTTLE.getRegistryName().toString());
                            add(Items.MUSIC_DISC_13.getRegistryName().toString());
                        }}, o -> o instanceof String);

                SERVER_BUILDER.pop();
            }

            // 垃圾箱
            {
                SERVER_BUILDER.comment("Dustbin", "垃圾箱").push("dustbin");

                // 自清洁间隔(毫秒)
                SELF_CLEAN_INTERVAL = SERVER_BUILDER
                        .comment("The interval of self-cleaning (in milliseconds)."
                                , "自清洁间隔(毫秒)。")
                        .defineInRange("selfCleanInterval", 60 * 60 * 1000, 0L, 7 * 24 * 60 * 60 * 1000);

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
                        .defineList("selfCleanMode", new ArrayList<String>() {{
                            add(EnumSelfCleanMode.NONE.name());
                        }}, EnumSelfCleanMode::isValid);

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
                        .define("dustbinOverflowMode", EnumOverflowMode.KEEP.name(), EnumOverflowMode::isValid);

                // 垃圾箱持久化
                DUSTBIN_PERSISTENT = SERVER_BUILDER
                        .comment("Whether to persistently store dustbin items to server files."
                                , "是否将垃圾箱物品持久化存储至服务器文件。若未启用持久化，服务器关闭后垃圾箱内物品将会丢失。")
                        .define("dustbinPersistent", true);

                // 垃圾箱方块位置
                DUSTBIN_BLOCK_POSITIONS = SERVER_BUILDER
                        .comment("The position of the dustbin block, format: dimension, x, y, z, side"
                                , "dimension: dimension ID, such as minecraft:overworld"
                                , "x, y, z: coordinates"
                                , "side: side of the block, can be empty. optional values: DOWN, UP, NORTH, SOUTH, WEST, EAST"
                                , "垃圾箱方块位置，格式： dimension, x, y, z, side"
                                , "dimension: 维度ID，如minecraft:overworld"
                                , "x, y, z: 坐标"
                                , "side: 方块面，可为空。可选值：DOWN、UP、NORTH、SOUTH、WEST、EAST")
                        .defineList("dustbinBlockPositions", new ArrayList<>(), o -> o instanceof String);

                // 垃圾箱应用模式
                DUSTBIN_MODE = SERVER_BUILDER
                        .comment("Dustbin Block Application Mode."
                                , "When using a dustbin block, you may encounter issues such as being unable to open the container via command or unable to empty the dustbin."
                                , "Please make sure the dustbin block is a valid container."
                                , "When using the open dustbin command, it will simulate the player right-clicking the center of the specified face of the block."
                                , "VIRTUAL: Only virtual dustbin;"
                                , "BLOCK: Only dustbin block;"
                                , "VIRTUAL_BLOCK: Virtual dustbin and dustbin block are enabled, and virtual dustbin is preferred;"
                                , "BLOCK_VIRTUAL: Dustbin block and virtual dustbin are enabled, and dustbin block is preferred."
                                , "垃圾箱方块应用模式。使用垃圾箱方块时，可能会出现无法使用指令打开容器页面、无法清空垃圾箱等情况。请确保垃圾箱方块是正常的容器，使用打开垃圾箱指令时会模拟玩家右键方块指定面的中间。"
                                , "VIRTUAL：仅虚拟垃圾箱；"
                                , "BLOCK：仅垃圾箱方块；"
                                , "VIRTUAL_BLOCK：虚拟垃圾箱和垃圾箱方块同时启用，且优先使用虚拟垃圾箱；"
                                , "BLOCK_VIRTUAL：垃圾箱方块和虚拟垃圾箱同时启用，且优先使用垃圾箱方块。")
                        .define("dustbinBlockMode", EnumDustbinMode.VIRTUAL.name(), EnumDustbinMode::isValid);


                SERVER_BUILDER.pop();
            }

            // 分批次清理
            {
                SERVER_BUILDER.comment("Batch", "批次").push("batch");

                // 每tick清理实体上限
                SWEEP_ENTITY_LIMIT = SERVER_BUILDER
                        .comment("The maximum number of entities to clean up per tick, prevents server lag caused by removing too many entities at once."
                                , "每tick清理实体上限，避免单次清理过多实体导致服务器卡顿。")
                        .defineInRange("sweepEntityLimit", 500, 1, Integer.MAX_VALUE);

                // 每批次清理间隔tick
                SWEEP_ENTITY_INTERVAL = SERVER_BUILDER
                        .comment("The interval between sweeps in ticks."
                                , "每批次清理间隔tick。")
                        .defineInRange("sweepEntityInterval", 2, 1, Integer.MAX_VALUE);

                // 每次清理的批次上限
                SWEEP_BATCH_LIMIT = SERVER_BUILDER
                        .comment("The maximum number of batches per cleanup, prevents excessive batching that could slow down the cleanup process."
                                , "This setting takes priority over sweepEntityLimit."
                                , "每次清理的批次上限，避免单次清理分批次过多导致清理过慢，该配置项优先级大于sweepEntityLimit。")
                        .defineInRange("sweepBatchLimit", 10, 1, Integer.MAX_VALUE);

                SERVER_BUILDER.pop();
            }

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

            PERMISSION_CATCH_PLAYER = SERVER_BUILDER
                    .comment("The permission level required to catch player."
                            , "延迟本次清理指令所需的权限等级。")
                    .defineInRange("permissionCatchPlayer", 3, 0, 4);

            SERVER_BUILDER.pop();
        }


        SERVER_CONFIG = SERVER_BUILDER.build();
    }


    public static void save() {
        SERVER_CONFIG.save();
    }

    /**
     * 重置服务器配置文件
     */
    private static void resetConfig() {
        HELP_HEADER.set("-----==== Aotake Sweep Help (%d/%d) ====-----");
        HELP_INFO_NUM_PER_PAGE.set(5);
        DEFAULT_LANGUAGE.set("en_us");

        SWEEP_INTERVAL.set(10 * 60 * 1000L);

        ENTITY_LIST.set(new ArrayList<String>() {{
            add(EntityType.ARROW.getRegistryName().toString());
            add(EntityType.SPECTRAL_ARROW.getRegistryName().toString());
            add(EntityType.EXPERIENCE_ORB.getRegistryName().toString());
            add("tick, clazz, itemClazz, createProcessing = CreateData.Processing.Time" +
                    " -> " +
                    "tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)");
        }});
        ENTITY_LIST_MODE.set(EnumListType.WHITE.name());
        ENTITY_LIST_LIMIT.set(250);
        ENTITY_REDLIST.set(new ArrayList<>());

        CHUNK_CHECK_INTERVAL.set(5 * 1000L);
        CHUNK_CHECK_LIMIT.set(250);
        CHUNK_CHECK_RETAIN.set(0.5);
        CHUNK_CHECK_NOTICE.set(true);
        CHUNK_CHECK_MODE.set(EnumChunkCheckMode.ADVANCED.name());
        CHUNK_CHECK_ENTITY_LIST.set(new ArrayList<String>() {{
            add("customName, hasOwner, createProcessing = CreateData.Processing.Time" +
                    " -> " +
                    "customName != null || hasOwner || createProcessing > 0");
        }});
        CHUNK_CHECK_ENTITY_LIST_MODE.set(EnumListType.WHITE.name());
        CHUNK_CHECK_ONLY_NOTICE.set(false);

        CATCH_ENTITY.set(new ArrayList<>());
        ALLOW_CATCH_ENTITY.set(false);
        CATCH_ITEM.set(new ArrayList<String>() {{
            add(Items.SNOWBALL.getRegistryName().toString());
            add(Items.GLASS_BOTTLE.getRegistryName().toString());
            add(Items.MUSIC_DISC_13.getRegistryName().toString());
        }});

        SELF_CLEAN_INTERVAL.set(60 * 60 * 1000L);
        SELF_CLEAN_MODE.set(new ArrayList<String>() {{
            add(EnumSelfCleanMode.NONE.name());
        }});
        DUSTBIN_OVERFLOW_MODE.set(EnumOverflowMode.KEEP.name());
        DUSTBIN_PERSISTENT.set(true);
        DUSTBIN_BLOCK_POSITIONS.set(new ArrayList<>());
        DUSTBIN_MODE.set(EnumDustbinMode.VIRTUAL.name());
        SWEEP_ENTITY_LIMIT.set(500);
        SWEEP_ENTITY_INTERVAL.set(2);
        SWEEP_BATCH_LIMIT.set(10);

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
        PERMISSION_CATCH_PLAYER.set(3);
    }

    public static void resetConfigWithMode0() {
        resetConfig();

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
