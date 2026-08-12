# MCP server — connect Claude, ChatGPT, Grok or Gemini

BlockPal can host a **Model Context Protocol (MCP)** server inside Minecraft. An AI app connects to your world and drives your companion with tools instead of requiring another model API key inside the game.

Run:

```text
/ai connection set mcp
/ai mcp
```

The in-game guide shows the MCP address and your access token. By default the local endpoint is:

```text
http://localhost:8000/blockpal
```

The game must stay running while an AI app is using BlockPal.

---

## What the AI gets

BlockPal exposes player-shaped tools rather than an admin console:

| Tool | What it does |
|------|--------------|
| `list_bots` | List companions in the world |
| `select_bot` | Choose which companion to drive |
| `look` | See the companion's rendered view plus scene text |
| `observe` | Read position, facing, health, inventory and nearby state |
| `api_reference` | Read the scripting API BlockPal understands |
| `run_code` | Run movement/mining/building/container scripts |
| `script_status` | Check a running script and its log |
| `stop` | Release movement/actions |
| `say` | Speak in game chat |
| `inventory` | Read held, worn and carried items |

There is deliberately no teleport, set-block, or whole-map tool.

---

## Claude Desktop — use the BlockPal MCPB extension

**Do not use the old `claude_desktop_config.json` + `npx mcp-remote` instructions.** That route depends on a system Node/npm installation and can fail when Claude Desktop cannot find `npx` or when its environment differs from your terminal.

BlockPal now includes a self-contained **`.mcpb` Claude Desktop extension** in the repository under `mcpb/`. The bridge runs with Claude Desktop's Node runtime and talks directly to BlockPal's localhost HTTP MCP server. It has no npm dependencies and does not require `npx`.

### Install

1. Start Minecraft and open the world/server containing BlockPal.
2. Run `/ai connection set mcp`.
3. Run `/ai mcp` and copy the **address** and **token**.
4. Download/build `BlockPal.mcpb` from this repository. CI also produces a `BlockPal-Claude-Desktop-MCPB` workflow artifact.
5. In Claude Desktop open **Settings → Extensions → Advanced settings → Install Extension…**.
6. Select `BlockPal.mcpb`.
7. Enter the MCP address and token when Claude asks.
8. Open a new Claude chat and enable/use the BlockPal tools.

The extension defaults to `http://localhost:8000/blockpal`, but the address is configurable for a non-default port.

### Build the MCPB from source

From the repository root:

```bash
python mcpb/build.py
```

This creates:

```text
mcpb/dist/BlockPal.mcpb
```

The bundle contains `manifest.json` and a small CommonJS stdio↔HTTP bridge. The token is **not** committed into the bundle; Claude Desktop collects it as sensitive user configuration and passes it at runtime.

### Troubleshooting Claude Desktop

Open **Settings → Developer → BlockPal → View Logs**. The bridge writes diagnostics to stderr, including the endpoint, HTTP status and connection failures.

Common failures:

- **Connection refused** — Minecraft/BlockPal is not running, MCP is not the selected connection, or the port is wrong.
- **401 Unauthorized** — the token entered into the extension does not match `/ai mcp token`.
- **Server disconnected immediately** — check the BlockPal bridge log. The bundled bridge should not call `npx`; if it does, an older extension is still installed.
- **No tools** — summon a companion and verify the server responds to `tools/list`.

---

## Claude Code

Claude Code runs locally and can connect directly over HTTP without the Desktop MCPB bridge:

```bash
claude mcp add --transport http blockpal http://localhost:8000/blockpal \
  --header "Authorization: Bearer YOUR_TOKEN"
```

Use the actual address/token shown by `/ai mcp`.

---

## Local apps vs cloud apps

Where the AI app runs determines whether `localhost` works.

| App | Runs where? | Tunnel needed? |
|---|---|---|
| Claude Desktop | your PC | No — use the MCPB extension |
| Claude Code | your PC | No |
| Gemini CLI | your PC | No |
| Cursor / VS Code local MCP clients | your PC | No |
| ChatGPT connector | cloud | Yes |
| grok.com | cloud | Yes |
| Google AI Studio | cloud | Yes |

A cloud service cannot reach `localhost` on your computer. To use a cloud MCP client, turn on remote access and put an HTTPS tunnel in front of BlockPal:

```text
/ai mcp remote on
```

Examples:

```bash
ngrok http 8000
```

or:

```bash
cloudflared tunnel --url http://localhost:8000
```

Use the resulting public **HTTPS** URL with the BlockPal path, and keep token authentication enabled. Turn the tunnel off when you are done.

---

## ChatGPT

ChatGPT custom connectors require a publicly reachable HTTPS MCP URL. `http://localhost:8000/blockpal` cannot be entered directly because ChatGPT's connector runs in OpenAI's cloud.

1. Run `/ai mcp remote on`.
2. Start an HTTPS tunnel to port 8000.
3. In ChatGPT connector settings, use the public HTTPS URL ending in `/blockpal`.
4. Send the token as `Authorization: Bearer <token>` if the connector supports bearer authentication.

---

## Grok

The grok.com website is cloud-hosted, so use the same remote/tunnel setup as ChatGPT. A locally running xAI/API integration can instead connect directly to the local address.

---

## Gemini

Gemini CLI runs locally. Add BlockPal's address and bearer token to its MCP settings. Google AI Studio is cloud-hosted and therefore needs the remote/tunnel setup.

---

## Commands

| Command | Effect |
|---------|--------|
| `/ai mcp` | Open the setup guide |
| `/ai mcp status` | Show address, port, binding and token state |
| `/ai mcp token` | Show your access token privately |
| `/ai mcp newtoken` | Rotate the token immediately |
| `/ai mcp port <n>` | Change the port (default 8000) |
| `/ai mcp remote on|off` | Allow remote connections or localhost only |
| `/ai mcp start` / `stop` | Start or stop the listener manually |

These controls are operator-only.

---

## Security

- BlockPal binds to localhost by default.
- Token authentication is enabled by default.
- The token is stored obfuscated by the mod and should never be committed to the repository or embedded in a distributed MCPB.
- Whoever has a reachable address and valid token can drive your companions, so rotate the token if it leaks.
- Remote mode should only be enabled when you actually need a cloud client.

---

## Transports

BlockPal serves both:

- **Streamable HTTP** — `POST /blockpal` (and `/mcp` as a compatibility alias).
- **Legacy HTTP + SSE** — `GET /sse` plus `POST /messages?sessionId=…`.

The Claude Desktop MCPB bridge prefers Streamable HTTP and includes a legacy SSE fallback for older server/client combinations.

See also: [Vision & Code](Vision-and-Code) · [Settings](Settings) · [Security](Security) · [Troubleshooting](Troubleshooting)
