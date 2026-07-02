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

## No server yet? Host one yourself for free

You **don't** need to rent a paid host. But there's one fact you can't get around:

> **Blockpal is a Java-Edition Fabric mod, so *some* Java PC has to run it.** A Bedrock
> device (iPad, phone, console) can't run the mod *or* host the world — Geyser only lets
> Bedrock players **connect** to a Java world that someone else is running. So at least
> **one friend needs Minecraft: Java Edition on a PC.** With that, everyone else —
> including iPads — can join. With *no* Java PC in the group, the mod can't be used at all.

Given one Java PC, here are your options, lightest first:

### 1. Just you — no server at all
Install Fabric + Blockpal and play **single-player**. Works instantly; nobody else joins.

### 2. Java friends on the same Wi-Fi — "Open to LAN"
The host plays single-player and chooses **Open to LAN** from the pause menu. Other
**Java** players on the same network join with no setup. *(Bedrock friends can't join a
LAN world easily — that needs Geyser in standalone mode, which is fiddly; use option 3
for iPad friends.)*

### 3. Java **and** Bedrock friends over the internet — free self-host
This is the route that lets your **iPad friends** join. One friend runs a **free Fabric
dedicated server** on their PC (just the server jar — no rented hosting) with Geyser and
Floodgate, and shares the address.

1. **Get the Fabric dedicated server** — grab the server jar from the
   [Fabric installer](https://fabricmc.net/use/server/) (pick "Server") and run it once
   to generate the files; accept the EULA.
2. **Add the mods** to the server's `mods/` folder:
   [Fabric API](https://modrinth.com/mod/fabric-api), **Blockpal** (from
   [`builds/`](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/tree/main/builds)),
   **Geyser-Fabric**, and **Floodgate-Fabric**
   ([downloads](https://geysermc.org/download)).
3. **Let friends reach it without port-forwarding** — instead of editing your router,
   run a free tunnel like **[playit.gg](https://playit.gg/)** (or ngrok). It gives you a
   public address that forwards to your server. Point it at the Java port (**25565**) for
   Java friends and the Bedrock/Geyser port (**UDP 19132**) for Bedrock friends.
4. **Everyone joins:**
   - **Java** friends → *Add Server* → the tunnel's Java address.
   - **Bedrock** friends (iPad) → *Servers* tab → *Add Server* → the tunnel's Bedrock
     address + port.

> The PC running the server has to be **on and online** for anyone to play — that's the
> trade-off for not renting a host. For a small group of friends, a spare laptop left
> running is plenty.

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

## "Host with Blockpal" — one-click hosting (3.10.0+)

Don't have a server already? A **Java** player can stand one up from inside the game,
and it comes Bedrock-ready out of the box.

- Open the **pause menu** (Esc) in a singleplayer world and click **"Host with
  Blockpal"**, or run **`/aihost`**. (`/aihost status` and `/aihost stop` also work.)
- Tick **"Minecraft EULA accepted"**, then **Start hosting**. Blockpal downloads — from
  the official sites — the Minecraft server, the Fabric server, Fabric API, and the
  **latest Geyser + Floodgate**, configures everything, and launches a real server. The
  first launch takes a minute while it fetches libraries.
- When it's running, the screen shows the **Java** (`ip:25565`) and **Bedrock**
  (`ip:19132`) addresses for both **LAN** and **internet**, with copy buttons.

### Host your current world (3.15.0)

By default (the **"Host current world"** toggle, ON when you opened the screen from a
world), the server hosts **the very world you were just playing**:

1. **Start hosting** saves the world and exits it (a save can't be hosted while open),
   copies it into the server, and launches. Watch progress via the **"Blockpal Host…"**
   button that appears on the title screen.
2. You rejoin your own world via **Multiplayer → Direct Connect → `localhost:25565`**;
   friends use the addresses shown (Java and Bedrock).
3. When you **Stop**, everything that happened on the server is **saved back into your
   singleplayer world**, the pre-host original is kept as a backup
   (`blockpal-host/backups/<world>-<timestamp>`), and the server's copy is **deleted** —
   so there's always exactly one true version of your world.

If that world is open in singleplayer when the server stops, Blockpal won't overwrite it —
a **"Sync world back"** button appears so you can run it after leaving the world (this
offer survives a crash or restart). Turn the toggle **OFF** to host a separate fresh world
instead (the old behaviour).

**Only Java can host.** Bedrock players have no mod, so they can only *join* a Java host
(the same Bedrock→Java direction Geyser supports) — there's no "host from Bedrock".

> ⚠ **Read before you share an address.** The internet address shown is **your own
> computer's public IP** — only give it to people you trust. And showing it doesn't make
> you reachable: friends outside your network still need you to **port-forward** TCP
> **25565** (Java) and UDP **19132** (Bedrock), or use a tunnel. On the same Wi-Fi/LAN the
> local address just works. Auto-downloading and running a server pulls third-party
> software (Geyser, Floodgate) onto your PC — it's all from official sources, but that's
> why it's opt-in behind the EULA prompt. Running your game **and** a server (let alone a
> big one) on one machine is resource-heavy.

### No-port-forward tunnel (3.14.0)

Don't want to forward ports or share your IP? The Host screen has a **"Start tunnel
(no port-forward)"** button that runs the **[playit.gg](https://playit.gg/)** agent — the
one relay that carries both Java (TCP) and Bedrock (UDP). Blockpal downloads the official
agent and runs it; on first run it prints a **free, one-time setup link** (shown in the
screen with a "Copy link" button). Open it, sign in, and map your Java (25565) and Bedrock
(19132) ports on the playit dashboard — after that friends connect to your playit address
with no port-forwarding on your end.

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
