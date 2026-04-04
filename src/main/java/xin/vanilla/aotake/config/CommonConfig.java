package xin.vanilla.aotake.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraftforge.fml.config.ModConfig;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.access.CommonConfigAccess;
import xin.vanilla.aotake.enums.*;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.config.ConfigData;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ForgeConfigAdapter;
import xin.vanilla.banira.common.config.annotation.Config;
import xin.vanilla.banira.common.config.annotation.ConfigEntry;

import java.util.*;

/**
 * 通用配置
 */
@Config(name = AotakeSweep.MODID + "-common", type = ModConfig.Type.COMMON)
public class CommonConfig implements ConfigData {

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "基础：垃圾箱、扫地、安全方块、区块检测等", en_us = "Base: dustbin, sweep, safe blocks, chunk check, …")
    private BaseCategory base = new BaseCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "自定义指令名（勿加 /）", en_us = "Custom command names (no leading /)")
    private CommandCategory command = new CommandCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "无前缀简短指令开关", en_us = "Concise (no-prefix) command toggles")
    private ConciseCategory concise = new ConciseCategory();

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ConfigEntry.Gui.CollapsibleObject
    @ConfigEntry.Gui.Tooltip(zh_cn = "各指令所需权限等级", en_us = "Permission levels for commands")
    private PermissionCategory permission = new PermissionCategory();

    public CommonConfig() {
    }

    public static RootView get() {
        return CommonConfigAccess.root(ForgeConfigAdapter.getHolder(CommonConfig.class));
    }

    public static void save() {
        ConfigHolder h = ForgeConfigAdapter.getHolder(CommonConfig.class);
        if (h != null) {
            h.save();
        }
    }

    public interface RootView {
        BaseView base();

        CommandView command();

        ConciseView concise();

        PermissionView permission();

        ConfigHolder holder();
    }

    public interface BaseView {
        DustbinView dustbin();

        SweepView sweep();

        SafeView safe();

        CommonSettingsView common();

        ChunkView chunk();

        EntityCatchView entityCatch();

        BatchView batch();
    }

    public interface DustbinView {
        int dustbinPageLimit();

        DustbinView dustbinPageLimit(int value);

        int cacheLimit();

        DustbinView cacheLimit(int value);

        long selfCleanInterval();

        DustbinView selfCleanInterval(long value);

        List<String> selfCleanMode();

        DustbinView selfCleanMode(List<String> value);

        String dustbinOverflowMode();

        DustbinView dustbinOverflowMode(String value);

        boolean dustbinPersistent();

        DustbinView dustbinPersistent(boolean value);

        int dropStatsFileLimit();

        DustbinView dropStatsFileLimit(int value);

        List<String> dustbinBlockPositions();

        DustbinView dustbinBlockPositions(List<String> value);

        String dustbinBlockMode();

        DustbinView dustbinBlockMode(String value);
    }

    public interface SweepView {
        boolean sweepWhenNoPlayer();

        SweepView sweepWhenNoPlayer(boolean value);

        String sweepWarningContent();

        SweepView sweepWarningContent(String value);

        String sweepWarningVoice();

        SweepView sweepWarningVoice(String value);

        int sweepWarningVoiceVolume();

        SweepView sweepWarningVoiceVolume(int value);

        long sweepInterval();

        SweepView sweepInterval(long value);

        List<String> entityList();

        SweepView entityList(List<String> value);

        String entityListMode();

        SweepView entityListMode(String value);

        int entityListLimit();

        SweepView entityListLimit(int value);

        List<String> entityRedlist();

        SweepView entityRedlist(List<String> value);
    }

    public interface SafeView {
        List<String> safeBlocks();

        SafeView safeBlocks(List<String> value);

        List<String> safeBlocksBelow();

        SafeView safeBlocksBelow(List<String> value);

        List<String> safeBlocksAbove();

        SafeView safeBlocksAbove(List<String> value);

        int safeBlocksEntityLimit();

        SafeView safeBlocksEntityLimit(int value);
    }

    public interface CommonSettingsView {
        String helpHeader();

        CommonSettingsView helpHeader(String value);

        int helpInfoNumPerPage();

        CommonSettingsView helpInfoNumPerPage(int value);

        String defaultLanguage();

        CommonSettingsView defaultLanguage(String value);
    }

    public interface ChunkView {
        long chunkCheckInterval();

        ChunkView chunkCheckInterval(long value);

        int chunkCheckLimit();

        ChunkView chunkCheckLimit(int value);

        double chunkCheckRetain();

        ChunkView chunkCheckRetain(double value);

        boolean chunkCheckNotice();

        ChunkView chunkCheckNotice(boolean value);

        String chunkCheckMode();

        ChunkView chunkCheckMode(String value);

        List<String> chunkCheckEntityList();

        ChunkView chunkCheckEntityList(List<String> value);

        String chunkCheckEntityListMode();

        ChunkView chunkCheckEntityListMode(String value);

        boolean chunkCheckOnlyNotice();

        ChunkView chunkCheckOnlyNotice(boolean value);
    }

    public interface EntityCatchView {
        List<String> catchEntity();

        EntityCatchView catchEntity(List<String> value);

        boolean allowCatchEntity();

        EntityCatchView allowCatchEntity(boolean value);

        List<String> catchItem();

        EntityCatchView catchItem(List<String> value);
    }

    public interface BatchView {
        int sweepEntityLimit();

        BatchView sweepEntityLimit(int value);

        int sweepEntityInterval();

        BatchView sweepEntityInterval(int value);

        int sweepBatchLimit();

        BatchView sweepBatchLimit(int value);
    }

    public interface CommandView {
        String commandPrefix();

        CommandView commandPrefix(String value);

        String commandLanguage();

        CommandView commandLanguage(String value);

        String commandVirtualOp();

        CommandView commandVirtualOp(String value);

        String commandDustbinOpen();

        CommandView commandDustbinOpen(String value);

        String commandDustbinClear();

        CommandView commandDustbinClear(String value);

        String commandDustbinDrop();

        CommandView commandDustbinDrop(String value);

        String commandCacheClear();

        CommandView commandCacheClear(String value);

        String commandCacheDrop();

        CommandView commandCacheDrop(String value);

        String commandSweep();

        CommandView commandSweep(String value);

        String commandClearDrop();

        CommandView commandClearDrop(String value);

        String commandDelaySweep();

        CommandView commandDelaySweep(String value);
    }

    public interface ConciseView {
        boolean conciseLanguage();

        ConciseView conciseLanguage(boolean value);

        boolean conciseVirtualOp();

        ConciseView conciseVirtualOp(boolean value);

        boolean conciseDustbinOpen();

        ConciseView conciseDustbinOpen(boolean value);

        boolean conciseDustbinClear();

        ConciseView conciseDustbinClear(boolean value);

        boolean conciseDustbinDrop();

        ConciseView conciseDustbinDrop(boolean value);

        boolean conciseCacheClear();

        ConciseView conciseCacheClear(boolean value);

        boolean conciseCacheDrop();

        ConciseView conciseCacheDrop(boolean value);

        boolean conciseSweep();

        ConciseView conciseSweep(boolean value);

        boolean conciseClearDrop();

        ConciseView conciseClearDrop(boolean value);

        boolean conciseDelaySweep();

        ConciseView conciseDelaySweep(boolean value);
    }

    public interface PermissionView {
        int permissionVirtualOp();

        PermissionView permissionVirtualOp(int value);

        int permissionDustbinOpen();

        PermissionView permissionDustbinOpen(int value);

        int permissionDustbinOpenOther();

        PermissionView permissionDustbinOpenOther(int value);

        int permissionDustbinClear();

        PermissionView permissionDustbinClear(int value);

        int permissionDustbinDrop();

        PermissionView permissionDustbinDrop(int value);

        int permissionCacheClear();

        PermissionView permissionCacheClear(int value);

        int permissionCacheDrop();

        PermissionView permissionCacheDrop(int value);

        int permissionSweep();

        PermissionView permissionSweep(int value);

        int permissionClearDrop();

        PermissionView permissionClearDrop(int value);

        int permissionDelaySweep();

        PermissionView permissionDelaySweep(int value);

        int permissionCatchPlayer();

        PermissionView permissionCatchPlayer(int value);
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class BaseCategory {
        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "虚拟垃圾箱页数、缓存上限、自清洁、溢出、持久化、方块垃圾箱等。",
                en_us = "Virtual dustbin pages, cache cap, self-clean, overflow, persistence, block dustbins.")
        private DustbinSection dustbin = new DustbinSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "定时扫地间隔、实体名单、提示音量等（文案/语音已迁移至 warning JSON 的项仅作兼容）。",
                en_us = "Sweep interval, entity lists, warning volume (legacy text/voice fields kept for compatibility).")
        private SweepSection sweep = new SweepSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "安全方块：处于其内/上/下的实体可豁免清理及数量上限。",
                en_us = "Safe blocks: entities inside/on/below may be exempt; per-chunk cap override.")
        private SafeSection safe = new SafeSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "帮助分页标题格式、每页条数、服务器默认语言代码。", en_us = "Help header format, lines per page, default language code.")
        private CommonHelpSection common = new CommonHelpSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "单区块实体过多检测：间隔、阈值、保留比例、模式与名单。", en_us = "Per-chunk entity overload: interval, threshold, retain ratio, mode, lists.")
        private ChunkSection chunk = new ChunkSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "实体捕获（原 TOML 节 catch；现路径 base.entityCatch）。",
                en_us = "Entity catch (formerly toml section catch; path base.entityCatch).")
        private EntityCatchSection entityCatch = new EntityCatchSection();

        @ConfigEntry.Gui.CollapsibleObject
        @ConfigEntry.Gui.Tooltip(zh_cn = "分批次清理：每 tick 上限、批次间隔与批次数量上限。", en_us = "Batched cleanup: per-tick limit, tick gap between batches, max batches.")
        private BatchSection batch = new BatchSection();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class DustbinSection {
        @ConfigEntry.Gui.Tooltip(zh_cn = "虚拟垃圾箱最大页数。", en_us = "Maximum pages for the virtual dustbin.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 16 * 16 * 16 * 16)
        private int dustbinPageLimit = 2;

        @ConfigEntry.Gui.Tooltip(zh_cn = "缓存区（溢出暂存等）最大物品数量。", en_us = "Max items in the overflow/cache buffer.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int cacheLimit = 5000;

        @ConfigEntry.Gui.Tooltip(zh_cn = "垃圾箱自清洁间隔（毫秒）。", en_us = "Self-clean interval for the dustbin (ms).")
        @ConfigEntry.BoundedLong(min = 0L, max = 7L * 24 * 60 * 60 * 1000)
        private long selfCleanInterval = 60L * 60 * 1000;

        @ConfigEntry.Gui.Tooltip(zh_cn = "自清洁模式列表：NONE、SWEEP_CLEAR、SWEEP_DELETE、SCHEDULED_CLEAR、SCHEDULED_DELETE。",
                en_us = "Self-clean modes: NONE, SWEEP_CLEAR, SWEEP_DELETE, SCHEDULED_CLEAR, SCHEDULED_DELETE.")
        private List<String> selfCleanMode = new ArrayList<>(Arrays.asList(EnumSelfCleanMode.NONE.name()));

        @ConfigEntry.Gui.Tooltip(zh_cn = "垃圾箱满溢时：KEEP / REMOVE / REPLACE。", en_us = "When dustbin overflows: KEEP, REMOVE, or REPLACE.")
        private String dustbinOverflowMode = EnumOverflowMode.KEEP.name();

        @ConfigEntry.Gui.Tooltip(zh_cn = "是否将垃圾箱持久化到磁盘（关闭则关服后丢失）。", en_us = "Persist dustbin to disk (off = lost after restart).")
        private boolean dustbinPersistent = true;

        @ConfigEntry.Gui.Tooltip(zh_cn = "掉落统计按日期保留文件数。-1 禁用，0 不限制。", en_us = "Max drop-stat files by date; -1 off, 0 unlimited.")
        @ConfigEntry.BoundedDiscrete(min = -1, max = 3650)
        private int dropStatsFileLimit = 15;

        @ConfigEntry.Gui.Tooltip(zh_cn = "方块垃圾箱坐标列表：dimension, x, y, z, side（可选 DOWN/UP/NORTH/SOUTH/WEST/EAST）。",
                en_us = "Block dustbin positions: dimension, x, y, z, optional side face.")
        private List<String> dustbinBlockPositions = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip(zh_cn = "垃圾箱模式：VIRTUAL / BLOCK / VIRTUAL_BLOCK / BLOCK_VIRTUAL。", en_us = "Dustbin mode: VIRTUAL, BLOCK, VIRTUAL_BLOCK, BLOCK_VIRTUAL.")
        private String dustbinBlockMode = EnumDustbinMode.VIRTUAL.name();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class SweepSection {
        @ConfigEntry.Gui.Tooltip(zh_cn = "服务器无玩家时是否仍执行自动扫地。", en_us = "Run auto-sweep when no players are online.")
        private boolean sweepWhenNoPlayer = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "已废弃：请改用 config/aotake_sweep-warning.json。", en_us = "Deprecated; use config/aotake_sweep-warning.json.")
        private String sweepWarningContent = "";

        @ConfigEntry.Gui.Tooltip(zh_cn = "已废弃：请改用 config/aotake_sweep-warning.json。", en_us = "Deprecated; use config/aotake_sweep-warning.json.")
        private String sweepWarningVoice = "";

        @ConfigEntry.Gui.Tooltip(zh_cn = "提示音效音量 0–100。", en_us = "Warning sound volume 0–100.")
        @ConfigEntry.BoundedDiscrete(max = 100)
        private int sweepWarningVoiceVolume = 33;

        @ConfigEntry.Gui.Tooltip(zh_cn = "自动扫地周期间隔（毫秒）。", en_us = "Auto-sweep interval (ms).")
        @ConfigEntry.BoundedLong(max = 7L * 24 * 60 * 60 * 1000)
        private long sweepInterval = 10L * 60 * 1000;

        @ConfigEntry.Gui.Tooltip(zh_cn = "实体过滤规则/ID 列表（与 entityListMode 配合）。", en_us = "Entity filter rules / ids (used with entityListMode).")
        private List<String> entityList = defaultEntityList();

        @ConfigEntry.Gui.Tooltip(zh_cn = "名单模式：BLACK 仅清列表内；WHITE 清列表外。", en_us = "List mode: BLACK clean listed only; WHITE clean unlisted.")
        private String entityListMode = EnumListType.BLACK.name();

        @ConfigEntry.Gui.Tooltip(zh_cn = "单类型实体全服超过该数量时仍强制清理。", en_us = "Global per-type cap; still clean when count exceeds this.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int entityListLimit = 250;

        @ConfigEntry.Gui.Tooltip(zh_cn = "红名单：仅清理、不回收的实体规则/ID。", en_us = "Redlist: entities to clean without recycling.")
        private List<String> entityRedlist = new ArrayList<>();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class SafeSection {
        @ConfigEntry.Gui.Tooltip(zh_cn = "实体处于这些方块「内部」时不清理（支持带状态，如 minecraft:lava[level=0]）。",
                en_us = "Skip cleanup when entity is inside these blocks (supports block states).")
        private List<String> safeBlocks = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip(zh_cn = "实体站在这些方块上时不清理。", en_us = "Skip cleanup when standing on these blocks.")
        private List<String> safeBlocksBelow = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip(zh_cn = "实体位于这些方块下方时不清理。", en_us = "Skip cleanup when below these blocks.")
        private List<String> safeBlocksAbove = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip(zh_cn = "即使在安全方块内，单区块该实体数超过此值仍会清理。", en_us = "Even in safe blocks, clean if per-chunk count exceeds this.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int safeBlocksEntityLimit = 250;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class CommonHelpSection {
        @ConfigEntry.Gui.Tooltip(zh_cn = "帮助指令分页标题，%d/%d 为当前页/总页。", en_us = "Help header format string; %d/%d = page/total.")
        private String helpHeader = "-----==== Aotake Sweep Help (%d/%d) ====-----";

        @ConfigEntry.Gui.Tooltip(zh_cn = "帮助每页显示的条目数。", en_us = "Help lines per page.")
        @ConfigEntry.BoundedDiscrete(min = 1, max = 9999)
        private int helpInfoNumPerPage = 5;

        @ConfigEntry.Gui.Tooltip(zh_cn = "服务器默认语言代码（如 en_us、zh_cn）。", en_us = "Server default language code (e.g. en_us, zh_cn).")
        private String defaultLanguage = "en_us";
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ChunkSection {
        @ConfigEntry.Gui.Tooltip(zh_cn = "区块实体检测间隔（毫秒），0 关闭。", en_us = "Chunk entity check interval (ms); 0 disables.")
        @ConfigEntry.BoundedLong(min = 0L, max = 7L * 24 * 60 * 60 * 1000)
        private long chunkCheckInterval = 5L * 1000;

        @ConfigEntry.Gui.Tooltip(zh_cn = "触发清理的实体数量阈值。", en_us = "Entity count threshold to trigger cleanup.")
        @ConfigEntry.BoundedDiscrete(min = 1, max = Integer.MAX_VALUE)
        private int chunkCheckLimit = 250;

        @ConfigEntry.Gui.Tooltip(zh_cn = "清理后保留实体比例（0–1），具体行为受 chunkCheckMode 影响。",
                en_us = "Fraction of entities to retain after cleanup (0–1); interacts with chunkCheckMode.")
        @ConfigEntry.BoundedDouble(min = 0.0, max = 1.0)
        private double chunkCheckRetain = 0.5;

        @ConfigEntry.Gui.Tooltip(zh_cn = "区块实体过多时是否向玩家发提示。", en_us = "Broadcast warning when chunk is overloaded.")
        private boolean chunkCheckNotice = true;

        @ConfigEntry.Gui.Tooltip(zh_cn = "DEFAULT：总实体超阈值；ADVANCED：单类型超阈值。", en_us = "DEFAULT: total count; ADVANCED: per-type count.")
        private String chunkCheckMode = EnumChunkCheckMode.ADVANCED.name();

        @ConfigEntry.Gui.Tooltip(zh_cn = "区块检测用的实体规则/名单（与 chunkCheckEntityListMode 配合）。",
                en_us = "Entity rules/list for chunk check (with chunkCheckEntityListMode).")
        private List<String> chunkCheckEntityList = defaultChunkCheckEntityList();

        @ConfigEntry.Gui.Tooltip(zh_cn = "区块名单模式：BLACK / WHITE。", en_us = "Chunk list mode: BLACK or WHITE.")
        private String chunkCheckEntityListMode = EnumListType.WHITE.name();

        @ConfigEntry.Gui.Tooltip(zh_cn = "仅提示、不执行清理。", en_us = "Notice only; do not clean.")
        private boolean chunkCheckOnlyNotice = false;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class EntityCatchSection {
        @ConfigEntry.Gui.Tooltip(zh_cn = "允许在清理时被「捕获」的实体规则/ID。", en_us = "Entities that may be caught during cleanup.")
        private List<String> catchEntity = new ArrayList<>();

        @ConfigEntry.Gui.Tooltip(zh_cn = "是否允许玩家用物品交互捕获实体。", en_us = "Allow players to catch entities using items.")
        private boolean allowCatchEntity = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "可用作捕获工具的物品 ID 列表。", en_us = "Item ids usable as catch tools.")
        private List<String> catchItem = defaultCatchItem();
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class BatchSection {
        @ConfigEntry.Gui.Tooltip(zh_cn = "每个游戏刻最多移除的实体数，防止卡顿。", en_us = "Max entities removed per tick to reduce lag.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int sweepEntityLimit = 500;

        @ConfigEntry.Gui.Tooltip(zh_cn = "批次之间的间隔（tick）。", en_us = "Ticks between batches.")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int sweepEntityInterval = 2;

        @ConfigEntry.Gui.Tooltip(zh_cn = "单次清理最多跑几批（优先级高于每刻上限）。", en_us = "Max batches per cleanup run (overrides per-tick cap).")
        @ConfigEntry.BoundedDiscrete(min = 1)
        private int sweepBatchLimit = 10;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class CommandCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "主指令前缀，仅字母与下划线。", en_us = "Root command prefix; letters and underscores only.")
        private String commandPrefix = AotakeSweep.DEFAULT_COMMAND_PREFIX;

        @ConfigEntry.Gui.Tooltip(zh_cn = "设置语言子命令名。", en_us = "Subcommand name for /prefix language.")
        private String commandLanguage = "language";

        @ConfigEntry.Gui.Tooltip(zh_cn = "虚拟权限子命令名。", en_us = "Subcommand name for virtual OP.")
        private String commandVirtualOp = "opv";

        @ConfigEntry.Gui.Tooltip(zh_cn = "打开垃圾箱子命令名。", en_us = "Subcommand to open dustbin.")
        private String commandDustbinOpen = "dustbin";

        @ConfigEntry.Gui.Tooltip(zh_cn = "清空垃圾箱子命令名。", en_us = "Subcommand to clear dustbin.")
        private String commandDustbinClear = "cleardustbin";

        @ConfigEntry.Gui.Tooltip(zh_cn = "掉落垃圾箱物品子命令名。", en_us = "Subcommand to drop dustbin items.")
        private String commandDustbinDrop = "dropdustbin";

        @ConfigEntry.Gui.Tooltip(zh_cn = "清空缓存子命令名。", en_us = "Subcommand to clear cache.")
        private String commandCacheClear = "clearcache";

        @ConfigEntry.Gui.Tooltip(zh_cn = "掉落缓存物品子命令名。", en_us = "Subcommand to drop cache items.")
        private String commandCacheDrop = "dropcache";

        @ConfigEntry.Gui.Tooltip(zh_cn = "手动触发扫底子命令名。", en_us = "Subcommand to trigger sweep.")
        private String commandSweep = "sweep";

        @ConfigEntry.Gui.Tooltip(zh_cn = "清除掉落物子命令名。", en_us = "Subcommand to clear ground items.")
        private String commandClearDrop = "killitem";

        @ConfigEntry.Gui.Tooltip(zh_cn = "延迟本次清理子命令名。", en_us = "Subcommand to delay next sweep.")
        private String commandDelaySweep = "delay";
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class ConciseCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀执行「设置语言」。", en_us = "Allow no-prefix alias for language command.")
        private boolean conciseLanguage = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀执行「虚拟权限」。", en_us = "Allow no-prefix alias for virtual OP.")
        private boolean conciseVirtualOp = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀打开垃圾箱。", en_us = "Allow no-prefix open dustbin.")
        private boolean conciseDustbinOpen = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀清空垃圾箱。", en_us = "Allow no-prefix clear dustbin.")
        private boolean conciseDustbinClear = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀掉落垃圾箱。", en_us = "Allow no-prefix drop dustbin.")
        private boolean conciseDustbinDrop = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀清空缓存。", en_us = "Allow no-prefix clear cache.")
        private boolean conciseCacheClear = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀掉落缓存。", en_us = "Allow no-prefix drop cache.")
        private boolean conciseCacheDrop = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀触发扫地。", en_us = "Allow no-prefix sweep.")
        private boolean conciseSweep = false;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀清除掉落物。", en_us = "Allow no-prefix clear drops.")
        private boolean conciseClearDrop = true;

        @ConfigEntry.Gui.Tooltip(zh_cn = "允许无前缀延迟清理。", en_us = "Allow no-prefix delay sweep.")
        private boolean conciseDelaySweep = false;
    }

    @Getter
    @Setter
    @Accessors(chain = true, fluent = true)
    public static class PermissionCategory {
        @ConfigEntry.Gui.Tooltip(zh_cn = "虚拟权限 / 修改配置相关所需权限等级（0–4）。", en_us = "Permission level for virtual OP / config (0–4).")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionVirtualOp = 4;

        @ConfigEntry.Gui.Tooltip(zh_cn = "打开垃圾箱所需权限等级。", en_us = "Level to open own dustbin.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinOpen = 0;

        @ConfigEntry.Gui.Tooltip(zh_cn = "为他人打开垃圾箱所需权限等级。", en_us = "Level to open dustbin for others.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinOpenOther = 2;

        @ConfigEntry.Gui.Tooltip(zh_cn = "清空垃圾箱所需权限等级。", en_us = "Level to clear dustbin.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinClear = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "掉落垃圾箱所需权限等级。", en_us = "Level to drop dustbin items.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDustbinDrop = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "清空缓存所需权限等级。", en_us = "Level to clear cache.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCacheClear = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "掉落缓存所需权限等级。", en_us = "Level to drop cache.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCacheDrop = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "手动扫地所需权限等级。", en_us = "Level to run sweep.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionSweep = 0;

        @ConfigEntry.Gui.Tooltip(zh_cn = "清除掉落物所需权限等级。", en_us = "Level to clear item entities.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionClearDrop = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "延迟清理所需权限等级。", en_us = "Level to delay sweep.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionDelaySweep = 1;

        @ConfigEntry.Gui.Tooltip(zh_cn = "用物品捕获玩家所需权限等级。", en_us = "Level to catch players with items.")
        @ConfigEntry.BoundedDiscrete(min = 0, max = 4)
        private int permissionCatchPlayer = 3;
    }

    private static List<String> defaultEntityList() {
        List<String> l = new ArrayList<>();
        l.add(EntityType.ARROW.getRegistryName().toString());
        l.add(EntityType.SPECTRAL_ARROW.getRegistryName().toString());
        l.add(EntityType.EXPERIENCE_ORB.getRegistryName().toString());
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
                Items.SNOWBALL.getRegistryName().toString(),
                Items.GLASS_BOTTLE.getRegistryName().toString(),
                Items.MUSIC_DISC_13.getRegistryName().toString()
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
        RootView c = get();
        c.base().dustbin()
                .dustbinPageLimit(2)
                .cacheLimit(5000)
                .selfCleanInterval(60L * 60 * 1000)
                .selfCleanMode(new ArrayList<>(Arrays.asList(EnumSelfCleanMode.NONE.name())))
                .dustbinOverflowMode(EnumOverflowMode.KEEP.name())
                .dustbinPersistent(true)
                .dropStatsFileLimit(15)
                .dustbinBlockPositions(new ArrayList<>())
                .dustbinBlockMode(EnumDustbinMode.VIRTUAL.name());
        c.base().sweep()
                .sweepWhenNoPlayer(false)
                .sweepWarningContent("")
                .sweepWarningVoice("")
                .sweepWarningVoiceVolume(33)
                .sweepInterval(10L * 60 * 1000)
                .entityList(defaultEntityListReset())
                .entityListMode(EnumListType.WHITE.name())
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
                .chunkCheckMode(EnumChunkCheckMode.ADVANCED.name())
                .chunkCheckEntityList(defaultChunkCheckEntityList())
                .chunkCheckEntityListMode(EnumListType.WHITE.name())
                .chunkCheckOnlyNotice(false);
        c.base().entityCatch()
                .catchEntity(new ArrayList<>())
                .allowCatchEntity(false)
                .catchItem(defaultCatchItem());
        c.base().batch()
                .sweepEntityLimit(500)
                .sweepEntityInterval(2)
                .sweepBatchLimit(10);
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
                .commandDelaySweep("delay");
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
                .conciseDelaySweep(false);
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
                .permissionCatchPlayer(3);
    }

    private static List<String> defaultEntityListReset() {
        List<String> l = new ArrayList<>();
        l.add(EntityType.ARROW.getRegistryName().toString());
        l.add(EntityType.SPECTRAL_ARROW.getRegistryName().toString());
        l.add(EntityType.EXPERIENCE_ORB.getRegistryName().toString());
        l.add("tick, clazz, itemClazz, createProcessing = CreateData.Processing.Time"
                + " -> "
                + "tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)");
        return l;
    }
}
