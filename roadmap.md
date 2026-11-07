# EnhancedSwarm Roadmap

## Current Foundation
- Autonomous gather / craft / smelt / deposit pipeline
- Dependency resolution for tool and material crafting
- Host / worker architecture
- Deposit chest categories
- Web panel
- Return-to-base flow
- Stable localserver testing baseline

## Next Priorities

### 1. Base Anchor System DONE!!!!
- Add authoritative base anchor position
- Commands:
  - `.eswarm base set`
  - `.eswarm base clear`
  - `.eswarm base show`
- Use base anchor for:
  - return target
  - chest scan center
  - utility search center
  - mining exclusion radius

### 2. Gather Away From Base DONEish
- Add configurable gather offset from base
- Default mining zone 100–200 blocks from base
- Prevent terrain damage near storage/utilities
- Optional future named gather zones:
  - cave
  - wood
  - stone
  - emergency

### 3. Reserved Survival Inventory
- Reserve slots/items for hostile-server use
- Keep in inventory:
  - tools
  - armor
  - food
  - totems
- Prevent deposit system from unloading protected gear
- Make inventory calculations aware of reserved slots

### 4. Chest Scanning / Storage Intelligence
- Scan known chest categories near base
- Track item counts / availability
- Show low-stock states in web panel
- Use for manual refill task planning

### 5. Web Panel Warnings
- Show low / healthy / critical state for:
  - tools
  - armor
  - food
  - fuel
  - empty shulkers
- Surface warnings without forcing full autonomy

## Major Feature Milestone

### 6. Bulk Gather With Shulker Logistics
- Support tasks larger than raw inventory capacity
- Calculate required shulkers for requested quantity
- Pull empty shulkers from `empty_shulker` chest
- Gather until shulkers are filled
- Deposit filled shulkers into ender chest for safety
- Return home and unload shulkers
- Recycle empties back into shulker supply chest

## Future Ideas
- Named gather zones
- Utility self-rebuild if crafting table/furnace missing
- Automatic base stock target suggestions
- Multi-worker resource balancing
- Safer hostile-server pathing rules