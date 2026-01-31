[简体中文](README.md) | **English** | [日本語](README_ja.md)

# Aotake Sweep

![](src/main/resources/logo.png)

**A Minecraft Forge, Neoforge and Fabric Timed Cleanup MOD.**

## Table of Contents

- [Aotake Sweep](#aotake-sweep)
    - [Table of Contents](#table-of-contents)
    - [Meaning](#meaning)
    - [Introduction](#introduction)
    - [Features](#features)
    - [Configuration](#configuration)
    - [Commands](#commands)
    - [Entity Filter](#entity-filter)
    - [License](#license)

## Meaning

- **Aotake (竹叶)**: Bamboo leaves have effects like clearing heat and relieving annoyance; green bamboo symbolizes
  freshness, purity, and elegance.
- **Sweep (清)**: To clean, sweep, or clear away.
- **Aotake Sweep (竹叶清)**: With the fresh power of bamboo leaves, sweep away dust and clutter, making the world as
  clean as new, peaceful, and elegant.

## Introduction

This project is for Minecraft (Neo)Forge servers, implementing timed cleanup of dropped items and entities.
This MOD is required on the server side and optional on the client side.

## Features

- **Recycling Strategy**: Optional strategies for handling overflow during garbage collection.
- **Timed Cleanup**: Automatically cleans up dropped items, arrows, etc., at regular intervals.
- **Manual Cleanup**: Allows triggering cleanup via commands, with customizable dimensions and range.
- **Auto Cleanup**: Automatically triggers sweeping when there are too many entities (only sweepable ones) in a chunk.
- **Safe Cleanup**: Configurable whitelist and blacklist, configurable to ignore items on specific blocks.
- **Multi-page Dustbin**: Customizable number of dustbin pages, no more worrying about insufficient capacity.
- **Custom Filters**: Customize entity filters using [Expressions](#entity-filter) based on your needs.
- **Poor Translation**: Text descriptions might be ambiguous or unclear (not just in English).
- **Poor Code**: Bad code + negligent testing = a bunch of smelly bugs.

## TODO

- [ ] **Heatmap**: Generate a heatmap of item drop frequency based on chunk, coordinates, and item type.

---

## Configuration

You can find the MOD-related configurations in the following paths. Details are not repeated here; please refer to the
comments in the Forge default configuration files.

### Common

- Countdown Message Config: [`config/aotake_sweep-warning.json`](config/aotake_sweep-warning.json)
- Server Dustbin Data: `world/data/world_trash_data.dat`
- Vanilla Xin Series Common Config: `config/vanilla.xin/common_config.json`
- Vanilla Xin Series Player Data: `world/playerdata/vanilla.xin/*.nbt`

### Forge

- Common Config (Both Sides): [`config/aotake_sweep-common.toml`](config/forge/aotake_sweep-common.toml)
- Client Config: [`config/aotake_sweep-client.toml`](config/forge/aotake_sweep-client.toml)
- Server Config: [`world/serverconfig/aotake_sweep-server.toml`](config/forge/aotake_sweep-server.toml)

### NeoForge

- Common Config (Both Sides): [`config/aotake_sweep-common.toml`](config/forge/aotake_sweep-common.toml)
- Client Config: [`config/aotake_sweep-client.toml`](config/forge/aotake_sweep-client.toml)
- Server Config: [`config/aotake_sweep-server.toml`](config/forge/aotake_sweep-server.toml)

### Fabric

- Client Config: [`config/aotake_sweep-client.toml`](config/fabric/aotake_sweep-client.toml)
- Server Config: [`config/aotake_sweep-server.toml`](config/fabric/aotake_sweep-server.toml)

---

## Commands

By default, use with the prefix `/aotake`.

- **dustbin**: Open the dustbin.
    - **Arguments**:
        1. `[<Page>]`
- **sweep**: Manually trigger sweeping.
    - **Arguments**:
        1. `[<Range>]`
        2. `[<Dimension>]`
- **delay**: Delay the next cleanup trigger time.
    - **Arguments**:
        1. `[<Seconds>]`
- **killitem**: Clean up dropped items.
    - **Arguments**:
        1. `[<Range>] [<Include Entities>] [<Ignore Entity List Filter>]`
        2. `[<Dimension>] [<Include Entities>] [<Ignore Entity List Filter>]`
- **clearcache**: Clear the garbage in the buffer.
- **dropcache**: Clear the garbage in the buffer by dropping them.
- **cleardustbin**: Clear the dustbin.
    - **Arguments**:
        1. `[<Page>]`
- **dropdustbin**: Clear the dustbin by dropping items.
    - **Arguments**:
        1. `[<Page>]`
- **opv**: Grant a player permission to use a specific command.
    - **Arguments**:
        1. `<Operation> <Player> [<Command Type List>]`
- **language**: Set the player's default language.
    - **Arguments**:
        1. `<Language>`
- **config**: Modify configuration. Do not use this command to modify complex `server` and `common` configurations.
    - **Arguments**:
        1. `mode <Mode>`: Reset configuration to a preset mode.
        2. `disable <Disable MOD>`: Temporarily disable MOD functions.
        3. `player <Config Key> <Config Value>`: Modify player configuration.
        4. `server <Config Key> <Config Value>`: Modify server configuration.
        5. `common <Config Key> <Config Value>`: Modify common configuration.

---

## Entity Filter

Entity Filter Expression. For convenience, the expression will be referred to as `AotakeEL` below.
Configuration items supporting AotakeEL include: `entityList`, `entityRedlist`, `catchEntity`, `chunkCheckEntityList`.

### Examples

- #### Entity ID:
    1. A specific entity, e.g., Arrow `minecraft:arrow`
    2. All entities under a MOD, e.g., [Diligent Stalker](https://github.com/Mafuyu404/DiligentStalker)
       `diligentstalker:*`
    3. A specific entity under any MOD, e.g., Arrow `*:arrow`
- #### AotakeEL:
    1. Items being processed by a fan in [Create](https://github.com/Creators-of-Create/Create)
       `clazz, itemClazz, createProcessing = [CreateData.Processing.Time] -> clazz :> itemClazz && createProcessing > 0`
    2. Dead Ice and Fire Dragons in [Ice and Fire](https://github.com/AlexModGuy/Ice_and_Fire)
       `resource, dead = <com.github.alexthe666.iceandfire.entity.EntityDragonBase:MODEL_DEAD> -> (resource == 'iceandfire:fire_dragon' || resource == 'iceandfire:ice_dragon') && dead == true`

### Explanation

1. Can consist solely of Entity ID, see [Entity ID](#examples) example.
2. Entity ID is automatically converted to AotakeEL, e.g.:
    1. `minecraft:arrow` is equivalent to `resource -> resource == 'minecraft:arrow'`
       or `namespace, path -> namespace == 'minecraft' && path == 'arrow'`
    2. `diligentstalker:*` is equivalent to `namespace -> namespace == 'diligentstalker'`
    3. `*:arrow` is equivalent to `path -> path == 'arrow'`
3. AotakeEL format: `Variable Declaration 1, ..., Variable Declaration n -> Logical Expression`, where:
    - **Variable Declaration** formats:
        1. `Built-in Variable Name`: e.g., Entity ID `resource`
        2. `Custom Variable Name = 'String Constant'`: e.g., `modName = 'AotakeSweep'`
        3. `Custom Variable Name = [Entity NBTPath]`: Only Forge and NeoForge, e.g., remaining processing time of items
           processed by a fan
           in [Create](https://github.com/Creators-of-Create/Create)
           `processTime = [CreateData.Processing.Time]`
        4. `Custom Variable Name = <EntityDataKey>`: e.g., death state of Ice and Fire Dragons
           in [Ice and Fire](https://github.com/AlexModGuy/Ice_and_Fire)
           `dead = <com.github.alexthe666.iceandfire.entity.EntityDragonBase:MODEL_DEAD>`
           or shorthand (not recommended) `dead = <MODEL_DEAD>`
    - **Logical Expression** supported syntax:
        1. `(`、`)`: Parentheses
        2. `!`: Logical NOT
        3. `&&`: Logical AND
        4. `||`: Logical OR
        5. `=`、`==`: Equal
        6. `<>`、`!=`: Not Equal
        7. `<`: Less than
        8. `<=`: Less than or Equal
        9. `>`: Greater than
        10. `>=`: Greater than or Equal
        11. `+`: Add
        12. `-`: Subtract
        13. `*`: Multiply
        14. `/`: Divide
        15. `%`: Modulo
        16. `^`: Math.pow()
        17. `:>`: rightClass.isAssignableFrom(leftClass)
        18. `<:`: rightClass.isInstance(leftClass)
        19. `contains`: left.contains(right)
        20. `Math functions`:
            sqrt、pow、abs、max、min、log、log10、exp、sin、cos、tan、asin、acos、atan、atan2、sinh、cosh、tanh、ceil、floor、round、signum、toRadians、toDegrees、random
        21. `Declared Variable Name`: e.g., `modName`, `processTime` in the examples above
4. AotakeEL Built-in Variables:
    1. `namespace`: The part before `:` in Entity ID, usually MOD ID
    2. `path`: The part after `:` in Entity ID
    3. `resource`, `location`, `resourceLocation`: Complete Entity ID
    4. `clazz`: The `java.lang.Class` object of the current entity
    5. `clazzString`: The name of the `java.lang.Class` object of the current entity
    6. `itemClazz`: The `java.lang.Class` object of the item entity
    7. `itemClazzString`: The name of the `java.lang.Class` object of the item entity
    8. `name`: Name of the entity
    9. `displayName`: Display name of the entity
    10. `customName`: Custom name of the entity
    11. `tick`: Tick count of the entity
    12. `num`: Quantity if it's an item, otherwise fixed to 1
    13. `dim`, `dimension`: Dimension where the entity is located
    14. `x`: Entity X coordinate
    15. `y`: Entity Y coordinate
    16. `z`: Entity Z coordinate
    17. `chunkX`: Entity Chunk X coordinate
    18. `chunkZ`: Entity Chunk Z coordinate
    19. `hasOwner`: Whether the entity is tamed by a player
    20. `ownerName`: Name of the player who tamed the entity

---

## License

MIT License

---

If you have any questions or suggestions, please submit Issues or Pull requests.
