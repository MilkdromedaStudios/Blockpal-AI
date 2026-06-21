# Changelog

User-facing release notes for **Blockpal**. The section matching the current
`mod_version` is published to Modrinth as that version's description, so keep the
top entry written for players.

## 3.3.0
- **Bring your own API key.** Server owners can now make players use their *own*
  API key (so one person isn't stuck with the whole bill). Turn it on with
  `/ai admin requirekey on`; players set their key with `/ai mykey <token>` or
  privately in the new `/ai mymenu` screen. Keys are stored scrambled and never
  shown to anyone else.
- **Key whitelist.** `/ai admin keylist add <player>` lets trusted players keep
  using the server's shared key even when "bring your own key" is on.
- **Pick your AI model.** Admins curate a list of models
  (`/ai admin models add|remove|list <id>`), and players choose which one their
  companion uses with `/ai model <id>`, `/ai models`, or the `/ai mymenu` screen.
  Turn player choice off with `/ai settings allow_model_choice false`.

## 3.2.0
- **New admin menu (ops only).** `/ai admin menu` opens a built-in admin panel —
  see and manage **every bot on the server**, kill them all at once, flip bots
  off/on for everyone, and set how many bots are allowed at a time. `/ai admin stats`
  and `/ai admin list` show the same info as text.
- **Live server stats.** The admin menu shows total bots vs. the cap, who owns how
  many bots, and each player's **FPS**, plus mod status and whether an API key is set.
- **Bot limit.** Owners/ops can cap how many Blockpal companions exist at once
  (`/ai admin maxbots <0-50>`, default 8). `/ai summon` politely refuses past the cap.
- **Tighter security.** Only operators can now change server-wide settings (API key,
  API URL, model, command permissions) or use the admin tools — this closes a hole
  where any player with the mod could change them. Everyday commands (summon, follow,
  come, stay, etc.) are unchanged for everyone. Who counts as an "op" is adjustable
  with `/ai settings admin_level <0-4>`.
- **Better API-key protection.** Your token is no longer stored as plain text in the
  config file (it's obfuscated), and you can instead provide it through the
  `BLOCKPAL_API_TOKEN` environment variable so it never touches disk at all. It's
  still never shown to other players or written to the log.

## 3.1.0
- Updated Blockpal to **Minecraft 26.2** (the "All En" update).
- Now published for both the **Fabric** and **Quilt** loaders.
- Fixed the in-game `/ai menu` settings screen against the 26.2 client API change.

## 3.0.0
- Renamed the mod to **Blockpal**: new mod id, texture namespace and
  `config/blockpal/` config folder. This is a fresh setup — configs and skins
  from older "Nexus AI" / "AI Assistant" installs are not carried over.

## 2.14.0
- Rebranded the display name to **Nexus AI** (later renamed again to Blockpal).
