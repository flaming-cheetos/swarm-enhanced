const DEFAULT_TIMEOUT_MS = 5000;

export class BridgeConnectionError extends Error {
  constructor(baseUrl, cause) {
    const code = cause?.cause?.code ?? cause?.code ?? 'connection-failed';
    super(`no client listening at ${baseUrl} (${code})`);
    this.name = 'BridgeConnectionError';
    this.baseUrl = baseUrl;
    this.connectionCode = code;
  }
}

export class BridgeContractError extends Error {
  constructor(message, details = {}) {
    super(message);
    this.name = 'BridgeContractError';
    this.status = details.status;
    this.body = details.body;
  }
}

export class BridgeAdapter {
  constructor({ baseUrl, token = '', timeoutMs = DEFAULT_TIMEOUT_MS }) {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.token = token;
    this.timeoutMs = timeoutMs;
  }

  async health() {
    return this.json('/harness/health');
  }

  async state() {
    return this.json('/harness/state');
  }

  async screen() {
    return this.json('/harness/screen');
  }

  async menu() {
    return this.json('/harness/menu');
  }

  async screenshot() {
    const response = await this.request('/harness/screenshot', { method: 'POST' });
    if (response.status !== 200) {
      throw new BridgeContractError(
        `screenshot returned HTTP ${response.status}`,
        { status: response.status, body: response.buffer.toString('utf8') }
      );
    }
    if (!response.contentType.toLowerCase().startsWith('image/png')) {
      throw new BridgeContractError(
        `screenshot content-type is not image/png: ${response.contentType || '<missing>'}`,
        { status: response.status }
      );
    }
    if (
      response.buffer.length < 8 ||
      response.buffer[0] !== 0x89 ||
      response.buffer[1] !== 0x50 ||
      response.buffer[2] !== 0x4e ||
      response.buffer[3] !== 0x47
    ) {
      throw new BridgeContractError('screenshot is not a PNG', { status: response.status });
    }
    return response.buffer;
  }

  async simulate(action, args = {}) {
    const response = await this.request('/harness/simulate', {
      method: 'POST',
      body: { action, ...args }
    });
    let body;
    try {
      body = JSON.parse(response.buffer.toString('utf8'));
    } catch {
      throw new BridgeContractError(
        `simulate returned non-JSON body (HTTP ${response.status})`,
        { status: response.status, body: response.buffer.toString('utf8') }
      );
    }
    if (response.status !== 200 || body?.ok !== true) {
      throw new BridgeContractError(
        `simulate returned HTTP ${response.status} without ok=true`,
        { status: response.status, body }
      );
    }
    return body;
  }

  async menuClick(slot) {
    if (!Number.isInteger(slot) || slot < 0) {
      throw new BridgeContractError('menuClick slot must be a non-negative integer');
    }
    const response = await this.request('/harness/menu/click', {
      method: 'POST',
      body: { slot }
    });
    let body;
    try {
      body = JSON.parse(response.buffer.toString('utf8'));
    } catch {
      throw new BridgeContractError(
        `menu/click returned non-JSON body (HTTP ${response.status})`,
        { status: response.status, body: response.buffer.toString('utf8') }
      );
    }
    return {
      status: response.status,
      body,
      contentType: response.contentType
    };
  }

  async json(path) {
    const response = await this.request(path, { method: 'GET' });
    let body;
    try {
      body = JSON.parse(response.buffer.toString('utf8'));
    } catch {
      throw new BridgeContractError(
        `${path} returned non-JSON body (HTTP ${response.status})`,
        { status: response.status, body: response.buffer.toString('utf8') }
      );
    }
    if (response.status !== 200) {
      throw new BridgeContractError(
        `${path} returned HTTP ${response.status}`,
        { status: response.status, body }
      );
    }
    if (!body || body.ok !== true) {
      throw new BridgeContractError(
        `${path} did not return {"ok":true}`,
        { status: response.status, body }
      );
    }
    return body;
  }

  async request(path, { method = 'GET', body = null } = {}) {
    const headers = {
      'X-Harness-Token': this.token
    };
    let payload;
    if (body !== null) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, {
        method,
        headers,
        body: payload,
        signal: AbortSignal.timeout(this.timeoutMs)
      });
    } catch (error) {
      if (isConnectionFailure(error)) {
        throw new BridgeConnectionError(this.baseUrl, error);
      }
      throw error;
    }

    return {
      status: response.status,
      contentType: response.headers.get('content-type') ?? '',
      buffer: Buffer.from(await response.arrayBuffer())
    };
  }
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
