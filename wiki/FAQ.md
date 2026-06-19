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
For normal updates, no — the config is versioned and migrated; existing values
(including your token) are preserved. The one exception is upgrading from the old
**Nexus AI / `ai-assistant`** builds to **Blockpal 3.0.0**: the config folder was
renamed to `config/blockpal/`, so you'll need to re-enter settings once. See
[Settings](Settings).

**I used to have "Nexus AI" / "AI Assistant" — is this the same mod?**
Yes. Blockpal is the same mod, renamed. As of **3.0.0** the mod id is `blockpal`, the
config folder is `config/blockpal/`, and the texture namespace is `blockpal:`. Older
builds used the `ai-assistant` id and aren't config-compatible. See [More Info](More-Info).
</content>
