import http from 'node:http';

const STUB_TOKEN = 'stub-harness-token-not-a-real-secret';
const MAX_BODY = 16 * 1024;
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64'
);

export async function startStub({
  port = 0,
  allowActions = true,
  corrupt = null
} = {}) {
  const menu = makeMenu();
  let actionInFlight = false;

  const server = http.createServer(async (request, response) => {
    try {
      if (request.headers['x-harness-token'] !== STUB_TOKEN) {
        return sendJson(response, 401, {
          ok: false,
          code: 'unauthorized',
          error: 'missing or invalid harness token'
        });
      }

      const url = new URL(request.url, 'http://stub');
      const pathname = url.pathname;

      if (request.method === 'GET') {
        switch (pathname) {
          case '/harness/health':
            return sendJson(response, 200, health());
          case '/harness/state':
            return sendJson(response, 200, state(menu, corrupt));
          case '/harness/screen':
            return sendJson(response, 200, screen(menu));
          case '/harness/menu':
            return sendJson(response, 200, menuSnapshot(menu));
          default:
            return sendJson(response, 404, {
              ok: false,
              code: 'not-found',
              error: 'unknown harness endpoint'
            });
        }
      }

      if (request.method === 'POST' && pathname === '/harness/screenshot') {
        response.writeHead(200, {
          'Content-Type': 'image/png',
          'Cache-Control': 'no-store',
          'Content-Length': PNG.length
        });
        return response.end(PNG);
      }

      if (request.method === 'POST' && pathname === '/harness/menu/click') {
        if (!allowActions) {
          return sendJson(response, 403, {
            ok: false,
            code: 'actions-disabled',
            error: 'actions are disabled; set allowActions=true in the harness config'
          });
        }
        if (actionInFlight) {
          return sendJson(response, 409, {
            ok: false,
            code: 'action-in-flight',
            error: 'another harness action is already in flight'
          });
        }

        actionInFlight = true;
        try {
          const body = await readJsonBody(request);
          const slot = parseSlot(body);
          if (!menu.open) {
            return sendJson(response, 409, {
              ok: false,
              code: 'menu-not-open',
              error: 'no container menu is open'
            });
          }
          if (slot >= menu.slots.length) {
            return sendJson(response, 400, {
              ok: false,
              code: 'invalid-slot',
              error: 'slot is outside the open menu'
            });
          }

          const before = snapshot(menu);
          const target = menu.slots[slot];
          let changed = false;
          if (target.item) {
            const destination = menu.slots.find(
              (candidate) =>
                candidate.playerSlot !== target.playerSlot && !candidate.item
            );
            if (destination) {
              destination.item = target.item;
              target.item = null;
              changed = true;
            }
          }

          await delay(75);
          const after = snapshot(menu);
          return sendJson(response, 200, {
            ok: true,
            result: {
              action: 'menu-click',
              slot,
              input: 'QUICK_MOVE',
              button: 0,
              changed,
              containerId: menu.containerId,
              stateId: menu.stateId
            },
            before,
            after
          });
        } finally {
          actionInFlight = false;
        }
      }

      return sendJson(response, 405, {
        ok: false,
        code: 'method-not-allowed',
        error: 'unsupported method for harness endpoint'
      });
    } catch (error) {
      if (response.headersSent) {
        response.destroy();
        return;
      }
      return sendJson(response, 400, {
        ok: false,
        code: 'bad-request',
        error: error instanceof Error ? error.message : String(error)
      });
    }
  });

  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', resolve);
  });

  const address = server.address();
  return {
    server,
    port: address.port,
    baseUrl: `http://127.0.0.1:${address.port}`,
    token: STUB_TOKEN,
    close: () => new Promise((resolve) => server.close(resolve))
  };
}

function makeMenu() {
  const slots = Array.from({ length: 90 }, (_, index) => ({
    index,
    slot: index,
    x: 8 + (index % 9) * 18,
    y: 18 + Math.floor((index % 54) / 9) * 18,
    coordinateSpace: 'menu-local',
    playerSlot: index >= 54,
    item: index === 0 ? item('minecraft:chest', 1) : null
  }));
  return {
    open: true,
    class: 'net.minecraft.world.inventory.ChestMenu',
    title: 'Large Chest',
    containerId: 1,
    stateId: 1,
    cursor: null,
    slots
  };
}

function item(id, count) {
  return {
    id,
    count,
    name: id === 'minecraft:chest' ? 'Chest' : 'Item',
    damage: 0,
    components: '{}'
  };
}

function health() {
  return {
    ok: true,
    inGame: true,
    screenOpen: true,
    menuOpen: true,
    version: 'stub'
  };
}

function state(menu, corrupt) {
  const result = {
    ok: true,
    inGame: true,
    screen: {
      open: true,
      class: 'net.minecraft.client.gui.screens.inventory.ContainerScreen',
      title: menu.title
    },
    menu: {
      open: menu.open,
      class: menu.class,
      containerId: menu.containerId
    },
    player: {
      name: 'stub-player',
      uuid: '00000000-0000-0000-0000-000000000001',
      health: 20,
      food: 20,
      x: 0,
      y: 64,
      z: 0,
      dimension: 'minecraft:overworld'
    },
    inventory: inventory(menu)
  };
  if (corrupt === 'state.player') delete result.player;
  return result;
}

function screen(menu) {
  return {
    ok: true,
    open: true,
    class: 'net.minecraft.client.gui.screens.inventory.ContainerScreen',
    title: menu.title,
    width: 320,
    height: 240,
    widgets: [
      {
        index: 0,
        type: 'Button',
        x: 0,
        y: 0,
        width: 40,
        height: 20,
        text: 'Close',
        active: true,
        visible: true
      }
    ]
  };
}

function menuSnapshot(menu) {
  return {
    ok: true,
    open: menu.open,
    class: menu.class,
    title: menu.title,
    containerId: menu.containerId,
    stateId: menu.stateId,
    cursor: menu.cursor,
    slots: menu.slots
  };
}

function inventory(menu) {
  return menu.slots
    .filter((slot) => slot.playerSlot)
    .map((slot, index) => ({
      index: slot.index,
      selected: index === 0,
      item: slot.item
    }));
}

function snapshot(menu) {
  return {
    menu: structuredClone(menuSnapshot(menu)),
    inventory: structuredClone(inventory(menu))
  };
}

function parseSlot(body) {
  if (!body || Object.keys(body).length !== 1 || !Object.hasOwn(body, 'slot')) {
    throw new Error('request body must contain only the slot field');
  }
  if (!Number.isInteger(body.slot) || body.slot < 0) {
    throw new Error('slot must be a non-negative integer');
  }
  return body.slot;
}

async function readJsonBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY) throw new Error('request body is too large');
    chunks.push(chunk);
  }
  if (chunks.length === 0) throw new Error('request body must be a JSON object');
  const parsed = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('request body must be a JSON object');
  }
  return parsed;
}

function sendJson(response, status, value) {
  const body = Buffer.from(JSON.stringify(value));
  response.writeHead(status, {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-store',
    'Content-Length': body.length
  });
  response.end(body);
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
