<div align="center">

# ⬢ BLOCKPAL AI ⬢

**A Minecraft AI companion that builds, fights, and thinks.**

[![Mod Version](https://img.shields.io/badge/mod-v3.1.0-6c63ff?style=for-the-badge)](builds/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b96e?style=for-the-badge)](https://fabricmc.net/)
[![Fabric](https://img.shields.io/badge/Fabric_Loader-0.19.3+-dbb74b?style=for-the-badge)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25+-e76f51?style=for-the-badge)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-264653?style=for-the-badge)](LICENSE)
[![GitHub](https://img.shields.io/badge/github-%23121011.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/MilkdromedaStudios/Blockpal-AI)

*Drop a friendly AI character named **Ethan** into your world. It reads your chat,
plans tasks through an LLM, fights back on reflex, and configures itself from a
real in-game settings screen.*

</div>

---

## 🎬 See it in action

<div align="center">

<!--
  ▶ DROP YOUR DEMO HERE
  ------------------------------------------------------------------
  GitHub renders an uploaded video inline. To add yours:
    1. Open a new GitHub Issue (or edit this README on github.com) and
       drag-and-drop your recording (.mp4 / .mov / .webm, <100 MB) into
       the text box. GitHub uploads it and gives you a URL like
       https://github.com/user-attachments/assets/XXXXXXXX
    2. Replace the placeholder block below with that URL on its own line:
           https://github.com/user-attachments/assets/XXXXXXXX
       (a bare video URL auto-embeds as a player — no <video> tag needed)
    3. Prefer a looping GIF instead? Commit it to docs/demo.gif and use:
           ![Blockpal AI demo](docs/demo.gif)
  ------------------------------------------------------------------
-->

[![▶ Watch Ethan build, fight & follow](https://img.shields.io/badge/▶_Demo_video-record_%26_drop_in_here-6c63ff?style=for-the-badge)](#-see-it-in-action)

*Gameplay clip coming soon — see the comment in this section to add it.*

</div>

> **Tip:** the best 20–30 second clip shows three things back-to-back: a chat
> command (`Ethan, build a 5×5 floor`), the bot auto-equipping gear, and a
> `SurvivalReflexGoal` fight where it retreats at low health.

---

## ⚡ What Ethan can do

| | |
|---|---|
| 🧠 **LLM task planning** | Natural-language orders (`/ai build a tower`) become a 5–15 step JSON action plan over any OpenAI-compatible API. |
| 💬 **Talks back** | Listens to chat, answers in first-person, handles `come` / `follow` / `stop` instantly with no API call. |
| ⚔️ **Fights on reflex** | Always scans for threats, retaliates, and retreats below 25% health — in any mode. |
| 🎒 **Manages its gear** | Picks up drops, auto-equips the best weapon & armor, eats food when hurt, tosses harmful items. |
| 🛠️ **16 actions** | Move, place, break, mine, use blocks, run commands, attack, follow, look, chat, collect, and more. |
| 🎨 **Custom skins** | Built-in skins or drop your own PNG into `config/blockpal/skins/` — no rebuild. |
| 🖥️ **In-game panel** | Tabbed settings & admin GUI — no config-file editing, no setting commands. |
| 🔑 **Bring-your-own-key** | Per-player API keys & selectable models so one server owner isn't billed for everyone. |
| 🛟 **Safety rails** | Task watchdog, server bot cap, and an emergency FPS kill switch that pauses the bot if frames collapse. |

---

## 🚀 Quick start

```text
1. Grab the latest jar from  builds/
2. Drop it in your  mods/  folder, next to Fabric API
3. Launch Minecraft (Fabric 26.2)
4. In-game:  /ai summon      → meet Ethan
            /ai mykey <token> → give it an LLM key
            /ai build a 5x5 floor
```

Everything else — install details, tokens, every command — is in the wiki below.

---

## 📖 Documentation lives in the Wiki

**All setup, usage, and configuration docs are on the [Blockpal Wiki »](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki)**

| I want to… | Wiki page |
|------------|-----------|
| Download & install the mod | [Installation](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/Installation) |
| Get started & add my AI token | [Getting Started](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/Getting-Started) |
| See every command | [Commands](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/Commands) |
| Talk to it in chat | [Talking to Your Assistant](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/Talking-to-Your-Assistant) |
| Change settings | [Settings](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/Settings) |
| Understand the **Developer menu** | [Developer Menu](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/Developer-Menu) |
| Learn how it all works (**More Info**) | [More Info](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/More-Info) |
| Build from source | [Building From Source](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/Building-From-Source) |
| Fix a problem | [Troubleshooting](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki/Troubleshooting) |

> **In a hurry?** Grab the latest jar from [`builds/`](builds/), drop it into your
> `mods/` folder alongside [Fabric API](https://modrinth.com/mod/fabric-api), launch
> Minecraft, and run `/ai summon`. Everything else is in the wiki.

---

## License

MIT — see [LICENSE](LICENSE).

<sub>Wiki sources are kept in [`wiki/`](wiki/) and published automatically. See [`wiki/README.md`](wiki/README.md) for how the sync works.</sub>
</content>
</invoke>
