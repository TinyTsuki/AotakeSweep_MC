# 竹叶清实体清理脚本示例

## 安装并启用

1. 将可信的 `.java` 脚本放入服务端运行目录下的 `config/vanilla.xin/aotake_sweep/scripts/`。文件名必须与实现 `EntityRule` 的公开类名一致，包名为 `xin.vanilla.banira.generated`。
2. 重启服务端，或执行 `/aotake config rules reload`。单文件脚本会自动发现，无需额外配置清单或绑定。
3. 在相应的规则列表中选中脚本 ID。ID 格式为 `script:` 加上去掉 `.java` 后的小写文件名。只把文件放进目录不会启用清理。

## 选择示例

| 示例 | 规则 ID | 适用场景 |
| --- | --- | --- |
| [CobblemonWild.java](CobblemonWild.java) | `script:cobblemonwild` | 用于 `BLACK` 模式的实体清理名单。匹配无主人且未处于对战锁定状态的 Cobblemon 宝可梦。需要匹配的 Cobblemon API，已在 Forge 1.20.1 / Cobblemon 1.5.2 上验证。 |
| [CreateProcessingProtection.java](CreateProcessingProtection.java) | `script:createprocessingprotection` | 用于自定义保护名单。匹配 `CreateData.Processing.Time` 大于 0 的物品实体。要保护这些物品，请在相应的实体或区块检查名单中启用 `WHITE` 模式并加入此规则。内置规则也会检查这个加工进度值。 |

**保护物品时请勿使用红名单。** 已进入清理范围的物品命中红名单后仍会被移除，只会跳过回收。强制清理选项也可能绕过已配置的过滤规则。

## 在单文件规则中读取 NBT

| 接口 | 用法 |
| --- | --- |
| `persistentNumber(path...)` | 保留原始数值类型：`Byte`、`Short`、`Integer`、`Long`、`Float` 或 `Double`。 |
| `persistentString(path...)`、`persistentBoolean(path...)`、`persistentValue(MyType.class, path...)` | 路径不存在或类型不匹配时返回 `null`。布尔读取只接受值为 0 或 1 的 NBT 字节。 |
| `persistentList(path...)`、`persistentCompound(path...)` | 返回与原数据分离的只读快照，嵌套容器也只读。容器较大时，建议直接读取更具体的路径。 |
| `hasPersistentValue(path...)` | 只检查路径是否存在，无需复制值。 |

每个路径参数都是独立的键名，不会按点号拆分。例如，机械动力示例用 `persistentNumber("CreateData", "Processing", "Time")` 读取加工进度。遇到列表或数组时，十进制数字参数用于按索引读取。

Forge 和 NeoForge 读取实体的持久 NBT。Fabric 读取当前规则判断期间序列化出的实体 NBT，只能访问模组确实写进实体存档的数据。旧接口 `persistentInt` 保留原有的整数转换行为，且路径仍只支持复合标签的键。

## 兼容旧规则

保留已有的 `rules/manifest.json`、`rules/sources/` 和 `rules/bindings/`，旧版生成的规则仍会从这些位置读取编译所需的绑定。原先放在 `rules/local/` 的单文件脚本可继续使用；移到 `scripts/` 后 ID 不变。同名脚本以 `scripts/` 中的文件为准。
