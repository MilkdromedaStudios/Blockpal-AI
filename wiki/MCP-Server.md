# MCP server — connect Claude, ChatGPT, Grok or Gemini

This is the **easiest** way to give your companion a brain, and since **3.25.0** it's a
first-class AI connection alongside "my own API key", Player2, Ollama and the free
service.

Instead of pasting an API key into the game and paying for tokens a second time, Blockpal
runs a small **[Model Context Protocol](https://modelcontextprotocol.io) server** and the
AI app you already use connects *to your world*. Your AI looks through the companion's
eyes and writes little scripts that walk, mine, build and open chests.

**In-game guide:** run **`/ai mcp`** — it shows this server's address, your access token,
and the exact config to paste, with copy buttons.

---

## Turn it on

```
/ai connection set mcp
/ai mcp
```

Only **one** AI connection runs at a time (see [Settings](Settings)), so switching to
MCP turns the others off. That's deliberate — no more guessing which provider answered.

In the panel: **`/ai menu` → AI & API → "AI: MCP server"**.

---

## What the AI gets

Ten tools, shaped like a player's hands and eyes — not an admin console:

| Tool | What it does |
|------|--------------|
| `list_bots` | Every companion in the world, with owner and position |
| `select_bot` | Choose which one to drive |
| `look` | **A picture rendered from the bot's own eyes**, plus the scene in words |
| `observe` | Position, facing, health, inventory, what's in reach, last result |
| `api_reference` | The scripting language the bot understands |
| `run_code` | Run a script — walk, turn, mine, place, use, chests |
| `script_status` | Is it still going, and what has it logged |
| `stop` | Let go of every key |
| `say` | Talk out loud in game chat |
| `inventory` | What it's wearing, holding and carrying |

There is no "place block at coordinates" tool and no map dump, because there is no such
button on a keyboard. See [Vision & Code](Vision-and-Code) for how the loop works and
what the scripts look like.

---

## Setting up each app

Every app needs the same two things: the **address** (`http://localhost:25569/mcp` by
default) and the **token** (`/ai mcp token`). Send the token as
`Authorization: Bearer <token>`.

> Menus move between app versions. The address, transport and token are the parts that
> matter — if a menu name below has changed, look for "MCP", "connectors" or "tools".

### Claude

**Claude Desktop** — Settings → Developer → Edit Config, then add to
`claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "blockpal": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:25569/mcp",
               "--header", "Authorization: Bearer YOUR_TOKEN"]
    }
  }
}
```

Restart Claude. (`mcp-remote` is the standard bridge for HTTP servers and needs
[Node.js](https://nodejs.org).)

**Claude Code** — one line:

```bash
claude mcp add --transport http blockpal http://localhost:25569/mcp \
  --header "Authorization: Bearer YOUR_TOKEN"
```

Then just ask: *"look through Ethan's eyes and chop the nearest tree."*

### ChatGPT

ChatGPT connects to MCP servers as a **connector** (Settings → Connectors → add a custom
connector). Use the address, pick bearer-token auth, and paste the token.

⚠ **ChatGPT runs in OpenAI's cloud, so it cannot reach `localhost` on your PC.** You must
expose the address to the internet first — a tunnel like ngrok or Cloudflare Tunnel — and
turn on:

```
/ai mcp remote on
```

Keep the token requirement **on** when you do that, and stop the tunnel when you're done.
Anything reachable from the internet is reachable by anyone who learns the URL.

### Grok (xAI)

Same shape: point Grok's connector/tool settings (or the xAI API's MCP server list) at the
address with the `Authorization: Bearer` header. grok.com is also cloud-hosted, so it needs
the tunnel + `/ai mcp remote on` treatment described above.

### Gemini / Google AI Studio

**Gemini CLI** runs on your own machine, so it reaches the server with no tunnel at all.
Add to `~/.gemini/settings.json`:

```json
{
  "mcpServers": {
    "blockpal": {
      "httpUrl": "http://localhost:25569/mcp",
      "headers": { "Authorization": "Bearer YOUR_TOKEN" }
    }
  }
}
```

**Google AI Studio** is cloud-hosted — same tunnel + `/ai mcp remote on` as ChatGPT.

---

## Commands

| Command | Effect |
|---------|--------|
| `/ai mcp` | Open the setup guide (chat version on Bedrock/vanilla) |
| `/ai mcp status` | Address, port, binding, token state |
| `/ai mcp token` | Show your access token (privately) |
| `/ai mcp newtoken` | Roll a new one — the old one stops working immediately |
| `/ai mcp port <n>` | Change the port (default 25569) |
| `/ai mcp remote on\|off` | Listen on all interfaces, or localhost only (default) |
| `/ai mcp start` / `stop` | Start or stop the listener by hand |

All of these are operator-only.

---

## Security

- **Localhost by default.** Nothing outside your machine can reach it until you turn on
  `/ai mcp remote on`.
- **Token required by default.** Every request must carry
  `Authorization: Bearer <token>` (a `?token=` query parameter is accepted for clients
  that can't set headers — weaker, since URLs end up in logs). Whoever holds the token can
  drive your companions, so don't paste it in chat or leave it on stream.
- **Stored obfuscated**, like the API key — never written to `config.json` in plaintext,
  never logged.
- **It can't do more than a player can.** No teleport tool, no set-block tool, no world
  dump. The AI has the same reach, the same speed and the same blindness around corners as
  anyone else in the world.

---

## Both transports are served

- **Streamable HTTP** — `POST /mcp`, the current spec, what most clients and `mcp-remote`
  use.
- **HTTP + SSE** — `GET /sse` then `POST /messages?sessionId=…`, the older transport some
  clients still speak. The guide screen shows this address too.

---

## Troubleshooting

**"Connection refused."** The listener only runs when the connection is set to MCP —
check `/ai mcp status`. If a port is already taken, `/ai mcp port 25570`.

**401 Unauthorized.** The token is wrong or missing. `/ai mcp token` to see it; make sure
the header is exactly `Authorization: Bearer <token>`.

**The app connects but sees no bots.** Someone has to summon one: `/ai summon`. Then
`list_bots` → `select_bot`.

**A cloud app can't reach it.** ChatGPT, Grok's website and AI Studio run on someone
else's computer. They need `/ai mcp remote on` plus a tunnel. Desktop apps (Claude
Desktop, Gemini CLI) don't.

See also: [Vision & Code](Vision-and-Code) · [Settings](Settings) ·
[Local & Player2 AI](Local-AI-Ollama-and-Player2) · [Security](Security)
