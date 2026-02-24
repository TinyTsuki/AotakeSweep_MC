package xin.vanilla.aotake.config;

import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.core.Registry;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务器配置
 */
@Getter
@Setter
@Accessors(fluent = true)
@Config(name = AotakeSweep.MODID + "-server")
public class ServerConfig implements ConfigData {

    public static ServerConfig get() {
        return AutoConfig.getConfigHolder(ServerConfig.class).getConfig();
    }

    // region 基础设置

    /**
     * 帮助指令信息头部内容
     */
    @ConfigEntry.Gui.Tooltip
    private String helpHeader = "-----==== Aotake Sweep Help (%d/%d) ====-----";

    /**
     * 帮助信息每页显示的数量
     */
    @ConfigEntry.Gui.Tooltip
    // @ConfigEntry.BoundedDiscrete(min = 1, max = 9999)
    private int helpInfoNumPerPage = 5;

    /**
     * 服务器默认语言
     */
    @ConfigEntry.Gui.Tooltip
    private String defaultLanguage = "en_us";

    /**
     * 命令前缀
     */
    @ConfigEntry.Gui.Tooltip
    private String commandPrefix = "aotake";

    // endregion 基础设置

    // region 定时清理
    @ConfigEntry.Gui.CollapsibleObject
    private SweepConfig sweepConfig = new SweepConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class SweepConfig {
        /**
         * 扫地间隔(毫秒)
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 0, max = 7 * 24 * 60 * 60 * 1000)
        private long sweepInterval = 10 * 60 * 1000;

        /**
         * 服务器没人时是否打扫
         */
        @ConfigEntry.Gui.Tooltip
        private boolean sweepWhenNoPlayer = false;

        /**
         * 打扫前提示内容
         */
        @ConfigEntry.Gui.Tooltip
        private String sweepWarningContent = JsonUtils.GSON.toJson(new LinkedHashMap<String, String>() {{
            put("error", "§r§e香草酱坏掉了，这绝对不是香草酱的错！");
            put("fail", "§r§e香草酱什么也没吃到，失落地离开了。");
            put("success", "§r§e香草酱吃掉了[itemCount]个物品与[entityCount]个实体，并满意地离开了。");
            put("1", "§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
            put("2", "§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
            put("3", "§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
            put("4", "§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
            put("5", "§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！");
            put("10", "§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。");
            put("30", "§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。");
            put("60", "§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。");
        }}, new TypeToken<LinkedHashMap<String, String>>() {
        }.getType());

        /**
         * 打扫前提示语音
         */
        @ConfigEntry.Gui.Tooltip
        private String sweepWarningVoice = JsonUtils.GSON.toJson(new LinkedHashMap<String, String>() {{
            put("error", "aotake_sweep:error");
            put("fail", "aotake_sweep:fail");
            put("success", "aotake_sweep:success");
            put("5", "aotake_sweep:agog");
            put("30", "aotake_sweep:hungry");
        }}, new TypeToken<LinkedHashMap<String, String>>() {
        }.getType());

        /**
         * 打扫前提示语音音量
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        private int sweepWarningVoiceVolume = 33;

        /**
         * 实体名单
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> entityList = new ArrayList<>() {{
            add(Registry.ENTITY_TYPE.getKey(EntityType.ARROW).toString());
            add(Registry.ENTITY_TYPE.getKey(EntityType.SPECTRAL_ARROW).toString());
            add(Registry.ENTITY_TYPE.getKey(EntityType.EXPERIENCE_ORB).toString());
            add("tick, clazz, itemClazz, createProcessing = [CreateData.Processing.Time]" +
                    " -> " +
                    "tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)");
        }};

        /**
         * 实体名单应用模式
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        private EnumListType entityListMode = EnumListType.BLACK;

        /**
         * 实体名单超过指定数量也进行清理
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int entityListLimit = 250;

        /**
         * 仅清理不回收的实体
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> entityRedlist = new ArrayList<>();
    }
    // endregion 定时清理

    // region 区块实体过多检测
    @ConfigEntry.Gui.CollapsibleObject
    private ChunkCheckConfig chunkCheckConfig = new ChunkCheckConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class ChunkCheckConfig {
        /**
         * 区块实体过多检测间隔(毫秒)
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 0, max = 7 * 24 * 60 * 60 * 1000)
        private long chunkCheckInterval = 5 * 1000;

        /**
         * 区块实体过多检测阈值
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int chunkCheckLimit = 250;

        /**
         * 区块实体过多检测保留的实体比例
         */
        @ConfigEntry.Gui.Tooltip
        private double chunkCheckRetain = 0.5;


        /**
         * 区块实体过多提示
         */
        @ConfigEntry.Gui.Tooltip
        private boolean chunkCheckNotice = true;

        /**
         * 区块实体过多检测模式
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        private EnumChunkCheckMode chunkCheckMode = EnumChunkCheckMode.ADVANCED;

        /**
         * 区块检测实体名单
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> chunkCheckEntityList = new ArrayList<>() {{
            add("customName, hasOwner, createProcessing = [CreateData.Processing.Time]" +
                    " -> " +
                    "customName != null || hasOwner || createProcessing > 0");
        }};

        /**
         * 区块检测实体名单应用模式
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        private EnumListType chunkCheckEntityListMode = EnumListType.WHITE;

        /**
         * 只检测不清理
         */
        @ConfigEntry.Gui.Tooltip
        private boolean chunkCheckOnlyNotice = false;
    }
    // endregion 区块实体过多检测

    // region 实体捕获
    @ConfigEntry.Gui.CollapsibleObject
    private CatchConfig catchConfig = new CatchConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class CatchConfig {
        /**
         * 清理时允许被捕获的实体
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> catchEntity = new ArrayList<>();

        /**
         * 是否允许玩家使用物品捕获实体
         */
        @ConfigEntry.Gui.Tooltip
        private boolean allowCatchEntity = false;

        /**
         * 使用以下物品捕获被清理的实体
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> catchItem = new ArrayList<>() {{
            add(Registry.ITEM.getKey(Items.SNOWBALL).toString());
            add(Registry.ITEM.getKey(Items.GLASS_BOTTLE).toString());
            add(Registry.ITEM.getKey(Items.MUSIC_DISC_13).toString());
        }};
    }
    // endregion 实体捕获

    // region 垃圾箱
    @ConfigEntry.Gui.CollapsibleObject
    private DustbinConfig dustbinConfig = new DustbinConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class DustbinConfig {
        /**
         * 垃圾箱页数限制
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 0, max = 16 * 16 * 16 * 16)
        private int dustbinPageLimit = 1;

        /**
         * 缓存区物品限制
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int cacheLimit = 5000;

        /**
         * 自清洁间隔(毫秒)
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 0, max = 7 * 24 * 60 * 60 * 1000)
        private long selfCleanInterval = 60 * 60 * 1000;

        /**
         * 垃圾箱自清洁模式
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> selfCleanMode = new ArrayList<>() {{
            add(EnumSelfCleanMode.NONE.name());
        }};

        /**
         * 垃圾箱溢出时的处理方式
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        private EnumOverflowMode dustbinOverflowMode = EnumOverflowMode.KEEP;

        /**
         * 垃圾箱持久化
         */
        @ConfigEntry.Gui.Tooltip
        private boolean dustbinPersistent = true;

        /**
         * 掉落统计文件数量上限
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = -1, max = 3650)
        private int dropStatsFileLimit = 15;

        /**
         * 垃圾箱方块位置
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> dustbinBlockPositions = new ArrayList<>();

        /**
         * 垃圾箱应用模式
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
        private EnumDustbinMode dustbinMode = EnumDustbinMode.VIRTUAL;
    }
    // endregion 垃圾箱

    // region 分批次清理
    @ConfigEntry.Gui.CollapsibleObject
    private BatchConfig batchConfig = new BatchConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class BatchConfig {
        /**
         * 每tick清理实体上限
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int sweepEntityLimit = 500;

        /**
         * 每批次清理间隔tick
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int sweepEntityInterval = 2;

        /**
         * 每次清理的批次上限
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int sweepBatchLimit = 10;
    }
    // endregion 分批次清理

    // region 安全方块
    @ConfigEntry.Gui.CollapsibleObject
    private SafeConfig safeConfig = new SafeConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class SafeConfig {
        /**
         * 实体处于该方块中时不会被清理
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> safeBlocks = new ArrayList<>();

        /**
         * 实体处于该方块上时不会被清理
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> safeBlocksBelow = new ArrayList<>();

        /**
         * 实体处于该方块下时不会被清理
         */
        @ConfigEntry.Gui.Tooltip
        private List<String> safeBlocksAbove = new ArrayList<>();

        /**
         * 处于安全方块的实体超过指定数量也进行清理
         */
        @ConfigEntry.Gui.Tooltip
        // @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int safeBlocksEntityLimit = 250;
    }
    // endregion 安全方块

    // region 指令权限
    @ConfigEntry.Gui.CollapsibleObject
    private PermissionConfig permissionConfig = new PermissionConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class PermissionConfig {
        /**
         * 设置虚拟权限指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionVirtualOp = 4;

        /**
         * 打开垃圾箱指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinOpen = 0;

        /**
         * 为他人打开垃圾箱指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinOpenOther = 2;

        /**
         * 清空垃圾箱指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinClear = 1;

        /**
         * 将垃圾箱物品掉落到世界指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinDrop = 2;

        /**
         * 清空缓存指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCacheClear = 2;

        /**
         * 将缓存内物品掉落至世界指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCacheDrop = 2;

        /**
         * 触发扫地指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionSweep = 2;

        /**
         * 清除掉落物指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionClearDrop = 2;

        /**
         * 延迟本次清理指令权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDelaySweep = 2;

        /**
         * 捕获玩家权限
         */
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCatchPlayer = 3;
    }
    // endregion 指令权限

    // region 自定义指令
    @ConfigEntry.Gui.CollapsibleObject
    private CommandConfig commandConfig = new CommandConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class CommandConfig {
        /**
         * 设置语言
         */
        @ConfigEntry.Gui.Tooltip
        private String commandLanguage = "language";

        /**
         * 设置虚拟权限
         */
        @ConfigEntry.Gui.Tooltip
        private String commandVirtualOp = "opv";

        /**
         * 打开垃圾箱
         */
        @ConfigEntry.Gui.Tooltip
        private String commandDustbinOpen = "dustbin";

        /**
         * 清空垃圾箱
         */
        @ConfigEntry.Gui.Tooltip
        private String commandDustbinClear = "cleardustbin";

        /**
         * 将垃圾箱物品掉落到世界
         */
        @ConfigEntry.Gui.Tooltip
        private String commandDustbinDrop = "dropdustbin";

        /**
         * 清空缓存
         */
        @ConfigEntry.Gui.Tooltip
        private String commandCacheClear = "clearcache";

        /**
         * 将缓存内物品掉落至世界
         */
        @ConfigEntry.Gui.Tooltip
        private String commandCacheDrop = "dropcache";

        /**
         * 触发扫地
         */
        @ConfigEntry.Gui.Tooltip
        private String commandSweep = "sweep";

        /**
         * 清除掉落物
         */
        @ConfigEntry.Gui.Tooltip
        private String commandClearDrop = "killitem";

        /**
         * 延迟本次清理
         */
        @ConfigEntry.Gui.Tooltip
        private String commandDelaySweep = "delay";
    }
    // endregion 自定义指令

    // region 简化指令
    @ConfigEntry.Gui.CollapsibleObject
    private ConciseConfig conciseConfig = new ConciseConfig();

    @Getter
    @Setter
    @Accessors(fluent = true)
    public static class ConciseConfig {
        /**
         * 设置语言
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseLanguage = false;

        /**
         * 设置虚拟权限
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseVirtualOp = false;

        /**
         * 打开垃圾箱
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseDustbinOpen = false;

        /**
         * 清空垃圾箱
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseDustbinClear = false;

        /**
         * 将垃圾箱物品掉落到世界
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseDustbinDrop = false;

        /**
         * 清空缓存
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseCacheClear = false;

        /**
         * 将缓存内物品掉落至世界
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseCacheDrop = false;

        /**
         * 触发扫地
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseSweep = false;

        /**
         * 清除掉落物
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseClearDrop = true;

        /**
         * 延迟本次清理
         */
        @ConfigEntry.Gui.Tooltip
        private boolean conciseDelaySweep = false;
    }
    // endregion 简化指令


    public static void register() {
        AutoConfig.register(ServerConfig.class, Toml4jConfigSerializer::new);
        AutoConfig.getConfigHolder(ServerConfig.class)
                .registerSaveListener((holder, config) -> {
                    AotakeSweep.entityFilter().clear();
                    AotakeUtils.clearWarns();
                    return InteractionResult.SUCCESS;
                });
    }

    public static void save() {
        AutoConfig.getConfigHolder(ServerConfig.class).save();
    }

    /**
     * 重置服务器配置文件
     */
    private static void resetConfig() {
        AutoConfig.getConfigHolder(ServerConfig.class).resetToDefault();
    }

    /**
     * 重置服务器配置文件
     */
    public static void resetConfigWithMode0() {
        resetConfig();

        save();

        Map<String, List<String>> group = WarningConfig.buildDefaultWarnGroup();
        List<Map<String, List<String>>> groups = new ArrayList<>();
        groups.add(group);
        WarningConfig.saveWarningContentGroups(groups);
        AotakeUtils.clearWarns();
    }

    public static void resetConfigWithMode1() {
        resetConfig();

        save();

        Map<String, List<String>> group = new LinkedHashMap<>();
        group.put("error", AotakeUtils.singleList("清理过程中发生了异常，请检查服务器异常日志。"));
        group.put("fail", AotakeUtils.singleList("§r§e世界很干净。"));
        group.put("success", AotakeUtils.singleList("§r§e清理了[itemCount]个物品与[entityCount]个实体。"));
        group.put("1", AotakeUtils.singleList("§r§e清理将会在§r§e%s§r§e秒后开始！"));
        group.put("2", AotakeUtils.singleList("§r§e清理将会在§r§e%s§r§e秒后开始！"));
        group.put("3", AotakeUtils.singleList("§r§e清理将会在§r§e%s§r§e秒后开始！"));
        group.put("4", AotakeUtils.singleList("§r§e清理将会在§r§e%s§r§e秒后开始！"));
        group.put("5", AotakeUtils.singleList("§r§e清理将会在§r§e%s§r§e秒后开始！"));
        group.put("10", AotakeUtils.singleList("§r§e清理将会在§r§e%s§r§e秒后开始。"));
        group.put("30", AotakeUtils.singleList("§r§e清理将会在§r§e%s§r§e秒后开始。"));
        group.put("60", AotakeUtils.singleList("§r§e清理将会在§r§e%s§r§e秒后开始。"));
        List<Map<String, List<String>>> groups = new ArrayList<>();
        groups.add(group);
        WarningConfig.saveWarningContentGroups(groups);
        AotakeUtils.clearWarns();
    }

    public static void resetConfigWithMode2() {
        resetConfig();

        save();

        Map<String, List<String>> group = new LinkedHashMap<>();
        group.put("error", AotakeUtils.singleList("An error occurred while cleaning up, check the server logs for details."));
        group.put("fail", AotakeUtils.singleList("§r§eCleaned up nothing."));
        group.put("success", AotakeUtils.singleList("§r§eCleaned up [itemCount] items and [entityCount] entities."));
        group.put("1", AotakeUtils.singleList("§r§eThe cleanup will start in §r§e%s§r§e seconds!"));
        group.put("2", AotakeUtils.singleList("§r§eThe cleanup will start in §r§e%s§r§e seconds!"));
        group.put("3", AotakeUtils.singleList("§r§eThe cleanup will start in §r§e%s§r§e seconds!"));
        group.put("4", AotakeUtils.singleList("§r§eThe cleanup will start in §r§e%s§r§e seconds!"));
        group.put("5", AotakeUtils.singleList("§r§eThe cleanup will start in §r§e%s§r§e seconds!"));
        group.put("10", AotakeUtils.singleList("§r§eThe cleanup will start in §r§e%s§r§e seconds."));
        group.put("30", AotakeUtils.singleList("§r§eThe cleanup will start in §r§e%s§r§e seconds."));
        group.put("60", AotakeUtils.singleList("§r§eThe cleanup will start in §r§e%s§r§e seconds."));
        List<Map<String, List<String>>> groups = new ArrayList<>();
        groups.add(group);
        WarningConfig.saveWarningContentGroups(groups);
        AotakeUtils.clearWarns();
    }

    @Override
    public void validatePostLoad() {
        if (helpInfoNumPerPage < 1) helpInfoNumPerPage = 1;
        if (helpInfoNumPerPage > 9999) helpInfoNumPerPage = 9999;

        if (sweepConfig.sweepInterval() < 0) sweepConfig.sweepInterval(0);
        if (sweepConfig.sweepInterval() > 7 * 24 * 60 * 60 * 1000L) sweepConfig.sweepInterval(7 * 24 * 60 * 60 * 1000L);

        if (sweepConfig.entityListLimit() < 1) sweepConfig.entityListLimit(1);

        if (chunkCheckConfig.chunkCheckInterval() < 0) chunkCheckConfig.chunkCheckInterval(0);
        if (chunkCheckConfig.chunkCheckInterval() > 7 * 24 * 60 * 60 * 1000L)
            chunkCheckConfig.chunkCheckInterval(7 * 24 * 60 * 60 * 1000L);

        if (chunkCheckConfig.chunkCheckLimit() < 1) chunkCheckConfig.chunkCheckLimit(1);

        if (dustbinConfig.dustbinPageLimit() < 0) dustbinConfig.dustbinPageLimit(0);
        if (dustbinConfig.dustbinPageLimit() > 16 * 16 * 16 * 16) dustbinConfig.dustbinPageLimit(16 * 16 * 16 * 16);

        if (dustbinConfig.cacheLimit() < 1) dustbinConfig.cacheLimit(1);

        if (dustbinConfig.selfCleanInterval() < 0) dustbinConfig.selfCleanInterval(0);
        if (dustbinConfig.selfCleanInterval() > 7 * 24 * 60 * 60 * 1000L)
            dustbinConfig.selfCleanInterval(7 * 24 * 60 * 60 * 1000L);

        if (dustbinConfig.dropStatsFileLimit() < -1) dustbinConfig.dropStatsFileLimit(-1);
        if (dustbinConfig.dropStatsFileLimit() > 3650) dustbinConfig.dropStatsFileLimit(3650);


        if (batchConfig.sweepEntityLimit() < 1) batchConfig.sweepEntityLimit(1);
        if (batchConfig.sweepEntityInterval() < 1) batchConfig.sweepEntityInterval(1);
        if (batchConfig.sweepBatchLimit() < 1) batchConfig.sweepBatchLimit(1);

        if (safeConfig.safeBlocksEntityLimit() < 1) safeConfig.safeBlocksEntityLimit(1);
    }

}
