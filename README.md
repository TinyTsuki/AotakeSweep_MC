# Aotake Sweep (竹叶清)

一个 Minecraft Forge 定时扫地 Mod。

## 目录

- [Aotake Sweep](#aotake_sweep)
    - [目录](#目录)
    - [释义](#释义)
    - [介绍](#介绍)
    - [特性](#特性)
    - [配置说明](#配置说明)
    - [指令说明](#指令说明)
    - [实体过滤器](#实体过滤器)
    - [注意事项](#注意事项)
    - [许可证](#许可证)

## 释义

- **竹叶**：竹叶具有清热除烦等功效；青竹象征着清新、纯粹与高雅。
- **清**：清理、清扫、清除。
- **竹叶清**：用竹叶般的清新之力，扫除尘埃与杂乱，令世界洁净如新、宁静高雅。

## 介绍

本项目适用于Minecraft Forge服务器，实现定时清理掉落物、箭矢等。
该Mod服务器必装，客户端可选。

## 特性

- **回收策略**：可选择的垃圾回收时溢出处理等策略。
- **定时清理**：每隔一段时间自动清理掉落物、箭矢等。
- **手动清理**：允许用指令触发清理，并且可指定维度与范围。
- **自动清理**：区块内实体(仅能被清扫的)过多时自动触发扫地。
- **安全清理**：可配置清理白名单与黑名单，可配置忽略方块上的物品。
- **多页垃圾箱**：垃圾箱页数可自定义，不再为容量不够而烦恼。
- **自定义过滤器**：可根据需求使用[表达式](#实体过滤器)自定义实体过滤器。
- **很烂的翻译**：文本描述可能存在歧义，或其表达方式不够清晰<del>（不仅仅是英文）</del>。
- **很烂的代码**：烂代码 + 疏忽的测试 = 一堆难闻的臭虫。

## TODO

- **热力图**：根据区块、坐标、物品类型统计掉落物掉落频率并生成热力图

## 配置说明

本地配置文件路径 [`config/aotake_sweep-common.toml`](aotake_sweep-common.toml)、
[`world/serverconfig/aotake_sweep-server.toml`](aotake_sweep-server.toml)
，其他的信息不再赘述，请参考默认配置文件中的注释。

## 指令说明

默认配置下，配合前缀`/aotake`食用

- **dustbin**：打开垃圾箱。  
  **参数列表**：
    1. `[<页数>]`
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
- **config**：修改服务器/玩家设置，请勿用该指令修改格式较为复杂的`server`与`common`配置。  
  **参数列表**：
    1. `player <配置项> <配置值>`
    2. `mode <预置配置模式>`
    3. `server <配置项> <配置值>`
    4. `common <配置项> <配置值>`

## 实体过滤器

实体过滤器表达式，为了方便说明，以下会将表达式称为`AotakeEL`，  
其中支持 AotakeEL 的配置项有：`entityList`、`entityRedlist`、`catchEntity`、`chunkCheckEntityList`。

**例子**：

- **实体ID**：
    1. 某个具体的实体，如 箭矢 `minecraft:arrow`
    2. 某个MOD下所有实体，如 [勤劳跟踪狂](https://github.com/Mafuyu404/DiligentStalker)`diligentstalker:*`
    3. 任意MOD下的某个实体，如 箭矢 `*:arrow`
- **AotakeEL**：
    1. [冰火传说](https://github.com/AlexModGuy/Ice_and_Fire) 中死亡的冰龙与火龙  
       `resource, dead = ModelDead -> dead == true && (resource == 'iceandfire:fire_dragon' || resource == 'iceandfire:ice_dragon')`
    2. [机械动力](https://github.com/Creators-of-Create/Create) 中正在被鼓风机处理的物品  
       `clazz, itemClazz = 'net.minecraft.entity.item.ItemEntity', createProcessing = CreateData.Processing.Time -> clazz :> itemClazz && createProcessing > 0`

**说明**：

1. 可以仅由实体ID组成，如 例子 **实体ID**
2. 实体ID会自动转换为AotakeEL，如：
    1. `minecraft:arrow` 等同于 `resource -> resource == 'minecraft:arrow'`   
       或 `namespace, path -> namespace == 'minecraft' && path == 'arrow'`
    2. `diligentstalker:*` 等同于 `namespace -> namespace == 'diligentstalker'`
    3. `*:arrow` 等同于 `path -> path == 'arrow'`
3. AotakeEL格式：`变量声明1, ..., 变量声明n -> 逻辑表达式`，其中：
    - **变量声明** 有以下格式：
        1. `内置变量名称`：如 实体ID `resource`
        2. `自定义变量名称 = '字符串常量'`：如 `modName = 'AotakeSweep'`
        3. `自定义变量名称 = 实体NBTPath`：如 [机械动力](https://github.com/Creators-of-Create/Create)
           中被鼓风机处理的物品的剩余处理时间  
           `processTime = CreateData.Processing.Time`
    - **逻辑表达式** 支持的语法：
        1. `(`、`)`： 括号
        2. `!`： 逻辑非
        3. `&&`： 逻辑与
        4. `||`： 逻辑或
        5. `=`、`==`： 等于
        6. `<>`、`!=`： 不等于
        7. `<`： 小于
        8. `<=`： 小于等于
        9. `>`： 大于
        10. `>=`： 大于等于
        11. `+`： 加
        12. `-`： 减
        13. `*`： 乘
        14. `/`： 除
        15. `^`： Math.pow()
        16. `:>`： rightClass.isAssignableFrom(leftClass)
        17. `<:`： rightClass.isInstance(leftClass)
        18. `contains`： left.contains(right)
        19. `sqrt`： Math.sqrt()
        20. `pow`： Math.pow()
        21. `log`： Math.log()
        22. `sin`： Math.sin()
        23. `cos`： Math.cos()
        24. `abs`： Math.abs()
        25. `random`： Math.random()
        26. `声明的变量`： 如上面例子中的 `modName`、`processTime`
4. AotakeEL内置变量：
    1. `namespace`：实体ID的`:`前半部分，一般为MOD ID
    2. `path`：实体ID的`:`后半部分
    3. `resource`、`location`、`resourceLocation`：完整的实体ID
    4. `clazz`：实体的 `java.lang.Class` 对象
    5. `clazzString`：实体的 `java.lang.Class` 对象名称
    6. `name`：实体的名称
    7. `displayName`：实体的显示名称
    8. `customName`：实体的自定义名称
    9. `tick`：实体的tick计数
    10. `num`：若为物品则为物品的数量，否则固定为1
    11. `dim`、`dimension`：实体所在维度
    12. `x`：实体所处x坐标
    13. `y`：实体所处y坐标
    14. `z`：实体所处z坐标
    15. `chunkX`：实体所处区块x坐标
    16. `chunkZ`：实体所处区块z坐标
    17. `hasOwner`：实体是否被玩家驯服
    18. `ownerName`：驯服该实体的玩家名称

## 注意事项

## 许可证

MIT License

---

如有任何问题或建议，欢迎提交 Issues 或 Pull requests。
