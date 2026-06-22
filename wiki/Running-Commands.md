# Running Commands

`RUN_COMMAND` is what turns the assistant from "places blocks one by one" into "builds
a working redstone contraption". It can run `/setblock`, `/fill`, `/give`, `/tp`,
`/effect`, `/summon`, `/clone`, `/time`, `/weather` and similar.

It's gated three ways:

## 1. Toggle

On by default. Turn it off with `/ai commands off` or the **Allow commands** switch in
`/ai menu → Combat`.

## 2. Permission level (default 2)

The command-block tier. At the default level **2** it can run building/utility commands
like `/setblock`, `/fill`, `/clone`, `/give`, `/summon`, `/tp`, `/effect`, `/time`,
`/weather`, but **not** server-admin commands. Adjust with the **Command perm level**
control in the panel (Combat tab, or the Admin panel).

## 3. Denylist

Dangerous admin commands — `stop`, `op`, `ban`, `whitelist`, `reload` and similar — are
**always refused** regardless of the permission level.

> Command execution is per-session and server-side. On a multiplayer server, make sure
> you trust the player who owns the assistant before enabling it.
</content>
