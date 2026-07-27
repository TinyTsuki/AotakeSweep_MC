<div align="center">

| [中文](README.md) | [English](locales/README_en.md) | [日本語](locales/README_ja.md) |
|:---------------:|:-------------------------------:|:---------------------------:|

<img src="assets/logo.png" alt="Aotake Sweep" width="160" />

# Aotake Sweep (竹叶清)

**一个 Minecraft Forge、NeoForge、Fabric 定时扫地 MOD。**

</div>

---

## 目录

- [Aotake Sweep](#aotake-sweep-竹叶清)
    - [目录](#目录)
    - [释义](#释义)
    - [介绍](#介绍)
    - [特性](#特性)
    - [配置说明](#配置说明)
    - [指令说明](#指令说明)
    - [实体过滤器](#实体过滤器)
    - [构建](#构建)
    - [许可证](#许可证)

## 释义

- **竹叶**：竹叶具有清热除烦等功效；青竹象征着清新、纯粹与高雅。
- **清**：清理、清扫、清除。
- **竹叶清**：用竹叶般的清新之力，扫除尘埃与杂乱，令世界洁净如新、宁静高雅。

## 介绍

本项目适用于 Minecraft (Neo)Forge 服务器，实现定时清理掉落物与实体。
该 MOD 服务器必装，客户端可选。

## 特性

- **回收策略**：可选择的垃圾回收时溢出处理等策略；
- **定时清理**：每隔一段时间自动清理掉落物、箭矢等；
- **手动清理**：允许用指令触发清理，并且可指定维度与范围；
- **自动清理**：区块内实体 (仅能被清扫的) 过多时自动触发扫地；
- **安全清理**：可配置清理白名单与黑名单，可配置忽略方块上的物品；
- **多页垃圾箱**：垃圾箱页数可自定义，不再为容量不够而烦恼；
- **区块超载暂存**：区块实体超限时可将回收物独立保存，管理员可列出、打开并授权玩家查看；
- **自定义过滤器**：可根据需求使用 [表达式](#实体过滤器) 自定义实体过滤器；
- **很烂的翻译**：文本描述可能存在歧义，或其表达方式不够清晰 <del>（不仅仅是英文）</del>；
- **很烂的代码**：烂代码 + 疏忽的测试 = 一堆难闻的臭虫。

## TODO

- [ ] **热力图**：根据区块、坐标、物品类型统计掉落物掉落频率并生成热力图

---

## 配置说明

您可以在以下路径找到 MOD 相关配置，详细的信息不再赘述，请参考 Forge 默认配置文件中的注释。

### 通用部分

- 倒计时提示配置 `config/aotake_sweep-warning.json`
- 服务器垃圾箱数据 `world/data/world_trash_data.dat`
- 掉落统计 `world/stats/aotake_sweep/*.json`（按日期存储，如 `2025-02-24.json`）
- 香草芯系列 MOD 通用配置 `config/vanilla.xin/common_config.json`
- 香草芯系列 MOD 玩家数据 `world/playerdata/vanilla.xin/*.nbt`
- 区块超载暂存位于世界数据目录的 `aotake_sweep/chunk_vault`，查看授权单独保存在 `chunk_vault_grants.json`

### 服务端配置要点（垃圾箱相关）

- **dustbinPersistent**：是否持久化垃圾箱数据
- **dropStatsFileLimit**：掉落统计文件数量上限（按日期）
    - `-1`：禁用掉落统计
    - `0`：不限制
    - `1`～`3650`：保留最近 N 天的统计文件，超出时删除最旧文件

### Forge

- 双端通用配置 `config/aotake_sweep-common.toml`
- 客户端相关配置 `config/aotake_sweep-client.toml`
- 服务端相关配置 `world/serverconfig/aotake_sweep-server.toml`

### NeoForge

- 双端通用配置 `config/aotake_sweep-common.toml`
- 客户端相关配置 `config/aotake_sweep-client.toml`
- 服务端相关配置 `config/aotake_sweep-server.toml`

### Fabric

- 客户端相关配置 `config/aotake_sweep-client.toml`
- 服务端相关配置 `config/aotake_sweep-server.toml`

---

## 指令说明

默认配置下，配合前缀 `/aotake` 使用。

- **dustbin**：打开垃圾箱。
  **参数列表**：
    1. `[<页数>]`
- **chunkvault**：查看与管理区块超载清扫时回收的物品；该暂存与全局垃圾箱分开保存。
  **参数列表**：
    1. `list [<页>]`：分页列出暂存编号
    2. `open <编号> [<页>]`：打开指定暂存
    3. `grant <编号> <玩家>`：授予玩家查看指定暂存的权限
- **sweep**：手动触发扫地。
  **参数列表**：
    1. `[<范围>]`
    2. `[<维度>]`
- **delay**：延后下次清理的触发时间。
  **参数列表**：
    1. `[<秒>]`
- **killitem**：清理掉落物。
  **参数列表**：
    1. `[<范围>] [<是否包含实体>] [<是否忽略实体名单过滤器>]`
    2. `[<维度>] [<是否包含实体>] [<是否忽略实体名单过滤器>]`
- **clearcache**：清空缓存区的垃圾。
- **dropcache**：以掉落物的形式清空缓存区的垃圾。
- **cleardustbin**：清空垃圾箱。
  **参数列表**：
    1. `[<页数>]`
- **dropdustbin**：以掉落物的形式清空垃圾箱。
  **参数列表**：
    1. `[<页数>]`
- **opv**：给某个玩家添加使用某个指令的权限。
  **参数列表**：
    1. `<操作> <玩家> [<指令类型列表>]`
- **language**：设置玩家默认语言。
  **参数列表**：
    1. `<语言>`
- **config**：修改配置，请勿用该指令修改格式较为复杂的 `server` 与 `common` 配置。
  **参数列表**：
    1. `mode <模式>` 将配置文件重置为预置的模式
    2. `disable <是否禁用MOD>` 临时禁用 MOD 功能
    3. `player <配置项> <配置值>` 修改玩家配置
    4. `server <配置项> <配置值>` 修改服务器配置
    5. `common <配置项> <配置值>` 修改通用配置

---

## 实体过滤器

实体过滤器表达式，为了方便说明，以下会将表达式称为 `AotakeEL`。
其中支持 AotakeEL 的配置项有：`entityList`、`entityRedlist`、`catchEntity`、`chunkCheckEntityList`。

### 例子

#### 实体 ID

1. 某个具体的实体，如 箭矢 `minecraft:arrow`
2. 某个 MOD 下所有实体，如 [勤劳跟踪狂](https://github.com/Mafuyu404/DiligentStalker) `diligentstalker:*`
3. 任意 MOD 下的某个实体，如 箭矢 `*:arrow`

#### AotakeEL

1. [机械动力](https://github.com/Creators-of-Create/Create) 中正在被鼓风机处理的物品
   `clazz, itemClazz, createProcessing = [CreateData.Processing.Time] -> clazz :> itemClazz && createProcessing > 0`
2. [冰火传说](https://github.com/AlexModGuy/Ice_and_Fire) 中死亡的冰龙与火龙
   `resource, dead = <com.github.alexthe666.iceandfire.entity.EntityDragonBase:MODEL_DEAD> -> (resource == 'iceandfire:fire_dragon' || resource == 'iceandfire:ice_dragon') && dead == true`
3. 从实体根反射读取嵌套字段（与 `<>` 区分：不经过 `DataParameter`）
   `t = {persistentData.someKey}, tick -> t != null && tick > 60`
4. 显式声明类上的反射链（与同步器路径相同类名规则），段之间可用 `.` 或 `:` 混写：
   `v = {com.example.Entity:someField:child}`（等价于用 `.` 连接各段，如 `{com.example.Entity.someField.child}`）

### 说明

1. 可以仅由实体 ID 组成，如 [例子](#实体-id)。
2. 实体 ID 会自动转换为 AotakeEL，如：
    1. `minecraft:arrow` 等同于 `resource -> resource == 'minecraft:arrow'`
       或 `namespace, path -> namespace == 'minecraft' && path == 'arrow'`
    2. `diligentstalker:*` 等同于 `namespace -> namespace == 'diligentstalker'`
    3. `*:arrow` 等同于 `path -> path == 'arrow'`
3. **AotakeEL 格式**：`变量声明1, ..., 变量声明n -> 逻辑表达式`
   逗号、箭头 `->`、等号 `=` 可用前缀 `\` 转义，避免与语法冲突。

#### 变量声明的几种来源

左侧每一项要么是 **`名称 = 右侧`**，要么是 **`名称`（无等号，表示预定义变量）**。右侧形态决定数据来源：

| 来源              | 示例                                                                    | 作用                                                                                                                                                                                                                      |
|-----------------|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **预定义**         | `resource`、`tick`、`clazz`                                             | 读取实体常用信息（注册 id、坐标、类对象、驯服等），见下表「内置变量」。**成本最低**。                                                                                                                                                                          |
| **字面量**         | `tag = 'foo'`、`tag = "bar"`                                           | 提供常量字符串，用于与别的变量比较。几乎无额外开销。                                                                                                                                                                                              |
| **NBT 路径**      | `t = [SomeMod.Data]` 或 `t = SomeMod.Data`                             | 读 `entity.getPersistentData()` 中路径；数字→`Number`，集合→数组，否则→字符串；无键为 `null`。**成本：NBT 树查找**。                                                                                                                                  |
| **`<…>` 同步器路径** | `dead = <pkg.Entity:FLAG>`、`v = <:FLAG>`、`v = <:a:b:0>`、`v = <a:b.c>` | 先按 `DataParameter` 从实体数据取值；**若首段不是同步器字段**，则按下方规则做反射回退。链上每一段可为 **字段名、Map 键、List/数组下标**。**成本：`DataParameter` 缓存命中时较低；链式反射/容器逐级访问随深度增加。**                                                                                  |
| **`{…}` 反射链**   | `x = {field.sub.0}`、`x = {pkg.Entity:field:child}`                    | **不读** `DataParameter`；可选 **声明类全限定名**（解析规则与 `<>` 相同），首字段在该类上对 `entity` 解析，其后为嵌套 walk。无声明类时从 `Entity` 根起；**无类时**段分隔符 **`.` 与 `:` 等价**；含字面分隔符时用 `\.`、`\:`。`Optional` / `OptionalInt` / `Atomic*` 等会规范成标量。**成本：与深层反射链相当。** |

**路径解析（`<>` 与 `{}` 共用）**

- **声明类**：从路径开头起，在每个未转义的 `.` 或 `:` 处截断，取能成功 `Class.forName` 的**最长**前缀作为声明类；其后为字段链。声明类段内只用
  `.` 包名（与 Java FQN 一致）。
- **有声明类时**：字段链在余下子串上同时按未转义的 **`.`** 与 **`:`** 切段（可混用）。
- **`<>` 且无声明类**：为兼容旧配置，**仅**按未转义的 **`:`** 切段；只有一段时**整段保留**（其中的 `.` 视为字段名的一部分，如
  `<MODEL_DEAD>` 或 `<foo.bar>` 单名）。
- **`{}` 且无声明类**：整段路径上同时按 **`.`** 与 **`:`** 切段（与「有声明类时的字段链」一致），便于写 `a.b.c` 或 `a:b:c`。

#### 逻辑表达式

支持括号、`!` `&&` `||`、比较与算术、`^`（`Math.pow`）、`:>` / `<:`（类继承/实例判断）、`contains`、以及一组允许的 `Math` 函数（如
`sqrt`、`abs`、`sin`…）。变量名即左侧声明的名称。

#### 内置变量

`namespace`、`path`、`resource` / `location` / `resourceLocation`、`clazz`、`clazzString`、`itemClazz`、`itemClazzString`、
`name`、`displayName`、`customName`、`tick`、`num`、`dim` / `dimension`、`x` / `y` / `z`、`chunkX`、`chunkZ`、`hasOwner`、
`ownerName`。未在此列出的单独标识符在表达式中值为 `null`。

#### 性能与缓存

- **按规则字符串缓存**：每条配置串首次出现时解析为「变量描述 + 表达式 AST」并放入缓存，之后相同字符串**不再解析**。
- **单次判定复用 Map**：对配置列表逐条尝试时**复用**同一个变量 `HashMap`（`clear` 后写入），减少小对象分配。
- **路径预解析**：`<>` 与 `{}` 内的路径在**编译缓存时**拆好，不在每个实体上重复 `split`。
- **`DataParameter` 缓存**：缓存键为 **`实体运行时类 :: 首段键`**，避免不同实体类共用同名静态字段键时取错同步器，也避免在错误类型上解析失败后把
  `null` 缓存给本应成功的类型。
- **成本排序（经验性）**：预定义 / 字面量 **<** `DataParameter` 单读 **<** NBT 路径 **≈** 深层反射链（`<>` 链式或 `{}`）。列表中
  **更轻的规则放前面**可略省 CPU（短路求值：命中即停）。

---

## 构建

docs 分支提供统一批量构建脚本：

```bat
scripts\build-all.bat
```

脚本默认动态构建本地 `forge/*`、`fabric/*`、`neoforge/*` 分支，不包含 `dev/*`、`maintenance/*` 等其他命名空间。
每个分支都在 detached 临时 worktree 中构建，不会切换当前工作树。仅检查分支和 JDK 配置时使用：

```bat
scripts\build-all.bat -ListOnly
```

通过 glob 表达式选择分支：

```bat
scripts\build-all.bat -BranchExpression "forge/*"
scripts\build-all.bat -BranchExpression "*/21.1"
scripts\build-all.bat -BranchExpression "forge/*,!forge/16.5"
scripts\build-all.bat -BranchExpression "fabric/18.2"
```

`!` 开头的表达式用于排除分支；旧参数名 `-Branches` 仍可作为别名使用。

---

## 许可证

MIT License

---

如有任何问题或建议，欢迎提交 Issues 或 Pull requests。
