'use strict';

const readline = require('node:readline');

const endpointText = process.env.BLOCKPAL_ENDPOINT || 'http://localhost:8000/blockpal';
const token = process.env.BLOCKPAL_TOKEN || '';
let endpoint;
try {
  endpoint = new URL(endpointText);
} catch (error) {
  console.error('[BlockPal bridge] Invalid BLOCKPAL_ENDPOINT:', endpointText);
  process.exit(1);
}

if (!token) {
  console.error('[BlockPal bridge] BLOCKPAL_TOKEN is missing. Reconfigure the extension with the token shown by /ai mcp token.');
  process.exit(1);
}

let sessionId = null;
let protocolVersion = null;
let legacyPostUrl = null;
let legacySseStarted = false;

function log(...args) {
  console.error('[BlockPal bridge]', ...args);
}

function out(obj) {
  process.stdout.write(JSON.stringify(obj) + '\n');
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function legacySseUrl() {
  const url = new URL(endpoint);
  url.pathname = '/sse';
  url.search = '';
  url.hash = '';
  return url;
}

function headersFor(msg) {
  const headers = {
    'content-type': 'application/json',
    'accept': 'application/json, text/event-stream',
    'authorization': `Bearer ${token}`
  };
  const version = msg?.params?.protocolVersion || protocolVersion;
  if (version) headers['mcp-protocol-version'] = version;
  if (sessionId) headers['mcp-session-id'] = sessionId;
  return headers;
}

async function parseSseBody(response) {
  if (!response.body) return;
  const decoder = new TextDecoder();
  let buffer = '';

  for await (const chunk of response.body) {
    buffer += decoder.decode(chunk, { stream: true }).replace(/\r\n/g, '\n');
    let boundary;
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      let event = 'message';
      const data = [];

      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
      }

      const payload = data.join('\n');
      if (!payload) continue;

      if (event === 'endpoint') {
        legacyPostUrl = new URL(payload, legacySseUrl());
        log('Legacy SSE POST endpoint:', legacyPostUrl.toString());
        continue;
      }

      try {
        const obj = JSON.parse(payload);
        log('Forwarding SSE JSON-RPC message to Claude:', obj.method || `response id=${obj.id}`);
        out(obj);
      } catch (_) {
        log('Ignoring non-JSON SSE payload:', payload.slice(0, 240));
      }
    }
  }
}

async function startLegacySse() {
  if (legacySseStarted) return;
  legacySseStarted = true;
  const sseUrl = legacySseUrl();

  try {
    log('Trying legacy SSE GET', sseUrl.toString());
    const response = await fetch(sseUrl, {
      headers: {
        accept: 'text/event-stream',
        authorization: `Bearer ${token}`
      }
    });
    log('Legacy SSE GET status:', response.status, 'content-type:', response.headers.get('content-type') || '(none)');
    if (!response.ok) throw new Error(`legacy SSE GET returned HTTP ${response.status}`);
    await parseSseBody(response);
  } catch (error) {
    log('Legacy SSE stopped:', error && error.stack ? error.stack : String(error));
  }
}

async function send(msg) {
  if (msg?.params?.protocolVersion) protocolVersion = msg.params.protocolVersion;
  const target = legacyPostUrl || endpoint;
  log(
    'Claude -> BlockPal:',
    msg.method || `response id=${msg.id}`,
    'id=', msg.id ?? '(notification)',
    'target=', target.toString(),
    'protocol=', protocolVersion || '(unknown)'
  );

  let response;
  try {
    response = await fetch(target, {
      method: 'POST',
      headers: headersFor(msg),
      body: JSON.stringify(msg)
    });
  } catch (error) {
    log('HTTP connection failed:', error && error.stack ? error.stack : String(error));
    if (msg?.id != null) {
      out({
        jsonrpc: '2.0',
        id: msg.id,
        error: {
          code: -32000,
          message: `BlockPal connection failed: ${error.message}`
        }
      });
    }
    return;
  }

  const sid = response.headers.get('mcp-session-id');
  if (sid) sessionId = sid;
  const contentType = (response.headers.get('content-type') || '').toLowerCase();
  log(
    'BlockPal HTTP response:',
    response.status,
    response.statusText || '',
    'content-type=', contentType || '(none)',
    'session=', sid || '(none)'
  );

  if (!legacyPostUrl && [400, 404, 405].includes(response.status) && msg?.method === 'initialize') {
    let preview = '';
    try {
      preview = await response.clone().text();
    } catch (_) {
      // Diagnostics only.
    }
    log('Modern initialize rejected. Body preview:', preview.slice(0, 500) || '(empty)');

    let isJsonRpcError = false;
    try {
      isJsonRpcError = Boolean(JSON.parse(preview)?.jsonrpc);
    } catch (_) {
      // Non-JSON response; legacy fallback is worth trying.
    }

    if (!isJsonRpcError) {
      log('Attempting legacy HTTP+SSE transport fallback...');
      void startLegacySse();
      for (let i = 0; i < 50 && !legacyPostUrl; i++) await sleep(100);
      if (legacyPostUrl) return send(msg);
    }
  }

  if (response.status === 202 || response.status === 204) {
    log('No response body expected for HTTP', response.status);
    return;
  }

  if (contentType.includes('text/event-stream')) {
    log('Reading Streamable HTTP SSE response...');
    return parseSseBody(response);
  }

  const text = await response.text();
  log('BlockPal response body preview:', text.slice(0, 800) || '(empty)');

  if (!text) {
    if (msg?.id != null) {
      out({
        jsonrpc: '2.0',
        id: msg.id,
        error: {
          code: -32001,
          message: `BlockPal returned HTTP ${response.status} with an empty response`
        }
      });
    }
    return;
  }

  try {
    const obj = JSON.parse(text);
    log('Forwarding JSON-RPC to Claude:', obj.method || `response id=${obj.id}`);
    out(obj);
  } catch (_) {
    log('BlockPal returned non-JSON:', text.slice(0, 500));
    if (msg?.id != null) {
      out({
        jsonrpc: '2.0',
        id: msg.id,
        error: {
          code: -32002,
          message: `BlockPal HTTP ${response.status}: ${text.slice(0, 300)}`
        }
      });
    }
  }
}

log('Starting. Node', process.version, 'endpoint=', endpoint.toString());
log('fetch available=', typeof fetch === 'function');

const input = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
input.on('line', line => {
  const trimmed = line.trim();
  if (!trimmed) return;
  try {
    const msg = JSON.parse(trimmed);
    void send(msg);
  } catch (error) {
    log('Invalid JSON from Claude:', error && error.stack ? error.stack : String(error));
  }
});
input.on('close', () => log('Claude closed stdin; bridge is shutting down.'));

process.on('uncaughtException', error => log('uncaughtException:', error && error.stack ? error.stack : String(error)));
process.on('unhandledRejection', error => log('unhandledRejection:', error && error.stack ? error.stack : String(error)));
