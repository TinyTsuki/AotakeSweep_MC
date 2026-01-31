[简体中文](README.md) | [English](README_en.md) | **日本語**

# Aotake Sweep (竹葉清)

![](src/main/resources/logo.png)

**Minecraft (Neo)Forge 向け定期掃除 MOD。**

## 目次

- [Aotake Sweep](#aotake-sweep-竹葉清)
    - [目次](#目次)
    - [意味](#意味)
    - [はじめに](#はじめに)
    - [特徴](#特徴)
    - [設定](#設定)
    - [コマンド](#コマンド)
    - [エンティティフィルター](#エンティティフィルター)
    - [ライセンス](#ライセンス)

## 意味

- **竹葉 (Aotake)**: 竹の葉には熱を取り除き、イライラを解消する効果があります。青竹は新鮮さ、純粋さ、優雅さを象徴しています。
- **清 (Sweep)**: 片付ける、掃除する、取り除く。
- **竹葉清 (Aotake Sweep)**: 竹の葉のような新鮮な力で、塵や乱雑さを掃き清め、世界を新品のように清潔で、静寂かつ優雅にする。

## はじめに

このプロジェクトは Minecraft (Neo)Forge サーバー向けで、ドロップアイテムやエンティティの定期的な掃除を実装します。
この MOD はサーバー側に必須で、クライアント側は任意です。

## 特徴

- **回収戦略**: ゴミ回収時のオーバーフロー処理などの戦略を選択可能。
- **定期掃除**: 一定時間ごとにドロップアイテムや矢などを自動的に掃除します。
- **手動掃除**: コマンドを使用して掃除をトリガーでき、ディメンションや範囲を指定可能。
- **自動掃除**: チャンク内のエンティティ（掃除可能なもののみ）が多すぎる場合、自動的に掃除をトリガーします。
- **安全な掃除**: ホワイトリストとブラックリストを設定可能。特定のブロック上のアイテムを無視する設定も可能。
- **複数ページのゴミ箱**: ゴミ箱のページ数をカスタマイズ可能。容量不足に悩む必要はありません。
- **カスタムフィルター**: 必要に応じて [式](#エンティティフィルター) を使用してエンティティフィルターをカスタマイズ可能。
- **拙い翻訳**: テキストの説明が曖昧だったり、表現が不明瞭だったりする可能性があります <del>（英語に限らず）</del>。
- **拙いコード**: 酷いコード + 杜撰なテスト = 臭いバグの山。

## TODO

- [ ] **ヒートマップ**: チャンク、座標、アイテムタイプに基づいてドロップアイテムのドロップ頻度を集計し、ヒートマップを生成する。

---

## 設定

MOD 関連の設定は以下のパスにあります。詳細は省略しますので、Forgeデフォルト設定ファイルのコメントを参照してください。

### 通用部分

- カウントダウン通知設定 [`config/aotake_sweep-warning.json`](config/aotake_sweep-warning.json)
- サーバーゴミ箱データ `world/data/world_trash_data.dat`
- Vanilla Xin シリーズ MOD 共通設定 `config/vanilla.xin/common_config.json`
- Vanilla Xin シリーズ MOD プレイヤーデータ `world/playerdata/vanilla.xin/*.nbt`

### Forge

- 両側共通設定 [`config/aotake_sweep-common.toml`](config/forge/aotake_sweep-common.toml)
- クライアント設定 [`config/aotake_sweep-client.toml`](config/forge/aotake_sweep-client.toml)
- サーバー設定 [`world/serverconfig/aotake_sweep-server.toml`](config/forge/aotake_sweep-server.toml)

### NeoForge

- 両側共通設定 [`config/aotake_sweep-common.toml`](config/forge/aotake_sweep-common.toml)
- クライアント設定 [`config/aotake_sweep-client.toml`](config/forge/aotake_sweep-client.toml)
- サーバー設定 [`config/aotake_sweep-server.toml`](config/forge/aotake_sweep-server.toml)

### Fabric

- クライアント設定 [`config/aotake_sweep-client.toml`](config/fabric/aotake_sweep-client.toml)
- サーバー設定 [`config/aotake_sweep-server.toml`](config/fabric/aotake_sweep-server.toml)


- 両側共通設定 [`config/aotake_sweep-common.toml`](aotake_sweep-common.toml)
- クライアント設定 [`config/aotake_sweep-client.toml`](aotake_sweep-client.toml)
- サーバー設定 [`world/serverconfig/aotake_sweep-server.toml`](aotake_sweep-server.toml)
- サーバーゴミ箱データ `world/data/world_trash_data.dat`
- Vanilla Xin シリーズ MOD 共通設定 `config/vanilla.xin/common_config.json`
- Vanilla Xin シリーズ MOD プレイヤーデータ `world/playerdata/vanilla.xin/*.nbt`

---

## コマンド

デフォルト設定では、プレフィックス `/aotake` と共に使用します。

- **dustbin**: ゴミ箱を開く。
    - **引数リスト**:
        1. `[<ページ数>]`
- **sweep**: 手動で掃除をトリガーする。
    - **引数リスト**:
        1. `[<範囲>]`
        2. `[<ディメンション>]`
- **delay**: 次回の掃除トリガー時間を延期する。
    - **引数リスト**:
        1. `[<秒>]`
- **killitem**: ドロップアイテムを掃除する。
    - **引数リスト**:
        1. `[<範囲>] [<エンティティを含めるか>] [<エンティティリストフィルターを無視するか>]`
        2. `[<ディメンション>] [<エンティティを含めるか>] [<エンティティリストフィルターを無視するか>]`
- **clearcache**: キャッシュエリアのゴミを空にする。
- **dropcache**: キャッシュエリアのゴミをドロップアイテムとして空にする。
- **cleardustbin**: ゴミ箱を空にする。
    - **引数リスト**:
        1. `[<ページ数>]`
- **dropdustbin**: ゴミ箱をドロップアイテムとして空にする。
    - **引数リスト**:
        1. `[<ページ数>]`
- **opv**: プレイヤーに特定のコマンドを使用する権限を与える。
    - **引数リスト**:
        1. `<操作> <プレイヤー> [<コマンドタイプリスト>]`
- **language**: プレイヤーのデフォルト言語を設定する。
    - **引数リスト**:
        1. `<言語>`
- **config**: 設定を変更する。形式が複雑な `server` と `common` 設定の変更にはこのコマンドを使用しないでください。
    - **引数リスト**:
        1. `mode <モード>` 設定ファイルをプリセットモードにリセットする
        2. `disable <MODを無効化するか>` MOD 機能を一時的に無効にする
        3. `player <設定キー> <設定値>` プレイヤー設定を変更する
        4. `server <設定キー> <設定値>` サーバー設定を変更する
        5. `common <設定キー> <設定値>` 共通設定を変更する

---

## エンティティフィルター

エンティティフィルター式。説明の便宜上、以下では式を `AotakeEL` と呼びます。
AotakeEL をサポートする設定項目には、`entityList`、`entityRedlist`、`catchEntity`、`chunkCheckEntityList` があります。

### 例

#### エンティティ ID

1. 特定のエンティティ、例：矢 `minecraft:arrow`
2. 特定の MOD 下のすべてのエンティティ、例：[Diligent Stalker](https://github.com/Mafuyu404/DiligentStalker)
   `diligentstalker:*`
3. 任意の MOD 下の特定のエンティティ、例：矢 `*:arrow`

#### AotakeEL

1. [Create](https://github.com/Creators-of-Create/Create) でファンによって処理中のアイテム  
   `clazz, itemClazz, createProcessing = [CreateData.Processing.Time] -> clazz :> itemClazz && createProcessing > 0`
2. [Ice and Fire](https://github.com/AlexModGuy/Ice_and_Fire) で死亡したアイスドラゴンとファイアドラゴン  
   `resource, dead = <com.github.alexthe666.iceandfire.entity.EntityDragonBase:MODEL_DEAD> -> (resource == 'iceandfire:fire_dragon' || resource == 'iceandfire:ice_dragon') && dead == true`

### 説明

1. エンティティ ID のみで構成可能。[例](#エンティティ-id)を参照。
2. エンティティ ID は自動的に AotakeEL に変換されます。例：
    1. `minecraft:arrow` は `resource -> resource == 'minecraft:arrow'` と等価  
       または `namespace, path -> namespace == 'minecraft' && path == 'arrow'`
    2. `diligentstalker:*` は `namespace -> namespace == 'diligentstalker'` と等価
    3. `*:arrow` は `path -> path == 'arrow'` と等価
3. AotakeEL 形式：`変数宣言1, ..., 変数宣言n -> 論理式`、ここで：
    - **変数宣言** の形式：
        1. `組み込み変数名`：例：エンティティ ID `resource`
        2. `カスタム変数名 = '文字列定数'`：例：`modName = 'AotakeSweep'`
        3. `カスタム変数名 = [エンティティNBTパス]`
           ：例：ForgeとNeoForgeのみ、[Create](https://github.com/Creators-of-Create/Create)
           でファンによって処理中のアイテムの残り処理時間  
           `processTime = [CreateData.Processing.Time]`
        4. `カスタム変数名 = <EntityDataKey>`：例：[Ice and Fire](https://github.com/AlexModGuy/Ice_and_Fire)
           のアイスドラゴンとファイアドラゴンの死亡状態  
           `dead = <com.github.alexthe666.iceandfire.entity.EntityDragonBase:MODEL_DEAD>`  
           または省略形（非推奨） `dead = <MODEL_DEAD>`
    - **論理式** でサポートされる構文：
        1. `(`、`)`: 括弧
        2. `!`: 論理否定
        3. `&&`: 論理積 (AND)
        4. `||`: 論理和 (OR)
        5. `=`、`==`: 等しい
        6. `<>`、`!=`: 等しくない
        7. `<`: より小さい
        8. `<=``: 以下
        9. `>`: より大きい
        10. `>=`: 以上
        11. `+`: 加算
        12. `-`: 減算
        13. `*`: 乗算
        14. `/`: 除算
        15. `%`: 剰余
        16. `^`: Math.pow()
        17. `:>`: rightClass.isAssignableFrom(leftClass)
        18. `<:`: rightClass.isInstance(leftClass)
        19. `contains`: left.contains(right)
        20. `Math関数`:
            sqrt、pow、abs、max、min、log、log10、exp、sin、cos、tan、asin、acos、atan、atan2、sinh、cosh、tanh、ceil、floor、round、signum、toRadians、toDegrees、random
        21. `宣言された変数名`: 上記の例の `modName`、`processTime` など
4. AotakeEL 組み込み変数：
    1. `namespace`: エンティティ ID の `:` の前の部分、通常は MOD ID
    2. `path`: エンティティ ID の `:` の後の部分
    3. `resource`、`location`、`resourceLocation`: 完全なエンティティ ID
    4. `clazz`: 現在のエンティティの `java.lang.Class` オブジェクト
    5. `clazzString`: 現在のエンティティの `java.lang.Class` オブジェクト名
    6. `itemClazz`: アイテムエンティティの `java.lang.Class` オブジェクト
    7. `itemClazzString`: アイテムエンティティの `java.lang.Class` オブジェクト名
    8. `name`: エンティティの名前
    9. `displayName`: エンティティの表示名
    10. `customName`: エンティティのカスタム名
    11. `tick`: エンティティの tick カウント
    12. `num`: アイテムの場合は数量、それ以外は 1 に固定
    13. `dim`、`dimension`: エンティティが存在するディメンション
    14. `x`: エンティティの X 座標
    15. `y`: エンティティの Y 座標
    16. `z`: エンティティの Z 座標
    17. `chunkX`: エンティティのチャンク X 座標
    18. `chunkZ`: エンティティのチャンク Z 座標
    19. `hasOwner`: エンティティがプレイヤーに手懐けられているか
    20. `ownerName`: エンティティを手懐けたプレイヤーの名前

---

## ライセンス

MIT License

---

質問や提案がある場合は、Issues または Pull requests を送信してください。
