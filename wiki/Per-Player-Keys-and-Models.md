# Per-Player API Keys & Models

By default a server shares one API key (set by an admin) and one model. On a busy
server that can mean **one person pays for everyone's** AI usage. Blockpal 3.3.0 adds
two things to fix that:

1. **Bring-your-own-key (BYOK)** — make players use their *own* API key.
2. **Player-selectable models** — let players pick their bot's model from a list you
   curate.

Both are off-impact by default: a fresh/updated server keeps working exactly as
before (shared key, model choice on with a starter list).

## Bring-your-own-key

### Turn it on
```
/ai admin requirekey on      # players must use their own key
/ai admin requirekey off     # back to the shared key for everyone (default)
```
You can also use `/ai settings require_own_key true|false`.

### How a key is chosen for a bot
A bot is owned by the player who summoned it. For each request Blockpal picks the key:

1. **The owner's personal key**, if they've set one — *always wins* (their bot, their bill).
2. Otherwise, if BYOK is **on** and the owner is **not** whitelisted → **no key**: the
   bot politely says it needs one (`/ai mykey <token>`).
3. Otherwise → the **shared server key**.

So even with BYOK off, any player *may* set a personal key to use their own model/quota.

### Players set their own key
```
/ai mykey <token>     # set your personal key
/ai mykey clear        # remove it
/ai mykey              # show whether you have one set
/ai mymenu             # do it (and pick a model) in a private screen
```
Keys are stored **obfuscated** per-player and are **never** shown to other players or
written to the log. Typing a token in chat can briefly expose it to anyone reading —
prefer **`/ai mymenu`** (a GUI field) when you can. See **[Security](Security)**.

### Exemption whitelist
Trusted players can keep using the shared key even when BYOK is on:
```
/ai admin keylist add <player>
/ai admin keylist remove <player>
/ai admin keylist list
```
Entries are usernames (case-insensitive). Example: turn BYOK on for the public, but
whitelist your moderators so they keep using the house key.

## Player-selectable models

### Curate the list (admin)
```
/ai admin models list
/ai admin models add <model-id>      # e.g. meta-llama/Llama-3.1-8B-Instruct
/ai admin models remove <model-id>
```
The list is seeded with a few common models on first run, and the **server default
model** (`/ai settings model <id>`) is always kept on it. Removing the default isn't
allowed — change the default first if you need to.

### Players pick a model
```
/ai models            # list the allowed models (your current one is marked)
/ai model <model-id>  # use this model for your bot
/ai mymenu            # pick from a dropdown in a screen
```
A player's pick only applies if it's on the allowed list. Turn the whole feature off
(everyone uses the server default) with:
```
/ai settings allow_model_choice false
```

## Where it's stored
Everything lives in `config/blockpal/config.json`:

| Key | Meaning |
|-----|---------|
| `requireOwnApiKey` | BYOK on/off (default `false`) |
| `ownKeyWhitelist` | Usernames exempt from BYOK |
| `playerApiKeysObf` | Per-player keys, **obfuscated**, keyed by UUID |
| `allowPlayerModelChoice` | Whether players may pick a model (default `true`) |
| `allowedModels` | The models players may choose from |
| `playerModels` | Each player's chosen model, keyed by UUID |

See also: **[Settings](Settings)**, **[Commands](Commands)**, **[Security](Security)**,
**[Admin Menu](Admin-Menu)**.
