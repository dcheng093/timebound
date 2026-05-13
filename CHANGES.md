# TimeBound Changes

## Added
- Added a default `config.yml` so the plugin starts cleanly on Paper 1.21.11.
- Added protection for Time weapons and Time Clocks dropped into lava or fire so they are not burned.
- Added server announcements when a Time weapon or Time Clock is destroyed by cactus contact or the void.
- Added ender chest blocking for Time weapons and Time Clocks. Existing TimeBound items found in player ender chests are moved back to the player inventory or dropped nearby if the inventory is full.

## Removed
- Removed the trial spawner listener and Brake Room Trial mechanics.
- Removed `/timebound generate` and `/timebound locate` tab-completion traces.
- Removed old structure schematic resources from the plugin package.
- Removed unused WorldEdit/FAWE soft-dependencies and the unused WorldEdit Maven dependency.

## Refactored
- Centralized TimeBound item checks so clocks and weapons share the same protection rules.
- Adjusted duplicate ownership checks so ender chests are no longer treated as valid hidden storage for TimeBound items.
