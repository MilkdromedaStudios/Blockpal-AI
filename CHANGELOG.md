# Changelog

User-facing release notes for **Blockpal**. The section matching the current
`mod_version` is published to Modrinth as that version's description, so keep the
top entry written for players.

## 3.6.0
- **Custom personalities.** Beyond the six built-ins, you can now write your *own*
  personality in plain words — "a wise old wizard", "a sarcastic robot butler", etc.
  Set it with **`/ai personality custom <text>`** or in the **My Settings** screen
  (`/ai mymenu`), where there's now a Personality picker and a custom text box.
- **Kept family-friendly automatically.** Custom text is checked by the AI before it's
  applied — anything with profanity, slurs, adult or otherwise unsafe content is
  rejected with a reason, so it stays appropriate for all ages.
- **In the settings panel, not just commands.** The Settings → Identity tab now has a
  **Default personality** picker (the personality new bots spawn with), and ops get an
  **"Allow custom personalities"** toggle (Behavior tab) to restrict players to the
  built-ins if they want.

## 3.5.0
- **Your companion now has a personality.** Pick how it talks *and* how it acts with
  **`/ai personality <id>`** — choose from **friendly** (the classic Ethan),
  **cheerful**, **grumpy**, **stoic**, **heroic** or **shy**. Run `/ai personality`
  on its own to see the list and which one your bot is using.
- Each bot remembers its own personality, so different companions can have different
  vibes. The personality flavours every quick reply (follow, come, stay, gear pick-ups,
  …) and is woven into the AI planner, so the things it *says* mid-task stay in
  character too.
- Server owners can set the default for newly summoned bots (the new
  `defaultPersonality` setting; defaults to **friendly**, so existing worlds sound
  exactly as before).

## 3.4.1
- **Behind-the-scenes / docs only — no gameplay changes.** The mod itself is identical
  to 3.4.0.
- Release, wiki and build automation now only run **after a pull request is merged**
  (never when one is just opened), so work-in-progress that gets closed never ships.
- The wiki and developer docs were brought up to date with the 3.2–3.4 changes.

## 3.4.0
- **Everything's in one panel now.** Open it with **`/ai panel`** (or `/ai menu`).
  Tabs across the top switch between **Settings** (admins), **Admin** (ops) and
  **My Settings** (everyone), so you no longer hunt for separate menus.
- **No more confusing setting commands.** `/ai settings`, `/ai token`, `/ai listen`,
  `/ai active` and `/ai commands` are gone — change everything in the panel instead.
  Your everyday commands (summon, follow, come, stay, `/ai mykey`, `/ai model`, …)
  are unchanged.
- **Admins can change more from the panel** — allow-commands, permission levels,
  admin level, the bot cap, bring-your-own-key and model choice are now toggles in
  the Admin panel, no commands or file-editing needed.
- **New first-run tutorial.** Fresh installs greet you and open a short how-to
  walkthrough on first join. Reopen it any time with **`/ai tutorial`**.

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
