<div align="center">

| [中文](README.md) | [English](README_en.md) | [日本語](README_ja.md) |
|:---------------:|:-----------------------:|:-------------------:|

<img src="src/main/resources/logo.png"  alt="Aotake Sweep" />

# Aotake Sweep (竹葉清)

**Minecraft Forge、NeoForge、Fabric 向け定期掃除 MOD。**

</div>

---

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

MOD 関連の設定は以下のパスにあります。詳細は省略しますので、Forge デフォルト設定ファイルのコメントを参照してください。

### 通用部分

- カウントダウン通知設定 [`config/aotake_sweep-warning.json`](config/aotake_sweep-warning.json)
- サーバーゴミ箱データ `world/data/world_trash_data.dat`
- ドロップ統計 `world/stats/aotake_sweep/*.json`（日付ごとに保存、例：`2025-02-24.json`）
- Vanilla Xin シリーズ MOD 共通設定 `config/vanilla.xin/common_config.json`
- Vanilla Xin シリーズ MOD プレイヤーデータ `world/playerdata/vanilla.xin/*.nbt`

### サーバー設定の要点（ゴミ箱関連）

- **dustbinPersistent**：ゴミ箱データを永続化するか
- **dropStatsFileLimit**：ドロップ統計ファイル数の上限（日付ごと）
    - `-1`：無効
    - `0`：制限なし
    - `1`～`3650`：直近 N 日分の統計を保持、超過分は最古のファイルから削除

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

説明の便宜上、エンティティフィルター式を **`AotakeEL`** と呼びます。  
AotakeEL をサポートする設定項目：`entityList`、`entityRedlist`、`catchEntity`、`chunkCheckEntityList`。

### 例

#### エンティティ ID

