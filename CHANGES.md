# TimeBound Changes

## Added
- Added a default `config.yml` so the plugin starts cleanly on Paper 1.21.11.
- Added a mandatory `PersistentDataContainer` UID (`timebound_uid`) for all Time Clocks and Timeblades to support global tracking and duplicate detection.
- Added an asynchronous global scanning system (`GlobalTimeItemScanner`) with a thread-safe registry snapshot (`GlobalTimeItemRegistry`) that recomputes existence across:
  - Online player inventories and Ender Chests
  - Loaded world item entities and loaded container inventories
  - Offline `world/playerdata/*.dat` files (NBT scan for TimeBound PDC keys)
- Added `/timebound test <on|off>` (permission `timebound.test`) to switch duplicate enforcement into log-only mode, persisted in `config.yml`.
- Added the "Master of Time" legendary weapon:
  - Shaped recipe (blades in diamond/cross + Nether Star) with PDC-based validation (renamed fakes do not work)
  - Server-wide craft announcement + challenge advancement (`crafted_master`)
  - Multi-ability kit (dash/blink/slow/burst) with cooldowns + passive buffs while held
- Added server announcements when a Time weapon or Time Clock is destroyed by cactus contact or the void.
- Added ender chest blocking for Time weapons and Time Clocks. Existing TimeBound items found in player ender chests are moved back to the player inventory or dropped nearby if the inventory is full.

## Removed
- Removed the trial spawner listener and Brake Room Trial mechanics.
- Removed `/timebound generate` and `/timebound locate` tab-completion traces.
- Removed old structure schematic resources from the plugin package.
- Removed unused WorldEdit/FAWE soft-dependencies and the unused WorldEdit Maven dependency.
- Removed the entire `ChunkyMonitor` system and all `Chunky` references.
- Removed the old `claimed.*` config + loaded-duplicate cleanup system (replaced by the global UID registry).

## Refactored
- Centralized TimeBound item checks so clocks and weapons share the same protection rules.
- Adjusted duplicate ownership checks so ender chests are no longer treated as valid hidden storage for TimeBound items.
- Reworked duplicate prevention to be driven by global existence scans (strict mode) with a persisted test-mode override.
- Decoupled `/timebound spawnclock` from global ownership/claim state (admins can spawn displays regardless of whether the item currently exists).
