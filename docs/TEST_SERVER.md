# Test server — Raspberry Pi 5

Status: **live**, verified 2026-11-11.

## Connect

- Address: `192.168.4.57:25565`
- Version: Minecraft Java Edition **26.3** (vanilla)
- Host: `rdh@192.168.4.57`; SSH alias `pi` / SSH MCP profile `pi-lan`
- World: `world`, seed `swarm-test-26.3`
- Mode: survival, normal difficulty, 4 player slots
- `online-mode=false`, whitelist off — **LAN test server only**

## Host layout

```text
/home/rdh/minecraft-test/
  server.jar          official Mojang 26.3 server
  jre/                Temurin 25.0.5, aarch64
  start.sh            starts with a PID file
  stop.sh             SIGTERM, lets the server save
  server.properties   gameplay + RCON settings
  server.out          console log
  logs/latest.log     Minecraft log
  world/              generated test world
```

Java 25 is local to this directory; no system Java was installed and no
`sudo` was used.

## Lifecycle

```bash
ssh pi
cd /home/rdh/minecraft-test
./start.sh          # no-op if already running
./stop.sh           # graceful stop
tail -f server.out
```

`@reboot` is installed in `rdh`'s crontab and clears the stale PID before
starting. Reboot autostart has **not** been exercised yet.

Heap is `-Xms512M -Xmx1280M`; view distance 5, simulation distance 3. The Pi has
4 GB total and also runs Jellyfin, the *arr stack, and Docker services, so watch
memory before raising these values.

## RCON

- `127.0.0.1:25575` works; the password is in `server.properties` and is not
  recorded here.
- Vanilla 26.3 has no `rcon.bind`; with `server-ip` blank, RCON listens on all
  interfaces. The 64-character password was rotated after an earlier diagnostic
  print exposed the first value.
- `25575` is reachable from the Windows machine. Do **not** port-forward it.
  A Pi firewall rule is the next hardening step; no firewall tool is installed
  in the current user setup.
- If RCON is not needed yet, set `enable-rcon=false` and restart.

## Harness bridge

The test-only client bridge is off unless each Prism instance has
`config/enhanced-swarm-harness.json`:

```json
{
  "enabled": true,
  "port": 8899,
  "token": "<16-256 character random token>",
  "allowActions": false
}
```

Use `8899` for the host and `8898` for the worker, with separate tokens. The
bridge binds only `127.0.0.1` and requires `X-Harness-Token` or `?token=`.
The read-only endpoints are `health`, `state`, `screen`, `menu`, and
`screenshot`. `allowActions` defaults to `false`; when enabled, the only action
is `POST /harness/menu/click` with a slot-only `QUICK_MOVE` request.

## Verified

- Official server jar SHA-1 matches
  `33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c`.
- `java -version` is Temurin `25.0.5`.
- Server reached `Done (0.397s)!` after a clean restart.
- RCON `list` returned `There are 0 of a max of 4 players online:`.
- Windows TCP checks succeeded for `25565` and `25575`.
- No Minecraft client was launched.

## Not yet verified

- A real host/worker client joining the server.
- Two- or four-player load.
- Autostart after a Pi reboot.
- Firewall isolation of RCON.
