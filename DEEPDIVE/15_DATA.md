# 15 — Data Files

All persistent state is JSON under the Minecraft run directory. Two different roots are in use:

| root | files | writer |
|---|---|---|
| `<run>/meteor-client/enhanced-swarm/` | `task-queue.json`, `deposit-chests.json`, `storage-index.json` | TaskQueue, DepositConfig, StorageIndex |
| `<run>/enhanced-swarm/waypoints/<server>/waypoints.json` | per-server waypoints | WaypointManager |

`<server>` = sanitized `mc.getCurrentServerEntry().address` (`[^a-zA-Z0-9._-] → _`, lowercased); singleplayer →
`singleplayer_<levelName>`.

## task-queue.json
```json
{
  "tasks": [
    {"id":"<uuid>","type":"GATHER","priority":0,"item":"iron_ingot",
     "targetQuantity":64,"collected":12,"status":"ACTIVE","assignedBot":"Steve"}
  ],
  "nicknames": {"Steve":"Miner1"}
}
```
Write path: `TaskQueue.save()` (called from add/addTask/removeById/setNickname/clearStale/save). Load: `load()` skips
COMPLETE/FAILED, re-resolves items via `Registries.ITEM.get(Identifier.tryParse(item))`, then `clearStale()` (another save).
Note: `Item` identity in `data` map is rebuilt at load; if an id is unknown the task still loads with a null item.

## deposit-chests.json
Shape (inferred from `getChests` returning `Map<String,List<int[]>>` plus a separate base):
```json
{
  "play.example.com": {
    "base": [100, 64, -20],
    "chests": {
      "ores": [[101, 64, -20], [102, 64, -20]],
      "valuables": [[110, 64, -20]]
    }
  }
}
```
API guarantees multiple chests per category (list of int[3]), one base triplet per server, `removeChest(server,cat,indexOneBased)`.

## storage-index.json
```json
{
  "play.example.com": {
    "ores": [
      {"x":101,"y":64,"z":-20,"category":"ores",
       "counts":{"minecraft:iron_ingot":32,"minecraft:diamond":4},
       "lastScan":1730000000000,"status":"FRESH",
       "usedSlots":12,"totalSlots":27,"fullness":0.44}
    ]
  }
}
```
`ChestStatus` JSON values: `FRESH|STALE|UNREACHABLE|MISSING`. `applyChestResult` merges reported counts and saves.

## waypoints.json (per server)
```json
{
  "base": {"name":"base","category":"base","x":100,"y":64,"z":-20,
           "netherX":12,"netherY":70,"netherZ":-3,"isNether":false}
}
```
Actual field names come from `SwarmWaypoint`; the map is `Map<String, SwarmWaypoint>` keyed by lowercase name. Both
overworld and nether coordinate triplets are stored per waypoint (`setCoords(x,y,z,isNether)`).

## Files that do NOT exist
- No `worker-configs.json` (WorkerConfig is in-memory only).
- No settings sync file; `SETTINGS:` is transient.
- No `deaths.json` (DeathTracker.load is a no-op placeholder).
- No bundled example JSONs in the repo (nothing to copy from; shapes above are reconstructed from the code paths).

## Coupling notes
- Three independent Gson instances (DepositConfig, StorageIndex, WaypointManager) each with pretty printing; TaskQueue
  builds a new Gson per save.
- All writes are synchronous on the calling thread (including inside `synchronized` queue methods and the Netty/2 s
  scheduler path for storage), so heavy task churn = disk churn.
