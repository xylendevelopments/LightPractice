# LightPractice

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.8.9-62B47A?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft">
  <img src="https://img.shields.io/badge/Java-8-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java">
  <img src="https://img.shields.io/badge/MongoDB-Database-47A248?style=for-the-badge&logo=mongodb&logoColor=white" alt="MongoDB">
  <img src="https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge" alt="License">
</p>

<p align="center">
  <strong>⚙️ A competitive Minecraft 1.8.9 practice core built for fast-paced PvP servers.</strong>
</p>

---

## ⚔️ About

**LightPractice** is a modular Minecraft practice core designed for competitive PvP networks.

It provides everything needed to run a complete practice server, including:

* Duels
* Ranked & unranked queues
* ELO
* Arenas
* Kits
* Parties
* Tournaments
* Events
* Bots
* Spectators
* Match history
* Statistics
* Leaderboards
* Cosmetics
* Progression
* Developer API

The project is designed around performance, modularity, and maintainability.

---

## ⚙️ Features

| System          | Features                                 |
| --------------- | ---------------------------------------- |
| ⚔️ Matches      | Solo, Team, FFA, Party & Bot matches     |
| 🎯 Queues       | Ranked, Unranked, ELO & matchmaking      |
| 🏆 Competitive  | Divisions, ELO & leaderboards            |
| 🗺️ Arenas      | Standalone arenas, schematics & resets   |
| 🧰 Kits         | Custom kits, editing & kit rules         |
| 👥 Parties      | Party FFA, split & party duels           |
| 🏟️ Tournaments | Automated tournaments & brackets         |
| 🤖 Bots         | Configurable PvP practice bots           |
| 👁️ Spectators  | Spectating & match observation           |
| 📊 Statistics   | Wins, losses, ELO & match history        |
| ✨ Cosmetics     | Kill effects, messages, trails & rewards |
| 💰 Progression  | Coins, XP, levels & daily rewards        |
| 🔌 API          | Public developer API                     |

---

## 🎮 Supported Modes

LightPractice is built to support a wide variety of PvP modes:

* BedFight
* BlockFight
* Boxing
* Combo
* Sumo
* NoDebuff
* Debuff
* BuildUHC
* UHC
* Pearl Fight
* Fireball Fight
* BattleRush
* Bridges
* TNT Sumo
* Spleef
* Crystal PvP
* OITC

Additional modes can be added without changing the core architecture.

---

## 🏗️ Architecture

LightPractice uses a modular package structure.

```text
gg.lightpractice
├── LightPractice.java
│
├── command
│   ├── DuelCommand.java
│   ├── QueueCommand.java
│   ├── PartyCommand.java
│   └── TournamentCommand.java
│
├── manager
│   ├── MatchManager.java
│   ├── QueueManager.java
│   ├── ArenaManager.java
│   └── KitManager.java
│
├── service
│   ├── MatchService.java
│   ├── QueueService.java
│   └── ProfileService.java
│
├── listener
│   ├── PlayerListener.java
│   ├── MatchListener.java
│   └── InventoryListener.java
│
├── arena
│   ├── Arena.java
│   ├── ArenaManager.java
│   └── ArenaResetService.java
│
├── kit
│   ├── Kit.java
│   ├── KitManager.java
│   └── KitEditor.java
│
├── match
│   ├── Match.java
│   ├── MatchState.java
│   └── MatchType.java
│
├── queue
│   ├── Queue.java
│   ├── QueueEntry.java
│   └── MatchmakingService.java
│
├── party
├── tournament
├── bot
├── profile
├── statistics
├── leaderboard
├── spectator
├── cosmetic
├── gui
├── scoreboard
├── tab
├── database
├── api
├── integration
├── config
├── model
└── util
```

Files are organized by responsibility instead of placing everything inside one package.

Example:

```text
command/
└── DuelCommand.java

match/
└── Match.java

queue/
└── Queue.java

arena/
└── Arena.java
```

---

## 🗺️ Arena System

The arena system supports:

* Standard arenas
* Standalone arenas
* Arena duplication
* Spawn points
* Boundaries
* Kit whitelists
* Schematics
* Automatic resets
* FAWE-based resets

