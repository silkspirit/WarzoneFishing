# WarzoneFishing v2.0.0

Custom fishing rewards in warzone areas with NBT-based rarity system for Minecraft 1.8.8.

## Features

- **NBT-Based Rarity Fish** - Different quality fish (Common, Uncommon, Rare, Epic, Legendary) with custom NBT tags for ShopGUIPlus integration
- **Custom Skull Textures** - Support for Base64 skull textures for special items
- **Crate Key Rewards** - Give crate keys via commands or as custom items
- **Teal/Aqua Theme** - Beautiful teal-themed messages and UI
- **Multiple Claim Plugins** - Works with Factions, FactionsUUID, WorldGuard, or standalone
- **Weighted Random Rewards** - Configurable chance weights for balanced loot
- **Broadcast System** - Announce rare catches server-wide
- **Title/ActionBar Display** - Beautiful catch notifications

## Installation

1. Place `WarzoneFishing-2.0.0.jar` in your `plugins/` folder
2. Start the server to generate `config.yml`
3. Configure rewards and settings in `config.yml`
4. Use `/wf reload` to apply changes

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/wf reload` | Reload configuration | `warzonefishing.admin` |
| `/wf list [rarity]` | List all rewards | `warzonefishing.admin` |
| `/wf give <player> <reward> [amount]` | Give a reward | `warzonefishing.admin` |
| `/wf test` | Test a random reward | `warzonefishing.admin` |
| `/wf preview <reward>` | Preview a reward | `warzonefishing.admin` |
| `/wf info` | Show plugin info | `warzonefishing.use` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `warzonefishing.use` | Basic command access | true |
| `warzonefishing.fish` | Can fish in warzone | true |
| `warzonefishing.admin` | Admin commands | op |
| `warzonefishing.bypass.cooldown` | Bypass fishing cooldown | op |

## Configuration

### Settings

```yaml
settings:
  # Warzone detection plugin: factions, factionsuuid, worldguard, none
  claim-plugin: factions
  
  # For WorldGuard: region name to check
  worldguard-region: warzone
  
  # For 'none' mode: allowed world list
  allowed-worlds:
    - world
    - warzone
  
  # Drop at hook location or give to inventory
  drop-at-hook: false
  
  # Cooldown between catches (seconds, 0 = disabled)
  cooldown: 0
  
  # Title animation (ticks, 20 = 1 second)
  title-fade-in: 10
  title-stay: 40
  title-fade-out: 10
  
  # Action bar message (empty = disabled)
  action-bar-message: "&b+1 &f{item}"
```

### Reward Types

#### ITEM - Regular items with optional NBT
```yaml
common_fish:
  type: ITEM
  material: RAW_FISH
  data: 0  # 0=cod, 1=salmon, 2=clownfish, 3=pufferfish
  display-name: "&7Common Cod"
  amount: 1
  lore:
    - "&8Just a regular fish..."
  nbt:
    warzone_rarity: "common"
    sell_value: 5
  enchantments:
    DURABILITY: 1
  glow: false
  unbreakable: false
  hide-flags: false
  chance: 45.0
  rarity: COMMON
  title-message: "&7Fish Caught!"
  subtitle-message: "&fCommon Cod"
  sound: SPLASH
  sound-pitch: 1.0
  sound-volume: 1.0
```

#### CUSTOM - Skull with texture
```yaml
fish_trophy:
  type: CUSTOM
  material: SKULL_ITEM
  data: 3
  display-name: "&6&lFish Trophy"
  skull-texture: "eyJ0ZXh0dXJlcy..." # Base64 texture
  # OR
  skull-owner: "Notch"  # Player skin
  lore:
    - "&eA rare trophy!"
  nbt:
    trophy: true
  chance: 1.0
  rarity: LEGENDARY
```

#### COMMAND - Command-only rewards
```yaml
crate_key:
  type: COMMAND
  chance: 2.0
  rarity: RARE
  title-message: "&3&lKey Found!"
  subtitle-message: "&bCrate Key"
  commands:
    - "crate give {player} rare 1"
  broadcast: true
  broadcast-message: "&3{player} found a crate key!"
```

### NBT Tags for ShopGUIPlus

Items can have custom NBT tags that ShopGUIPlus can use for pricing:

```yaml
nbt:
  warzone_rarity: "rare"    # String tag
  sell_value: 100           # Integer tag
  custom_flag: true         # Boolean tag
```

In your ShopGUIPlus config, use `compareMeta: true` to match items by NBT.

### Rarity Colors

| Rarity | Color Code | Display |
|--------|------------|---------|
| COMMON | `&7` | Gray |
| UNCOMMON | `&a` | Green |
| RARE | `&3` | Dark Aqua |
| EPIC | `&5` | Dark Purple |
| LEGENDARY | `&6` | Gold |

### Placeholders

Available in messages:
- `{player}` - Player name
- `{item}` - Item display name
- `{rarity}` - Rarity name
- `{rarity_color}` - Rarity color code
- `{id}` - Reward ID

## ShopGUIPlus Integration

To sell warzone fish at different prices based on rarity:

```yaml
# In ShopGUIPlus shops/fishing.yml
common_cod:
  type: item
  item:
    material: RAW_FISH
    name: "&7Common Cod"
    lore:
      - "&8Just a regular fish..."
  sellPrice: 5
  compareMeta: true  # IMPORTANT: matches by name/lore

rare_cod:
  type: item
  item:
    material: RAW_FISH
    name: "&3&lPrize Cod"
    lore:
      - "&7An exceptional specimen!"
  sellPrice: 100
  compareMeta: true
```

## PhoenixCrates Integration

Give crate keys as fishing rewards:

```yaml
fishing_key:
  type: COMMAND
  commands:
    - "phoenixcrates key give {player} fishing 1"
  chance: 3.0
  rarity: RARE
  broadcast: true
  broadcast-message: "&3{player} found a Fishing Crate Key!"
```

## Dependencies

- **Required:** Spigot 1.8.8
- **Optional:** Factions, FactionsUUID, WorldGuard, Vault

## Building

```bash
cd WarzoneFishing
mvn clean package
```

The JAR will be in `target/WarzoneFishing-2.0.0.jar`

## Support

For issues or feature requests, contact the server administrator.

---

*WarzoneFishing v2.0.0 - Teal Theme Edition*