1. 特定のエンティティ（例：矢 `minecraft:arrow`）
2. ある MOD 配下のすべて（例：[Diligent Stalker](https://github.com/Mafuyu404/DiligentStalker) `diligentstalker:*`）
3. 任意の MOD 配下のある ID（例：矢 `*:arrow`）

#### AotakeEL

1. [Create](https://github.com/Creators-of-Create/Create) でファン処理中のアイテム  
   `clazz, itemClazz, createProcessing = [CreateData.Processing.Time] -> clazz :> itemClazz && createProcessing > 0`
2. [Ice and Fire](https://github.com/AlexModGuy/Ice_and_Fire) の死亡した氷竜・火竜  
   `resource, dead = <com.github.alexthe666.iceandfire.entity.EntityDragonBase:MODEL_DEAD> -> (resource == 'iceandfire:fire_dragon' || resource == 'iceandfire:ice_dragon') && dead == true`
3. エンティティ根からの反射（`<>` とは異なり **`DataParameter` を読まない**）  
   `t = {persistentData.someKey}, tick -> t != null && tick > 60`
4. 宣言クラス付き反射（同期器パスと同じクラス判定）。段は **`.`** と **`:`** の混在可  
   `v = {com.example.Entity:someField:child}`（`{com.example.Entity.someField.child}` と同趣旨）

### 説明

1. エンティティ ID のみの行も可。[エンティティ ID](#エンティティ-id) を参照。
2. エンティティ ID は AotakeEL に自動展開されます。例：
    1. `minecraft:arrow` → `resource -> resource == 'minecraft:arrow'` または
       `namespace, path -> namespace == 'minecraft' && path == 'arrow'`
    2. `diligentstalker:*` → `namespace -> namespace == 'diligentstalker'`
    3. `*:arrow` → `path -> path == 'arrow'`
3. **AotakeEL 形式**：`変数宣言1, ..., 変数宣言n -> 論理式`  
   カンマ、`->`、`=` は衝突時に `\` でエスケープ可能。

#### 変数宣言の種類

各要素は **`名前 = 右辺`** か、等号なしの **`名前`**（組み込み変数）のいずれか。右辺の形でデータ元が決まります。

| 種類            | 例                                                                     | 役割                                                                                                                                                                                                                                    |
|---------------|-----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **組み込み**      | `resource`、`tick`、`clazz`                                             | 登録 ID・座標・クラス・手懐け等（下表）。**最も軽い。**                                                                                                                                                                                                       |
| **リテラル**      | `tag = 'foo'`、`tag = "bar"`                                           | 定数文字列。ほぼ無コスト。                                                                                                                                                                                                                         |
| **NBT パス**    | `t = [SomeMod.Data]` または `t = SomeMod.Data`                           | `entity.getPersistentData()`。数値→`Number`、集合→配列、他は文字列。無ければ `null`。**NBT 走査コスト。**                                                                                                                                                       |
| **`<…>` 同期器** | `dead = <pkg.Entity:FLAG>`、`v = <:FLAG>`、`v = <:a:b:0>`、`v = <a:b.c>` | まず `DataParameter`。**先頭が同期器でない**場合は下記ルールで反射にフォールバック。各段は **フィールド名 / Map キー / リスト・配列添字**。**キャッシュヒット時は軽いが、深いほど反射・コンテナ走査が重い。**                                                                                                            |
| **`{…}` 反射**  | `x = {field.sub.0}`、`x = {pkg.Entity:field:child}`                    | **`DataParameter` は使わない**。任意で **宣言クラス FQN**（`<>` と同じ判定）。先頭フィールドはそのクラス文脈で `entity` から読み、その後ネスト。宣言クラスなしは `Entity` 根から。**クラスなし時**は **`.` と `:` を同等の区切り**；字面は `\.`、`\:`。`Optional` / `OptionalInt` / `Atomic*` は式向けに正規化。**深い反射と同程度のコスト。** |

**パス解析（`<>` と `{}` 共通）**

- **宣言クラス**：先頭から、未エスケープの `.` / `:` で切った接頭辞について `Class.forName` が成功する **最長**
  を宣言クラスとし、残りをフィールド連（FQN 内は `.` のみ）。
- **宣言クラスあり**：残り（tail）を未エスケープの **`.`** と **`:`** で分割（混在可）。
- **`<>` かつ宣言クラスなし**（互換）：未エスケープの **`:`** のみで分割。**1 段だけ**のときは **全体を 1 セグメント**（`.`
  は名前の一部。例：`<MODEL_DEAD>`、`<foo.bar>`）。
- **`{}` かつ宣言クラスなし**：内側全体を **`.`** と **`:`** で分割（「宣言クラスありの tail」と同じルール）。`a.b.c` や
  `a:b:c` の記述が容易。

#### 論理式（右辺）

括弧、`!` `&&` `||`、比較・四則、`^`（`Math.pow`）、`:>` / `<:`（クラス関係）、`contains`、許可された `Math` 系関数（`sqrt`、`abs`、
`sin` 等）。変数名は左辺で宣言した名前。

#### 組み込み変数

`namespace`、`path`、`resource` / `location` / `resourceLocation`、`clazz`、`clazzString`、`itemClazz`、`itemClazzString`、
`name`、`displayName`、`customName`、`tick`、`num`、`dim` / `dimension`、`x` / `y` / `z`、`chunkX`、`chunkZ`、`hasOwner`、
`ownerName`。ここにない単独識別子は `null`。

#### 性能・キャッシュ

- **ルール文字列ごとにキャッシュ**：初回だけ変数記述＋式 AST に分解し、同一文字列は再解析しない。
- **1 回の判定で `HashMap` を再利用**：ルール列を順に試すとき `clear` して使い回し、小オブジェクト削減。
- **パスはコンパイル時に分割**：`<>` / `{}` 内をエンティティ毎に毎回 split しない。
- **`DataParameter` キャッシュキー**：**`エンティティの実行時クラス :: 先頭キー`**。別エンティティ型で同名静的フィールドを取り違えないほか、
  **非互換型で失敗した `null` を、本来成功する型へ誤キャッシュしない**。
- **おおよその重さ**：組み込み／リテラル **<** 単一 `DataParameter` **<** NBT **≈** 深い反射。**軽いルールを前に**
  置くと短絡でわずかに有利。

---

## ライセンス

MIT License

---

質問や提案がある場合は、Issues または Pull requests を送信してください。
