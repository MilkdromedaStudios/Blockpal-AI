# Friend Sharing

Everything about playing Blockpal **with other people** in one place: hosting a world for
friends, letting them join from Java or Bedrock, giving them control of your bot, and
(coming soon) inviting a whole group for [Minigames](Minigames).

## Cross-play direction (read this first)

Blockpal follows the same direction Geyser supports: **Bedrock players join a Java host —
not the other way around.** A Bedrock device can't run the mod, so it can only *join*; a
Java player is always the host. Everything below assumes a **Java host** with **Bedrock
and Java friends joining**.

## Host a world in one click (available now)

A Java player can stand up a Bedrock-ready server from inside the game — no manual setup.

- Open the **pause menu** (Esc) in a singleplayer world and click **"Host with
  Blockpal"**, or run **`/aihost`**.
- Accept the Minecraft EULA, then **Start hosting**. Blockpal downloads the Minecraft
  server, Fabric, and the **latest Geyser + Floodgate** from their official sites,
  configures everything, and launches a real server.
- When it's running it shows the **Java** (`ip:25565`) and **Bedrock** (`ip:19132`)
  connect addresses, for both your local network and the internet, with copy buttons.

Full walkthrough and caveats: **[Bedrock (Geyser)](Geyser-Bedrock)**.

> ⚠ **Share addresses carefully.** The internet address shown is your own computer's
> public IP — give it only to people you trust. And showing it doesn't make you
> reachable: friends outside your network still need a forwarded port (TCP 25565 for
> Java, UDP 19132 for Bedrock). On the same Wi-Fi/LAN the local address just works. A
> no-port-forward **tunnel** option (so you needn't expose your IP) is planned.

## Let Bedrock friends join (available now)

If you already run a Fabric server, add **Geyser-Fabric** and **Floodgate-Fabric** to its
`mods/` folder and your friends on iPad, console, or phone can join and play with Ethan —
no mod on their device. Blockpal treats Floodgate as optional, so the server still runs
fine without it. Details and the works/doesn't-work list are on the
**[Bedrock (Geyser)](Geyser-Bedrock)** page.

## Let friends command your bot (available now)

Sharing a world doesn't mean handing over your companion. By default only you (its owner)
can give it orders. To let a specific friend command a specific bot, use **trust**:

```
/ai trust <player>     # let them give this bot orders
/ai untrust <player>   # take it back
/ai trust list         # who's trusted on this bot
```

Trusted friends can tell the bot to come/follow/stay/stop and give it tasks; renaming,
dismissing, and editing the trust list stay owner-only. Full details:
**[Trust & Per-Bot Management](Trust-and-Per-Bot)**.

## Invite a group / party (available now)

Gather a group with the built-in **party** system — the team that [Minigames](Minigames)
will run on. It's entirely command-driven and handled on the server, so **Java and Bedrock
players use it the same way**.

```
/party invite <player>   # invite an online player (auto-creates your party)
/party accept            # accept an invite (expires after 2 minutes)
/party deny              # decline an invite
/party list              # show your party and its leader
/party leave             # leave your party
/party kick <player>     # leader: remove a member
/party disband           # leader: end the party
```

- One **leader**, up to **100 members**.
- The leader invites, kicks, and disbands; if the leader leaves or disconnects, leadership
  passes to another member automatically.
- Parties live for the session (they aren't saved across a server restart).

> The minigame modes (coming next) will start a round for your whole party, so everyone —
> and the bot — plays together.

## Related pages

- [Bedrock (Geyser)](Geyser-Bedrock) — hosting walkthrough + how Bedrock joins.
- [Trust & Per-Bot Management](Trust-and-Per-Bot) — share control of a bot.
- [Minigames](Minigames) — the game modes you'll play with your invited friends.