Arenas can be reused across multiple matches without manually rebuilding them.

---

## 🧰 Kit System

Kits control the rules and inventory of a match.

Each kit can define:

* Inventory
* Armor
* Potions
* Enchantments
* Game rules
* Arena whitelist
* Queue availability
* Duel availability
* Ranked availability
* Unranked availability
* Party support

Kit editing is completely configurable.

---

## 🎯 Queue System

LightPractice supports:

```text
Ranked
Unranked
Solo
Team
Party
```

Matchmaking can consider:

* ELO
* Ping
* Kit
* Team size
* Queue type
* Arena availability
* Configured matchmaking limits

---

## 👥 Parties

Players can create parties for:

* Party FFA
* Party Split
* Party vs Party
* Party Duels
* Party Events

Party settings and permissions are configurable.

---

## 🏆 Tournaments

LightPractice includes a tournament system supporting:

* Hosts
* Participants
* Brackets
* Match creation
* Kit rules
* Arena allocation
* Spectators
* Cooldowns
* Automated progression

---

## 🤖 Bots

LightPractice bots provide configurable PvP practice.

Bot settings can include:

* Difficulty
* CPS
* Movement
* Strafing
* Knockback
* Reach behavior
* Sprinting
* Healing
* Kit-specific behavior

---

## 📊 Statistics

Player statistics can include:

```text
Wins
Losses
Kills
Deaths
Winstreak
ELO
Games Played
Playtime
```

Statistics can be tracked globally or per kit.

---

## ✨ Cosmetics

Players can unlock cosmetics such as:

* Kill effects
* Kill messages
* Trails
* Particles
* Victory effects

Cosmetics can integrate with the progression system.

---

## 🗄️ Database

LightPractice uses **MongoDB** for persistent player data.

Possible stored data includes:

* Profiles
* Statistics
* Match history
* Kits
* ELO
* Divisions
* Cosmetics
* Settings
* Progression

Temporary match state remains in memory whenever possible.

---

## 🔌 Developer API

LightPractice exposes a public API under:

```text
gg.lightpractice.api
```

External plugins can interact with:

* Profiles
* Arenas
* Kits
* Queues
* Matches
* Parties
* Tournaments
* Statistics
* Leaderboards
* Spectators

Example:

```java
LightPracticeAPI.getProfileService();
LightPracticeAPI.getMatchService();
LightPracticeAPI.getArenaService();
```

---

## 📦 Dependencies

### Required

* Minecraft 1.8.9
* Java 8
* MongoDB
* FastAsyncWorldEdit

### Optional

* ProtocolLib
* PlaceholderAPI
* Citizens
* Vault
* ViaVersion

Optional integrations are loaded only when their dependencies are available.

---

## ⚙️ Configuration

Configuration is separated by system:

```text
plugins/
└── LightPractice/
    ├── config.yml
    ├── database.yml
    ├── messages.yml
    ├── arenas.yml
    ├── kits.yml
    ├── queues.yml
    ├── tournaments.yml
    ├── cosmetics.yml
    └── gui.yml
```

Gameplay values should be configurable instead of hardcoded throughout the Java source.

---

## 💻 Commands

Core commands include:

```text
/duel
/queue
/randomqueue
/party
/tournament
/arena
/kit
/spectate
/bot
/profile
/leaderboard
/lightpractice
```

Commands provide:

* Permissions
* Tab completion
* Argument validation
* Configurable messages
* Error handling

---

## 🛠️ Development

LightPractice is designed around clean and maintainable Java code.

Development principles:

* Modular architecture
* Clear class responsibilities
* Constructor-based dependency injection
* Minimal static state
* No duplicate managers
* No duplicate listeners
* No unnecessary NMS
* Efficient memory usage
* Minimal database operations
* Configurable systems
* Public API isolation

---

## 📈 Project Goals

LightPractice aims to provide a lightweight but complete practice core capable of handling competitive Minecraft PvP environments without sacrificing:

**Performance · Stability · Scalability · Maintainability**

---

## 📜 License

LightPractice is a private/proprietary project unless otherwise specified by the project owner.

---

<p align="center">
  <strong>LightPractice</strong><br>
  Built for competitive Minecraft PvP.
</p>
