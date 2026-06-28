# Playing Blockpal from Bedrock (Geyser & Floodgate)

Want your friends to join your server from **Minecraft Bedrock Edition** — iPad,
phone, console, or the Windows 10/11 app — and play with **Ethan** too? You can.
Blockpal is built to work for Bedrock players through a **Geyser** proxy, with no
special build of the mod.

> **Short version:** Blockpal is mostly *server-side*, so once you install **Geyser**
> and **Floodgate** on your Fabric server, Bedrock players can summon Ethan, talk to
> it, and give it tasks — all from chat and `/ai` commands. There is **no client mod
> to install on a Bedrock device** (Bedrock can't run Fabric mods at all). A couple of
> Java-only extras (the visual menus, the FPS watchdog) gracefully fall back to text.

---

## What you need

This is a **server-admin** setup. Bedrock players install nothing.

1. A **Fabric server** running Blockpal (the normal setup — see
   [Installation](Installation)).
2. **[Geyser-Fabric](https://geysermc.org/download)** — the proxy that lets Bedrock
   clients connect to a Java server. Drop it in `mods/`.
3. **[Floodgate-Fabric](https://geysermc.org/download)** — lets Bedrock players join
   **without a paid Java account**. Drop it in `mods/`. Blockpal uses Floodgate (when
   present) to recognise Bedrock players and tailor their experience.

> **Version note:** Geyser and Floodgate have to support your **Minecraft version**
> (this build targets **26.2**). Geyser usually adds support shortly after a Java
> release — check the [GeyserMC downloads](https://geysermc.org/download) and their
> Discord for the current status before you upgrade your server. Blockpal does **not**
> bundle Geyser/Floodgate; you add them yourself.

Blockpal declares Floodgate only as a **suggested** (optional) dependency, so your
server still loads and runs perfectly fine **without** Geyser installed. The Bedrock
niceties simply switch on when Floodgate is present.

Connecting a Bedrock client itself (default port 19132, the "Add Server" screen on
Bedrock) is standard Geyser setup — see the
[Geyser setup guide](https://geysermc.org/wiki/geyser/setup/).

---

## What works from Bedrock

Because the entity AI, chat handling and commands all live on the server, the heart of
Blockpal works for Bedrock players out of the box:

| Feature | Bedrock? | Notes |
|--------|:--------:|-------|
| `/ai summon`, `dismiss`, `come`, `follow`, `stay`, `stop` | ✅ | Geyser translates Java commands to Bedrock. |
| Talking in chat ("Ethan, follow me", "clear these trees") | ✅ | Server-side chat listening — works the same. |
| Natural-language tasks (`/ai build a 5x5 floor`) | ✅ | Planned on the server. |
| Personalities (`/ai personality …`, custom) | ✅ | Server-side. |
| Per-player keys & models (`/ai mykey`, `/ai model`) | ✅ | Via chat commands. |
| Inventory, gear, combat reflexes | ✅ | All server-side. |
| Admin controls (`/ai admin …`) | ✅ | The full text command tree works on Bedrock. |
| Setting the AI key without the GUI | ✅ | `/ai admin token <key>` (see below). |

## What's different on Bedrock

A Bedrock client can't run the Fabric **client** mod, so the features that mod
provides aren't available — Blockpal detects this and hands you a text alternative
instead of a menu you can't open:

| Java-client feature | On Bedrock | Use instead |
|--------------------|------------|-------------|
| Visual settings panel (`/ai menu`, `/ai panel`) | Not shown | `/ai admin …` text commands |
| Personal menu (`/ai mymenu`) | Not shown | `/ai mykey <token>`, `/ai model <id>` |
| Admin panel GUI (`/ai admin menu`) | Not shown | `/ai admin stats`, `/ai admin list`, etc. |
| Tutorial / AI Manual screens | Not shown | `/ai tutorial` prints a text walkthrough |
| Emergency FPS kill-switch | Doesn't run | It's a Java-client watchdog; harmless to skip |
| Custom companion **skins** rendering | ⚠️ Limited | See *Known limitation* below |

### Configuring the AI key on Bedrock

The normal way to set the shared API key is the in-game panel — which needs a Java
client. So that a **Bedrock or vanilla** admin can still configure everything, Blockpal
exposes text commands under the ops-only `/ai admin` tree:

```text
/ai admin token <your-api-key>     # set the shared AI key
/ai admin apiurl <url>             # set the OpenAI-compatible endpoint
/ai admin model <model-id>         # set the server default model
```

> ⚠️ Typing a key in chat can expose it to anyone watching. On a server you control,
> prefer the **`BLOCKPAL_API_TOKEN`** environment variable — it's used but never
> written to disk. See [Security](Security).

Individual players (Java or Bedrock) set their own key and model the same way:

```text
/ai mykey <your-api-key>
/ai model <model-id>
```

---

## Known limitation: the companion's appearance

Geyser can translate built-in Java entities and has mapping systems for custom
**items** and **blocks**, but it has **no general system for custom *entities*.**
Ethan is a custom entity (`blockpal:ai_assistant`), so on Bedrock it may appear
**incorrectly or be invisible**, even though it's fully there and working on the
server — you can still command it, it still follows, fights, and builds.

This is a Geyser-side limitation, not something the Blockpal jar can fix on its own.
Improving Bedrock rendering (e.g. shipping a Geyser resource pack, or representing the
companion as a player-type entity Geyser already knows) is on the roadmap. If you only
care that your friends can *play with* Ethan from an iPad, that already works today —
the appearance is the rough edge.

---

## Troubleshooting

- **Bedrock players can't connect at all** — that's Geyser/Floodgate setup, not
  Blockpal. Check the proxy is listening (default UDP **19132**) and that Geyser
  supports your Minecraft version. See the
  [Geyser setup guide](https://geysermc.org/wiki/geyser/setup/).
- **`/ai` commands missing on Bedrock** — make sure Geyser's command translation is
  working and the player has permission. Try typing `/ai help` in chat.
- **"This menu needs a Java client"** — expected on Bedrock. Use the text command it
  suggests (`/ai admin …`, `/ai mykey`, `/ai model`).
- **The bot is invisible** — see *Known limitation* above; it's still there and
  responding to commands.

See also: [Commands](Commands) · [Admin Menu](Admin-Menu) · [Security](Security) ·
[Troubleshooting](Troubleshooting).
