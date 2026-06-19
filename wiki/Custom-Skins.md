# Custom Skins

Change the assistant's look with `/ai skin <name>`.

```
/ai skin robot           # built-in (also: void, slate, ember, forest, amethyst)
/ai skin default         # vanilla Steve
/ai skin minecraft:textures/entity/player/wide/steve.png   # any texture id
/ai skin my_skin         # PNG in config/blockpal/skins/my_skin.png
```

## Built-in skins

`default` · `steve` · `robot` · `void` · `slate` · `ember` · `forest` · `amethyst`

## Drop-in custom skins (no rebuild)

1. Place a standard **64×64** player-skin PNG in `config/blockpal/skins/`.
   - In `/ai menu → Identity`, the **Open skins folder** button jumps straight there
     (it creates the folder first if needed).
2. Apply it with `/ai skin <filename-without-extension>`.
3. Edited a PNG? `/aiskins reload` hot-reloads the folder — no restart needed.
   `/aiskins list` shows what's currently in the folder.

PNGs are loaded into dynamic textures on demand (cached, lazy GPU upload), so no
rebuild of the mod is required.

## Resolution order

When you set a skin, it's resolved in this order:

1. vanilla `default` / `steve`
2. an explicit `namespace:path` texture id
3. a PNG in the skins folder
4. a baked-in skin
</content>
