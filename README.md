# IpsecuzPet

IpsecuzPet is a Minecraft Paper/Folia pet RPG plugin with collectible pets, pet stats, leveling, capture balls, shop purchases, duels, mounts, custom models, and configurable messages.

## Features

- **Pet ownership system**: players can own, summon, despawn, rename, withdraw, and redeem pets.
- **Pet GUI**: `/pet` opens the player pet profile menu, where pets can be summoned or stored with one click.
- **Pet shop GUI**: `/pet shop` opens a shop menu for buying pets from `config.yml`.
- **Multiple currency types**:
  - `ITEM` purchases using Minecraft materials.
  - `MONEY` purchases through Vault economy.
  - `POINTS` purchases through PlayerPoints.
- **RPG stat system**: pets have damage, health, defense, speed, and intelligence stats.
- **Leveling system**: active pets gain EXP when the owner kills monsters.
- **Configurable stat growth**: pet stats scale by level using values from `rpg_system.default_growth`.
- **Permission-based EXP multipliers**: grant permissions such as `ipsecuzpet.multiplier.1.5` or `ipsecuzpet.multiplier.2.0` for boosted pet EXP.
- **Pet buffs**: pets can apply potion effects to their owner, configured per pet.
- **Pet particles**: each pet can display a configurable particle effect.
- **Pet following AI**: pets follow their owner and teleport back when too far away.
- **Combat assist**: pets can target enemies attacked by the owner and respond when the owner is attacked.
- **Pet duel system**: players can challenge each other to pet duels with `/pet duel <player>` and `/pet accept`.
- **Pet death and revival**: dead pets are marked as dead and can be revived by mining diamond ore or deepslate diamond ore.
- **Capture system**: players can catch configured mobs using custom capture balls.
- **Capture ball rules**: each ball supports whitelist or blacklist capture modes, custom chances, materials, names, and lore.
- **Permission-based capture bonuses**: permissions like `ipsecuzpet.catch.vip` and `ipsecuzpet.catch.mvp` increase capture chance.
- **Pet item withdrawal**: `/pet withdraw <pet_id>` converts an inactive pet into a redeemable item that stores its level and EXP.
- **Mount support**: owners can right-click their pet to ride it.
- **Quick stats view**: shift-right-click an owned pet or use `/pet stats` to view pet stats.
- **BetterModel support**: pets can use BetterModel models via `model_id`, with idle/walk animation updates.
- **Folia support**: scheduling helpers support both Folia and Paper/Spigot-style servers.
- **Customizable language file**: messages are stored in `messages.yml` and support legacy color codes plus hex colors in `&#RRGGBB` format.
- **Admin tools**: admins can give pets, give capture balls, and reload plugin configuration.
- **Update checker**: admins are notified when a newer Spigot resource version is available.

## Requirements

- Java 21
- Paper/Folia-compatible Minecraft server
- Plugin API version: `1.19`
- Built against Paper API `1.20.4` and Folia API `1.20.1`

### Optional dependencies

These are only needed for the related features:

| Dependency | Used for |
| --- | --- |
| BetterModel | Custom pet models through `model_id` |
| Vault + an economy plugin | `MONEY` pet purchases |
| PlayerPoints | `POINTS` pet purchases |

## Installation

1. Build or download the plugin JAR.
2. Place the JAR in your server's `plugins` folder.
3. Install optional dependencies if you use BetterModel, Vault economy, or PlayerPoints features.
4. Restart the server.
5. Edit `plugins/IpsecuzPet/config.yml` and `plugins/IpsecuzPet/messages.yml` as needed.
6. Use `/pet reload` after changing configuration files.

## Building from source

This project uses Maven.

```bash
mvn clean package
```

The compiled plugin JAR will be generated in the `target/` folder.

> Note: the project references `libs/BetterModel.jar` with Maven `system` scope, so keep that file in place when building unless you change the dependency setup.

## Commands

Main command: `/ipsecuzpet`  
Alias: `/pet`

| Command | Description | Permission |
| --- | --- | --- |
| `/pet` | Open the pet profile GUI | `ipsecuzpet.use` |
| `/pet help` | Show the command list | `ipsecuzpet.use` |
| `/pet shop` | Open the pet shop GUI | `ipsecuzpet.use` |
| `/pet despawn` | Store the active pet | `ipsecuzpet.use` |
| `/pet stats` | Show active pet stats | `ipsecuzpet.use` |
| `/pet info` | Alias for `/pet stats` | `ipsecuzpet.use` |
| `/pet rename <name>` | Rename the active pet | `ipsecuzpet.rename` |
| `/pet withdraw <pet_id>` | Convert an inactive pet into a redeemable item | `ipsecuzpet.use` |
| `/pet duel <player>` | Send a pet duel request | `ipsecuzpet.use` |
| `/pet accept` | Accept a pet duel request | `ipsecuzpet.use` |
| `/pet give <player> <pet_id>` | Give a pet to a player | `ipsecuzpet.admin` |
| `/pet giveball <player> <ball_id> [amount]` | Give capture balls to a player | `ipsecuzpet.admin` |
| `/pet reload` | Reload config, messages, data, and capture balls | `ipsecuzpet.admin` |

