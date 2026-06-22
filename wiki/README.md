# Blockpal — Wiki sources

This folder holds the **source** for the [Blockpal GitHub Wiki](https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI/wiki).
Editing the wiki by hand in GitHub's UI is fine for quick fixes, but the canonical
copy lives here so it's versioned alongside the code and reviewed in pull requests.

## How publishing works

A GitHub Action ([`.github/workflows/wiki.yml`](../.github/workflows/wiki.yml)) pushes
the contents of this folder to the wiki repository automatically whenever something
in `wiki/**` changes on the `main` branch. You don't have to copy anything by hand.

### One-time setup (required before the first sync)

The GitHub wiki is a separate git repository that only exists once the wiki has been
initialised. Do this **once**:

1. Open the repo on GitHub → **Settings → Features** and make sure **Wikis** is enabled.
2. Go to the **Wiki** tab and click **Create the first page** — save any placeholder
   text (the sync will overwrite it).
3. Make sure Actions are allowed to write to the repo: **Settings → Actions → General →
   Workflow permissions → Read and write permissions**.

After that, every push to `main` that touches `wiki/**` republishes the wiki.

## Page naming

GitHub wikis map a file like `Developer-Menu.md` to the page URL
`…/wiki/Developer-Menu` and the title **Developer Menu** (hyphens become spaces).
Special files:

| File | Role |
|------|------|
| `Home.md` | Wiki landing page |
| `_Sidebar.md` | Navigation sidebar shown on every page |
| `_Footer.md` | Footer shown on every page |

Internal links use the page name without extension, e.g. `[Commands](Commands)`.

## Manual publish (fallback)

If you'd rather not use the Action, clone the wiki repo and copy the files in:

```bash
git clone https://github.com/MilkdromedaStudios/Nexus-Minecraft-AI.wiki.git
cp wiki/*.md Nexus-Minecraft-AI.wiki/
cd Nexus-Minecraft-AI.wiki
git add . && git commit -m "Update wiki" && git push
```
</content>
