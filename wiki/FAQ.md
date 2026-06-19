# FAQ

**Do I need an API token to use the mod?**
No — quick commands (come, follow, stay, stop, where are you) work with no token. You
only need a token for AI-planned tasks and active chat analysis. See
[Getting Started](Getting-Started).

**Is it free?**
The mod is free (MIT). The LLM is whatever you point it at — HuggingFace's router has a
free tier, or you can run a local model (Ollama, LM Studio) for free. See
[Settings](Settings).

**Does it work on a server?**
Yes. Put the jar + Fabric API on the server, and players need it client-side too for the
settings screen. See [Installation](Installation).

**Can other players control my assistant?**
No — only the player who summoned it (owner-only obedience). See
[Talking to Your Assistant](Talking-to-Your-Assistant).

**Will it grief my world / run dangerous commands?**
Command execution is off-limited by a permission level and a denylist of admin commands,
and is toggleable. See [Running Commands](Running-Commands).

**It's lagging my game — what do I do?**
Apply the **Potato** preset (`/ai settings preset potato`). If FPS fully collapses the
[emergency kill-switch](Performance-Presets) stops the mod; recover and run `/ai resume`.
See also [Troubleshooting](Troubleshooting).

**What's the Developer tab for?**
Power-user settings that trade safety for speed/behavior — read
[Developer Menu](Developer-Menu) before touching them.

**Will updating the mod wipe my settings or API key?**
No. The config is versioned and migrated; existing values (including your token) are
preserved. See [Settings](Settings).

**Why is the mod id `ai-assistant` if it's called Nexus AI?**
The display name changed; the internal id/package/namespace stayed the same so existing
configs, skins and textures keep working. See [More Info](More-Info).
</content>
