"""Staged 1v1 shield duel against the local worker harness.

Control run: shield forced off. Experiment: shield forced on.
Same arena, same invulnerable zombie, same sampling window. Prints the hp
traces so the difference is evidence, not a claim.
"""
import json
import time
import urllib.request

INST = r"C:/Users/rdh/Desktop/PineconeMC-Windows-MSVC-Portable-11.1.2"
HOST = json.load(open(INST + "/instances/main/.minecraft/config/enhanced-swarm-harness.json"))["token"]
BOT = json.load(open(INST + "/instances/bot/.minecraft/config/enhanced-swarm-harness.json"))["token"]
HOST_URL = "http://127.0.0.1:8899/harness"
BOT_URL = "http://127.0.0.1:8898/harness"


def post(url, path, body):
    req = urllib.request.Request(
        f"{url}/{path}?token={HOST if '8899' in url else BOT}",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    return json.load(urllib.request.urlopen(req))


def command(name, enabled=None, clear=False):
    body = {"name": name}
    if clear:
        body["clear"] = True
    elif enabled is not None:
        body["enabled"] = enabled
    return post(BOT_URL, "force", body)


def chat(message):
    post(HOST_URL, "chat", {"message": message})
    time.sleep(0.6)


def state():
    return json.load(urllib.request.urlopen(f"{BOT_URL}/state?token={BOT}"))


def sample(seconds):
    hp, blocking, out = [], False, []
    end = time.time() + seconds
    while time.time() < end:
        d = state()
        p = d.get("player") or {}
        hp.append(p.get("health"))
        blocking = blocking or bool(d.get("shieldBlocking"))
        out.append((p.get("health"), d.get("shieldBlocking")))
        time.sleep(0.15)
    return hp, blocking, out


def kill_duel():
    chat('/data merge entity @e[tag=duel,limit=1] {Invulnerable:0b}')
    chat("/kill @e[tag=duel]")


def spawn_duel():
    chat('/summon zombie -513 150 602 {Invulnerable:1b,Tags:["duel"]}')
    time.sleep(1)
    chat("/execute if entity @e[tag=duel] run say DUEL_OK")


def setup(shield_on):
    chat("/difficulty easy")
    chat("/time set night")
    chat("/tp jamal -516 150 602")
    chat("/attribute jamal minecraft:max_health base set 20")
    chat("/effect give jamal minecraft:instant_health 1 20 true")
    chat("/give jamal minecraft:iron_sword 1")
    post(BOT_URL, "slot", {"slot": 0})
    post(BOT_URL, "stop-path", {})
    command("kill-aura", True)
    command("swarm-shield", shield_on)
    if shield_on:
        chat("/give jamal minecraft:shield 1")
    time.sleep(3)
    d = state()
    p = d.get("player") or {}
    inv = [i for i in d.get("inventory", []) if i.get("index") == 40]
    print(
        f"[setup shield_on={shield_on}] hp={p.get('health')} main={p.get('mainHand')} "
        f"offhand={[i.get('item', {}).get('id') for i in inv]} "
        f"shieldMod={d.get('swarmShield')} aura={d.get('killAura')}"
    )
    return d


def run(label, shield_on):
    kill_duel()
    setup(shield_on)
    spawn_duel()
    hp, blocking, trace = sample(14)
    hp_clean = [h for h in hp if h is not None]
    print(f"[{label}] min={min(hp_clean)} max={max(hp_clean)} "
          f"first={hp_clean[0]} last={hp_clean[-1]} blocking_seen={blocking}")
    drops = [b for a, b in zip(hp_clean, hp_clean[1:]) if b < a]
    print(f"[{label}] hp drop events={len(drops)} drops={drops[:10]}")
    kill_duel()
    return min(hp_clean), blocking


print("=== CONTROL: shield off ===")
control_min, control_blocking = run("control", False)
chat("/effect give jamal minecraft:instant_health 1 20 true")

print("=== EXPERIMENT: shield on ===")
shield_min, shield_blocking = run("shield", True)

print("=== RESULT ===")
print(f"control min_hp={control_min} blocking={control_blocking}")
print(f"shield  min_hp={shield_min} blocking={shield_blocking}")
print("PASS" if shield_min > control_min and shield_blocking else "INCONCLUSIVE")
