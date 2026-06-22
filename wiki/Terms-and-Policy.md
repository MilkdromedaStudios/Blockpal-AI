# Terms & Policy

Plain-language terms for using **Blockpal**, and a fair-use / no-cheating policy.
By installing or using the mod you agree to the following.

## Fair use — no hacking or exploits

Blockpal is meant to be a fun, helpful companion. Please **don't**:

- **Hack, cheat, or exploit** the mod, the server, or other players — including
  trying to bypass Blockpal's permission checks, the admin gate, the bot cap, or the
  emergency kill switch.
- **Tamper with other players' bots** or settings, or use the mod to **grief**
  (spawn-spamming companions, lag machines, destroying others' builds, etc.).
- **Steal, extract, or misuse API keys** — yours or anyone else's — or use the mod
  to send someone else's key to a server you control.
- **Abuse the AI** to generate harmful, illegal, hateful, or harassing content, or to
  attack, spam, or overload any service.
- **Circumvent server rules or operator controls.** Operators have the final say on
  their servers (see *Operator rights* below).

Server operators may remove the mod, bots, or players who break these rules.

## Use it at your own risk

- Blockpal can run game commands and modify the world on your behalf. **You are
  responsible** for what you ask it to do and for backing up your worlds. Use the
  bot cap, the command toggle/permission level, and the task watchdog to stay safe.
- The mod is provided **"as is," without warranty of any kind.** The authors are not
  liable for lost worlds, banned accounts, API charges, or other damages arising from
  use of the mod. (See the project's MIT license for the full legal text.)

## Your AI provider & API key

- Blockpal connects to a third-party, OpenAI-compatible API (HuggingFace by default,
  or whatever you configure). **You must follow that provider's own terms of service**
  and any usage limits or costs.
- **You supply and are responsible for your own API key.** Keep it secret. Blockpal
  helps (it never shows your key to other players, never logs it, obfuscates it on
  disk, and supports the `BLOCKPAL_API_TOKEN` environment variable) — but ultimate
  responsibility for the key is yours. See **[Security](Security)**.
- Chat and task text you give the assistant is sent to your configured API provider so
  it can plan. Don't share anything you wouldn't want sent to that provider.

## Operator rights

Server operators (admins) can:

- Change all server-wide settings and use the **[Admin Menu](Admin-Menu)**.
- Limit how many bots exist (`maxBotsPerServer`), disable command execution, and
  globally disable the mod.
- Decide who counts as an admin via `adminPermissionLevel`.

Non-operators can use everyday companion features but cannot change server-wide
settings or use admin tools. This is by design.

## Reporting issues

Found a bug, an exploit, or a security problem? Please open an issue on the GitHub
repository. For security issues, **report privately/responsibly** rather than posting
a working exploit publicly.

## Changes

These terms may change as the mod evolves; the current version always lives here in
the wiki. Continued use after a change means you accept the updated terms.

---

*Blockpal is a community mod and is not affiliated with Mojang, Microsoft, HuggingFace,
or any AI provider. "Minecraft" is a trademark of Mojang/Microsoft.*