## Permissions

| Permission | Description | Default / note |
| --- | --- | --- |
| `ipsecuzpet.use` | Allows basic pet commands, GUI, shop, duel, and help | `true` |
| `ipsecuzpet.admin` | Allows admin commands | `op` |
| `ipsecuzpet.rename` | Allows `/pet rename` | Assign with a permission plugin |
| `ipsecuzpet.maxslots.<amount>` | Raises a player's pet slot limit, for example `ipsecuzpet.maxslots.5` | Assign with a permission plugin |
| `ipsecuzpet.multiplier.1.5` | Gives 1.5x pet EXP | Configurable in `config.yml` |
| `ipsecuzpet.multiplier.2.0` | Gives 2.0x pet EXP | Configurable in `config.yml` |
| `ipsecuzpet.catch.vip` | Adds capture chance bonus from `capture_system.permission_bonus.vip` | Configurable in `config.yml` |
| `ipsecuzpet.catch.mvp` | Adds capture chance bonus from `capture_system.permission_bonus.mvp` | Configurable in `config.yml` |

## Default pet content

The default configuration includes **43 pets** across three purchase types:

| Currency type | Count | Examples |
| --- | ---: | --- |
| `ITEM` | 15 | Chicken, Pig, Cow, Skeleton, Creeper, Dolphin, Shulker, Parrot |
| `MONEY` | 16 | Wolf, Cat, Fox, Iron Golem, Blaze, Enderman, Turtle, Pillager |
| `POINTS` | 12 | Wither, Ender Dragon, Warden, Ravager, Allay, Frog, Phantom, Vex |

Each pet can define:

- Vanilla entity type
- Display name
- GUI icon
- Price and currency type
- Catchable status
- Particle effect
- Owner potion effects
- RPG stats
- Optional BetterModel `model_id`

## Capture balls

The default config includes three capture balls:

| Ball ID | Material | Base chance | Mode | Notes |
| --- | --- | ---: | --- | --- |
| `basic_ball` | Egg | 20% | Whitelist | Only catches selected passive mobs |
| `ultra_ball` | Snowball | 50% | Blacklist | Cannot catch boss-type mobs by default |
| `master_ball` | Slime Ball | 100% | Blacklist | Can catch all configured types unless blacklisted |

Capture balls are configured in `capture_system.items`.

## Configuration overview

Most gameplay settings are in `src/main/resources/config.yml` before building, or `plugins/IpsecuzPet/config.yml` after the plugin runs.

Important sections:

```yaml
max_pets: 2

rpg_system:
  max_level: 100
  base_exp_requirement: 50
  exp_per_kill: 10
  revive_cost_diamonds: 32
  default_growth:
    damage: 0.5
    health: 2.0
    defense: 0.2
    speed: 0.001
    intelligence: 0.5

pets:
  chicken_pet:
    type: CHICKEN
    name: "&eGà Con Lon Ton"
    icon: FEATHER
    price: 32
    currency: ITEM
    material: WHEAT_SEEDS
    catchable: true
    particle: "VILLAGER_HAPPY"
    effects: ["SLOW_FALLING:0"]
    stats:
      damage: 2.0
      health: 10.0
      defense: 0.0
      speed: 0.3
      intelligence: 5.0
```

## Adding a custom pet

Add a new entry under `pets:` in `config.yml`.

```yaml
pets:
  red_dragon_pet:
    type: ENDER_DRAGON
    name: "&cRed Dragon"
    model_id: "red_dragon"
    icon: DRAGON_HEAD
    price: 200
    currency: POINTS
    catchable: false
    particle: "FLAME"
    effects: ["INCREASE_DAMAGE:1", "FIRE_RESISTANCE:0"]
    stats:
      damage: 15.0
      health: 100.0
      defense: 5.0
      speed: 0.35
      intelligence: 10.0
```

If you use BetterModel, make sure `model_id` matches a valid BetterModel model key. The plugin attempts to play `idle` and `walk` animations when available.

## Data and messages

- `data.yml` stores player pet data, levels, EXP, status, custom names, and revive progress.
- `messages.yml` stores all user-facing messages.
- Messages support placeholders such as `%pet_name%`, `%player%`, `%level%`, `%exp%`, `%req%`, `%current%`, and `%max%` depending on the message.
- Color formats supported by the language manager:
  - Legacy colors: `&a`, `&e`, `&l`, etc.
  - Hex colors: `&#00AAFF`

## Notes for server owners

- Use a permission plugin to assign non-default permission nodes such as `ipsecuzpet.rename`, `ipsecuzpet.maxslots.<amount>`, EXP multipliers, and capture bonuses.
- Install Vault and an economy plugin before using `MONEY` pets.
- Install PlayerPoints before using `POINTS` pets.
- Configure BetterModel models before using custom `model_id` values.
- Always back up `data.yml` before making major changes to pet IDs or player data.
