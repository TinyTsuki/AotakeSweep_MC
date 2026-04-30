package xin.vanilla.aotake.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.editable.EditableConfigRegistry;
import xin.vanilla.banira.editable.annotation.BaniraFieldMeta;

import java.util.*;

/**
 * 通用配置
 */
@Getter
@Setter
@Accessors(chain = true, fluent = true)
@Config(name = AotakeSweep.MODID + "-common")
public class CommonConfig implements ConfigData {

    private static final ConfigHolder<CommonConfig> HOLDER =
            AutoConfig.register(CommonConfig.class, Toml4jConfigSerializer::new);

    static {
        EditableConfigRegistry.registerAutoConfig(AotakeSweep.MODID, HOLDER, true);
    }

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    private BaseCategory base = new BaseCategory();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    private CommandCategory command = new CommandCategory();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    private ConciseCategory concise = new ConciseCategory();

    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip
    private PermissionCategory permission = new PermissionCategory();

    public CommonConfig() {
    }

    public static CommonConfig get() {
        return HOLDER.getConfig();
    }

    public static void save() {
        HOLDER.save();
    }

    public void validatePostLoad() {
        if (base == null) {
            base = new BaseCategory();
        }
        if (base.dustbin() == null) {
            base.dustbin(new DustbinSection());
        }
        if (base.sweep() == null) {
            base.sweep(new SweepSection());
        }
        if (base.safe() == null) {
            base.safe(new SafeSection());
        }
        if (base.common() == null) {
            base.common(new CommonHelpSection());
        }
        if (base.chunk() == null) {
            base.chunk(new ChunkSection());
        }
        if (base.entityCatch() == null) {
            base.entityCatch(new EntityCatchSection());
        }
        if (base.batch() == null) {
            base.batch(new BatchSection());
        }
        if (command == null) {
            command = new CommandCategory();
        }
        if (concise == null) {
            concise = new ConciseCategory();
        }
        if (permission == null) {
            permission = new PermissionCategory();
        }
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class BaseCategory {
        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip
        private DustbinSection dustbin = new DustbinSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip
        private SweepSection sweep = new SweepSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip
        private SafeSection safe = new SafeSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip
        private CommonHelpSection common = new CommonHelpSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip
        private ChunkSection chunk = new ChunkSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip
        private EntityCatchSection entityCatch = new EntityCatchSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip
        private BatchSection batch = new BatchSection();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class DustbinSection {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 16 * 16 * 16 * 16)
        private int dustbinPageLimit = 2;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int cacheLimit = 5000;

        @ConfigEntry.Gui.Tooltip
        @BaniraFieldMeta.BoundedLong(min = 0L, max = 7L * 24 * 60 * 60 * 1000)
        private long selfCleanInterval = 60L * 60 * 1000;

        @ConfigEntry.Gui.Tooltip
        private List<EnumSelfCleanMode> selfCleanMode = new ArrayList<>(Collections.singletonList(EnumSelfCleanMode.NONE));

        @ConfigEntry.Gui.Tooltip
        private EnumOverflowMode dustbinOverflowMode = EnumOverflowMode.KEEP;

        @ConfigEntry.Gui.Tooltip
        private boolean dustbinPersistent = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = -1, max = 3650)
        private int dropStatsFileLimit = 15;

        @ConfigEntry.Gui.Tooltip
        private List<String> dustbinBlockPositions = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip
        private EnumDustbinMode dustbinBlockMode = EnumDustbinMode.VIRTUAL;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class SweepSection {
        @ConfigEntry.Gui.Tooltip
        private boolean sweepWhenNoPlayer = false;

        @ConfigEntry.Gui.Tooltip
        private String sweepWarningContent = "";

        @ConfigEntry.Gui.Tooltip
        private String sweepWarningVoice = "";

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        private int sweepWarningVoiceVolume = 33;

        @ConfigEntry.Gui.Tooltip
        @BaniraFieldMeta.BoundedLong(max = 7L * 24 * 60 * 60 * 1000)
        private long sweepInterval = 10L * 60 * 1000;

        @ConfigEntry.Gui.Tooltip
        private List<String> entityList = defaultEntityList();

        @ConfigEntry.Gui.Tooltip
        private EnumListType entityListMode = EnumListType.BLACK;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int entityListLimit = 250;

        @ConfigEntry.Gui.Tooltip
        private List<String> entityRedlist = new ArrayList<>();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class SafeSection {
        @ConfigEntry.Gui.Tooltip
        private List<String> safeBlocks = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip
        private List<String> safeBlocksBelow = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip
        private List<String> safeBlocksAbove = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int safeBlocksEntityLimit = 250;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class CommonHelpSection {
        @ConfigEntry.Gui.Tooltip
        private String helpHeader = "-----==== Aotake Sweep Help (%d/%d) ====-----";

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 9999)
        private int helpInfoNumPerPage = 5;

        @ConfigEntry.Gui.Tooltip
        private String defaultLanguage = "en_us";
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ChunkSection {
        @ConfigEntry.Gui.Tooltip
        @BaniraFieldMeta.BoundedLong(min = 0L, max = 7L * 24 * 60 * 60 * 1000)
        private long chunkCheckInterval = 5L * 1000;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int chunkCheckLimit = 250;

        @ConfigEntry.Gui.Tooltip
        @BaniraFieldMeta.BoundedDouble(min = 0.0, max = 1.0)
        private double chunkCheckRetain = 0.5;

        @ConfigEntry.Gui.Tooltip
        private boolean chunkCheckNotice = true;

        @ConfigEntry.Gui.Tooltip
        private EnumChunkCheckMode chunkCheckMode = EnumChunkCheckMode.ADVANCED;

        @ConfigEntry.Gui.Tooltip
        private List<String> chunkCheckEntityList = defaultChunkCheckEntityList();

        @ConfigEntry.Gui.Tooltip
        private EnumListType chunkCheckEntityListMode = EnumListType.WHITE;

        @ConfigEntry.Gui.Tooltip
        private boolean chunkCheckOnlyNotice = false;

        @ConfigEntry.Gui.Tooltip
        private boolean chunkVaultEnabled = true;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 3650)
        private int chunkVaultRetentionDays = 2;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 24)
        private int chunkVaultBucketHours = 1;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class EntityCatchSection {
        @ConfigEntry.Gui.Tooltip
        private List<String> catchEntity = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip
        private boolean allowCatchEntity = false;

        @ConfigEntry.Gui.Tooltip
        private List<String> catchItem = defaultCatchItem();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class BatchSection {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int sweepEntityLimit = 500;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int sweepEntityInterval = 2;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int sweepBatchLimit = 10;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class CommandCategory {
        @ConfigEntry.Gui.Tooltip
        private String commandPrefix = AotakeSweep.DEFAULT_COMMAND_PREFIX;

        @ConfigEntry.Gui.Tooltip
        private String commandLanguage = "language";

        @ConfigEntry.Gui.Tooltip
        private String commandVirtualOp = "opv";

        @ConfigEntry.Gui.Tooltip
        private String commandDustbinOpen = "dustbin";

        @ConfigEntry.Gui.Tooltip
        private String commandDustbinClear = "cleardustbin";

        @ConfigEntry.Gui.Tooltip
        private String commandDustbinDrop = "dropdustbin";

        @ConfigEntry.Gui.Tooltip
        private String commandCacheClear = "clearcache";

        @ConfigEntry.Gui.Tooltip
        private String commandCacheDrop = "dropcache";

        @ConfigEntry.Gui.Tooltip
        private String commandSweep = "sweep";

        @ConfigEntry.Gui.Tooltip
        private String commandClearDrop = "killitem";

        @ConfigEntry.Gui.Tooltip
        private String commandDelaySweep = "delay";

        @ConfigEntry.Gui.Tooltip
        private String commandChunkVault = "chunkvault";
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ConciseCategory {
        @ConfigEntry.Gui.Tooltip
        private boolean conciseLanguage = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseVirtualOp = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseDustbinOpen = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseDustbinClear = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseDustbinDrop = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseCacheClear = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseCacheDrop = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseSweep = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseClearDrop = true;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseDelaySweep = false;

        @ConfigEntry.Gui.Tooltip
        private boolean conciseChunkVault = false;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class PermissionCategory {
        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionVirtualOp = 4;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinOpen = 0;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinOpenOther = 2;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinClear = 1;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinDrop = 1;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCacheClear = 1;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCacheDrop = 1;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionSweep = 0;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionClearDrop = 1;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDelaySweep = 1;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCatchPlayer = 3;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionChunkVault = 2;
    }

    private static List<String> defaultEntityList() {
        List<String> l = new ArrayList<>();
        l.add(Registry.ENTITY_TYPE.getKey(EntityType.ARROW).toString());
        l.add(Registry.ENTITY_TYPE.getKey(EntityType.SPECTRAL_ARROW).toString());
        l.add(Registry.ENTITY_TYPE.getKey(EntityType.EXPERIENCE_ORB).toString());
        l.add("tick, clazz, itemClazz, createProcessing = [CreateData.Processing.Time]"
                + " -> "
                + "tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)");
        return l;
    }

    private static List<String> defaultChunkCheckEntityList() {
        List<String> l = new ArrayList<>();
        l.add("customName, hasOwner, createProcessing = [CreateData.Processing.Time]"
                + " -> "
                + "customName != null || hasOwner || createProcessing > 0");
        return l;
    }

    private static List<String> defaultCatchItem() {
        return new ArrayList<>(Arrays.asList(
                Registry.ITEM.getKey(Items.SNOWBALL).toString(),
                Registry.ITEM.getKey(Items.GLASS_BOTTLE).toString(),
                Registry.ITEM.getKey(Items.MUSIC_DISC_13).toString()
        ));
    }

    public static void resetConfigWithMode0() {
        applyResetDefaults();
        save();
        Map<String, List<String>> group = WarningConfig.buildDefaultWarnGroup();
        List<Map<String, List<String>>> groups = new ArrayList<>();
        groups.add(group);
        WarningConfig.saveWarningContentGroups(groups);
        AotakeUtils.clearWarns();
    }

    public static void resetConfigWithMode1() {
        applyResetDefaults();
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
        applyResetDefaults();
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

    private static void applyResetDefaults() {
        CommonConfig c = get();
        c.base().dustbin()
                .dustbinPageLimit(2)
                .cacheLimit(5000)
                .selfCleanInterval(60L * 60 * 1000)
                .selfCleanMode(new ArrayList<>(Collections.singletonList(EnumSelfCleanMode.NONE)))
                .dustbinOverflowMode(EnumOverflowMode.KEEP)
                .dustbinPersistent(true)
                .dropStatsFileLimit(15)
                .dustbinBlockPositions(new ArrayList<>())
                .dustbinBlockMode(EnumDustbinMode.VIRTUAL);
        c.base().sweep()
                .sweepWhenNoPlayer(false)
                .sweepWarningContent("")
                .sweepWarningVoice("")
                .sweepWarningVoiceVolume(33)
                .sweepInterval(10L * 60 * 1000)
                .entityList(defaultEntityListReset())
                .entityListMode(EnumListType.WHITE)
                .entityListLimit(250)
                .entityRedlist(new ArrayList<>());
        c.base().safe()
                .safeBlocks(new ArrayList<>())
                .safeBlocksBelow(new ArrayList<>())
                .safeBlocksAbove(new ArrayList<>())
                .safeBlocksEntityLimit(250);
        c.base().common()
                .helpHeader("-----==== Aotake Sweep Help (%d/%d) ====-----")
                .helpInfoNumPerPage(5)
                .defaultLanguage("en_us");
        c.base().chunk()
                .chunkCheckInterval(5L * 1000)
                .chunkCheckLimit(250)
                .chunkCheckRetain(0.5)
                .chunkCheckNotice(true)
                .chunkCheckMode(EnumChunkCheckMode.ADVANCED)
                .chunkCheckEntityList(defaultChunkCheckEntityList())
                .chunkCheckEntityListMode(EnumListType.WHITE)
                .chunkCheckOnlyNotice(false)
                .chunkVaultEnabled(true)
                .chunkVaultRetentionDays(2)
                .chunkVaultBucketHours(1);
        c.base().entityCatch()
                .catchEntity(new ArrayList<>())
                .allowCatchEntity(false)
                .catchItem(defaultCatchItem());
        c.base().batch()
                .sweepEntityLimit(500)
                .sweepEntityInterval(2)
                .sweepBatchLimit(10);
        applyResetCommandConcisePermissionDefaults(c);
    }

    // region 与 resetConfigWithMode 配套的 command / concise / permission 默认值
    private static void applyResetCommandConcisePermissionDefaults(CommonConfig c) {
        c.command()
                .commandPrefix(AotakeSweep.DEFAULT_COMMAND_PREFIX)
                .commandLanguage("language")
                .commandVirtualOp("opv")
                .commandDustbinOpen("dustbin")
                .commandDustbinClear("cleardustbin")
                .commandDustbinDrop("dropdustbin")
                .commandCacheClear("clearcache")
                .commandCacheDrop("dropcache")
                .commandSweep("sweep")
                .commandClearDrop("killitem")
                .commandDelaySweep("delay")
                .commandChunkVault("chunkvault");
        c.concise()
                .conciseLanguage(false)
                .conciseVirtualOp(false)
                .conciseDustbinOpen(false)
                .conciseDustbinClear(false)
                .conciseDustbinDrop(false)
                .conciseCacheClear(false)
                .conciseCacheDrop(false)
                .conciseSweep(false)
                .conciseClearDrop(true)
                .conciseDelaySweep(false)
                .conciseChunkVault(false);
        c.permission()
                .permissionVirtualOp(4)
                .permissionDustbinOpen(0)
                .permissionDustbinOpenOther(2)
                .permissionDustbinClear(1)
                .permissionDustbinDrop(1)
                .permissionCacheClear(1)
                .permissionCacheDrop(1)
                .permissionSweep(0)
                .permissionClearDrop(1)
                .permissionDelaySweep(1)
                .permissionCatchPlayer(3)
                .permissionChunkVault(2);
    }
    // endregion 与 resetConfigWithMode 配套的 command / concise / permission 默认值

    private static List<String> defaultEntityListReset() {
        List<String> l = new ArrayList<>();
        l.add(Registry.ENTITY_TYPE.getKey(EntityType.ARROW).toString());
        l.add(Registry.ENTITY_TYPE.getKey(EntityType.SPECTRAL_ARROW).toString());
        l.add(Registry.ENTITY_TYPE.getKey(EntityType.EXPERIENCE_ORB).toString());
        l.add("tick, clazz, itemClazz, createProcessing = CreateData.Processing.Time"
                + " -> "
                + "tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)");
        return l;
    }
}
