# Security

How Blockpal protects your server and your API key — and an honest note on what is
and isn't possible for a Minecraft mod.

## Who can change what

Blockpal's settings are **server-wide** (one shared config), so changing them is
restricted to admins:

- **Everyday commands stay open to everyone** — `summon`, `follow`, `come`, `stay`,
  `stop`, `locate`, `inventory`, `skin`, `name`, and reading `/ai settings` /
  `/ai help`. (The owner-only rule for *commanding a specific bot* still applies.)
- **Changing settings is admin-only** — `/ai menu`, `/ai token`,
  `/ai settings <key> <value>`, `/ai listen|active|commands on|off`, the sneak-click
  menu, and every `/ai admin …` command.

"Admin" means permission level ≥ `adminPermissionLevel` (default **2** = ops). Change
it with `/ai settings admin_level <0-4>`.

### Why this matters

The save action from the settings menu, and every admin button, is a network packet.
**The server never trusts the client** — it re-checks your permission before applying
anything. So even a modified/hacked client that hides the UI or forges the packet
**cannot** rewrite the API token, the API URL, the command-permission tier, or run
admin actions like *kill all bots*. (Before 3.2.0 any client could do exactly that —
that hole is now closed.)

## Protecting your API key

Layered protection for the HuggingFace / OpenAI-compatible token:

1. **Never sent to clients.** When the settings menu opens, the server sends only
   "token: set / not set" — never the token itself.
2. **Never logged.** The key is not written to the game log, even with debug logging on.
3. **Obfuscated at rest.** In `config/blockpal/config.json` the token is stored as
   `hfTokenObf` (a reversible scramble), not as readable text. This stops it being
   read at a glance, copied out of the file, or caught in a screenshot. An old
   plaintext token is converted automatically the first time the config is saved.
4. **Environment variable (strongest).** Set `BLOCKPAL_API_TOKEN` (or pass
   `-Dblockpal.apiToken=...` on the JVM command line) and Blockpal uses that token
   **without ever writing it to disk**. The admin menu will show `set ✓ (from env)`.
   This is the recommended option for dedicated/hosted servers.

```bash
# Example: dedicated server, key kept out of the config file entirely
export BLOCKPAL_API_TOKEN="hf_xxxxxxxxxxxxxxxxxxxxx"
java -jar fabric-server-launch.jar nogui
```

**Per-player keys** (when players bring their own — see
[Per-Player Keys & Models](Per-Player-Keys-and-Models)) get the same treatment: stored
obfuscated per-UUID, never shown to other players, never logged. Setting a key via
`/ai mymenu` (a GUI field) avoids the chat-exposure risk of typing it in chat.

> **Honest limit:** the on-disk obfuscation is **obfuscation, not encryption**. The
> unscramble key is in the mod, which (like any mod) can be decompiled, so anyone with
> file access *could* recover an on-disk token. If that matters to you, use the
> environment variable so the key never touches disk.

## Can `.gitignore` hide the mod from hackers?

**No — and that's worth understanding.** A Minecraft mod is shipped as a compiled
`.jar`, and Java bytecode can be decompiled back into readable code in seconds with
free tools. So:

- Putting the mod's **source files** in `.gitignore` would **not** hide anything from
  anyone who has the mod — they already have the (decompilable) code. It would only
  break building the mod for you and your collaborators. "Security through obscurity"
  isn't real security.
- What `.gitignore` **should** do — and what Blockpal's does — is keep **secrets** out
  of the public repo: your API token, the runtime `config/` folder, and stray
  `*.token` / `hf_token*` files. That's a genuine, important protection.

The real defenses are the ones above: server-side permission checks, keeping the token
off the wire and out of logs, obfuscating it at rest, and supporting an env-var so it
never lands on disk.

## Reporting a problem

Found a way to bypass these checks, or another security issue? Please open an issue on
the GitHub repository rather than sharing an exploit publicly. See
**[Terms & Policy](Terms-and-Policy)**.
