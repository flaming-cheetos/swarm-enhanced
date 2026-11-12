const DEFAULT_TIMEOUT_MS = 5000;

export class PanelConnectionError extends Error {
  constructor(baseUrl, cause) {
    const code = cause?.cause?.code ?? cause?.code ?? 'connection-failed';
    super(`no panel listening at ${baseUrl} (${code})`);
    this.name = 'PanelConnectionError';
    this.baseUrl = baseUrl;
    this.connectionCode = code;
  }
}

export class PanelContractError extends Error {
  constructor(message, details = {}) {
    super(message);
    this.name = 'PanelContractError';
    this.status = details.status;
    this.body = details.body;
  }
}

/**
 * The panel is the control plane: task add/cancel/pause/resume, state and logs.
 * The HarnessBridge is the client-truth plane: inventory, menu, screen, pixels.
 */
export class PanelAdapter {
  constructor({ baseUrl, token = '', timeoutMs = DEFAULT_TIMEOUT_MS }) {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.token = token;
    this.timeoutMs = timeoutMs;
    this.ws = null;
    this.state = null;
    this.logs = [];
    this.waiters = new Set();
    this.closed = false;
  }

  async connect() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN && this.state) {
      return this.state;
    }

    const url = new URL(this.baseUrl);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.pathname = '/ws';
    url.searchParams.set('token', this.token);

    this.ws = new WebSocket(url);
    this.ws.addEventListener('message', (event) => this.onMessage(event));
    this.ws.addEventListener('close', () => this.onClose());
    this.ws.addEventListener('error', (event) => this.onError(event));

    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        cleanup();
        reject(new PanelContractError(`panel WebSocket did not open at ${this.baseUrl}`));
      }, this.timeoutMs);
      const onOpen = () => {
        cleanup();
        resolve();
      };
      const onError = (event) => {
        cleanup();
        reject(errorFromEvent(event));
      };
      const cleanup = () => {
        clearTimeout(timer);
        this.ws.removeEventListener('open', onOpen);
        this.ws.removeEventListener('error', onError);
      };
      this.ws.addEventListener('open', onOpen);
      this.ws.addEventListener('error', onError);
    });

    await this.waitForState(() => true, this.timeoutMs);
    return this.state;
  }

  async close() {
    this.closed = true;
    for (const waiter of this.waiters) {
      clearTimeout(waiter.timer);
      waiter.reject(new PanelContractError('panel connection closed'));
    }
    this.waiters.clear();
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.close();
    }
  }

  async post(pathname, body = {}) {
    let response;
    try {
      response = await fetch(`${this.baseUrl}${pathname}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Swarm-Token': this.token
        },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(this.timeoutMs)
      });
    } catch (error) {
      if (isConnectionFailure(error)) {
        throw new PanelConnectionError(this.baseUrl, error);
      }
      throw error;
    }

    let parsed;
    const text = await response.text();
    try {
      parsed = text ? JSON.parse(text) : {};
    } catch {
      throw new PanelContractError(
        `${pathname} returned non-JSON body (HTTP ${response.status})`,
        { status: response.status, body: text }
      );
    }

    if (response.status !== 200) {
      throw new PanelContractError(
        `${pathname} returned HTTP ${response.status}`,
        { status: response.status, body: parsed }
      );
    }
    return parsed;
  }

  async taskAdd({ item, qty = 1, type = 'auto', bot = 'any' }) {
    return this.post('/api/task/add', { item, qty, type, bot });
  }

  async taskClearDone() {
    return this.post('/api/task/clear_done', {});
  }

  async taskCancel(bot) {
    return this.post('/api/task/cancel', { bot });
  }

  async taskPause(bot) {
    return this.post('/api/task/pause', { bot });
  }

  async taskResume(bot) {
    return this.post('/api/task/resume', { bot });
  }

  async action(name) {
    return this.post(`/api/action/${name}`, {});
  }

  async waitForState(predicate, timeoutMs = this.timeoutMs, label = 'panel state') {
    if (this.state && predicate(this.state)) return this.state;

    return new Promise((resolve, reject) => {
      const waiter = {
        predicate,
        resolve,
        reject,
        timer: setTimeout(() => {
          this.waiters.delete(waiter);
          reject(new PanelContractError(`timed out waiting for ${label} after ${timeoutMs}ms`));
        }, timeoutMs)
      };
      this.waiters.add(waiter);
    });
  }

  async waitForTask({
    item = null,
    type = null,
    status = null,
    bot = null,
    timeoutMs = this.timeoutMs,
    label = null
  } = {}) {
    const statuses = status === null
      ? null
      : new Set((Array.isArray(status) ? status : [status]).map((value) => String(value).toUpperCase()));
    const itemKey = item === null ? null : normalizeItem(item);
    const typeKey = type === null ? null : String(type).toUpperCase();
    const botKey = bot === null ? null : String(bot);

    return this.waitForState((state) => {
      const tasks = Array.isArray(state.tasks) ? state.tasks : [];
      return tasks.find((task) => {
        if (itemKey !== null && normalizeItem(task.item) !== itemKey) return false;
        if (typeKey !== null && String(task.type).toUpperCase() !== typeKey) return false;
        if (statuses !== null && !statuses.has(String(task.status).toUpperCase())) return false;
        if (botKey !== null && String(task.bot ?? '') !== botKey) return false;
        return true;
      });
    }, timeoutMs, label ?? `task ${item ?? type ?? 'any'} ${status ?? ''}`.trim())
      .then((state) => (state.tasks ?? []).find((task) => {
        if (itemKey !== null && normalizeItem(task.item) !== itemKey) return false;
        if (typeKey !== null && String(task.type).toUpperCase() !== typeKey) return false;
        if (statuses !== null && !statuses.has(String(task.status).toUpperCase())) return false;
        if (botKey !== null && String(task.bot ?? '') !== botKey) return false;
        return true;
      }));
  }

  async waitForBot({
    name = null,
    halted = null,
    timeoutMs = this.timeoutMs,
    label = null
  } = {}) {
    const wanted = name === null ? null : String(name);
    return this.waitForState((state) => {
      const bots = Array.isArray(state.bots) ? state.bots : [];
      return bots.find((bot) => {
        if (wanted !== null && String(bot.username) !== wanted && String(bot.nickname) !== wanted) return false;
        if (halted !== null && Boolean(bot.halted) !== Boolean(halted)) return false;
        return true;
      });
    }, timeoutMs, label ?? `bot ${name ?? 'any'} halted=${halted}`.trim())
      .then((state) => (state.bots ?? []).find((bot) => {
        if (wanted !== null && String(bot.username) !== wanted && String(bot.nickname) !== wanted) return false;
        if (halted !== null && Boolean(bot.halted) !== Boolean(halted)) return false;
        return true;
      }));
  }

  logsSince(mark = 0) {
    return this.logs.slice(mark);
  }

  onMessage(event) {
    let message;
    try {
      message = JSON.parse(String(event.data));
    } catch {
      return;
    }

    if (message.type === 'state') {
      this.state = message;
      this.notifyWaiters();
      return;
    }

    if (message.type === 'log') {
      this.logs.push(message);
      if (this.logs.length > 500) this.logs.shift();
      return;
    }

    if (message.type === 'log_history' && Array.isArray(message.logs)) {
      this.logs = message.logs.slice(-500);
    }
  }

  notifyWaiters() {
    for (const waiter of [...this.waiters]) {
      let matched = false;
      try {
        matched = Boolean(waiter.predicate(this.state));
      } catch {
        matched = false;
      }
      if (!matched) continue;
      this.waiters.delete(waiter);
      clearTimeout(waiter.timer);
      waiter.resolve(this.state);
    }
  }

  onClose() {
    if (this.closed) return;
    for (const waiter of this.waiters) {
      clearTimeout(waiter.timer);
      waiter.reject(new PanelContractError('panel connection closed'));
    }
    this.waiters.clear();
  }

  onError(event) {
    if (this.closed) return;
    const error = errorFromEvent(event);
    for (const waiter of this.waiters) {
      clearTimeout(waiter.timer);
      waiter.reject(error);
    }
    this.waiters.clear();
  }
}

function normalizeItem(value) {
  return String(value ?? '').replace(/^minecraft:/, '');
}

function errorFromEvent(event) {
  return new PanelContractError(
    `panel WebSocket failed${event?.message ? `: ${event.message}` : ''}`
  );
}

function isConnectionFailure(error) {
  const code = error?.cause?.code ?? error?.code;
  return (
    code === 'ECONNREFUSED' ||
    code === 'ENOTFOUND' ||
    code === 'EHOSTUNREACH' ||
    code === 'ENETUNREACH' ||
    code === 'ECONNRESET'
  );
}
