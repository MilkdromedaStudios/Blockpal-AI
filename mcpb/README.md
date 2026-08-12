# BlockPal Claude Desktop extension

This folder contains the self-contained `.mcpb` bridge for Claude Desktop.

## Why this exists

BlockPal's Minecraft mod hosts MCP over local HTTP, normally at:

`http://localhost:8000/blockpal`

Claude Desktop extensions communicate with Claude over stdio. This bridge translates Claude's stdio MCP messages to BlockPal's local HTTP MCP endpoint.

It intentionally does **not** use `npx`, `npm`, `mcp-remote`, or a system Node installation. Claude Desktop provides the Node runtime used to launch the extension. This avoids the PATH/startup problems caused by the older `npx mcp-remote` configuration.

## Install

1. In Minecraft, select MCP as the AI connection: `/ai connection set mcp`.
2. Run `/ai mcp` and copy the address and token.
3. Download/build `BlockPal.mcpb`.
4. In Claude Desktop, open **Settings → Extensions → Advanced settings → Install Extension…** and select `BlockPal.mcpb`.
5. Enter the BlockPal MCP address and access token when Claude asks.
6. Keep the Minecraft world/server running while using the tools.

The default address is `http://localhost:8000/blockpal`.

## Build locally

From the repository root:

```bash
python mcpb/build.py
```

The bundle is written to:

`mcpb/dist/BlockPal.mcpb`

No third-party Python or Node packages are required to build or run the bridge.

## Security

Never commit a real BlockPal token into this folder. The manifest asks Claude Desktop for the token at install/configuration time and passes it to the bridge through the `BLOCKPAL_TOKEN` environment variable.

The bridge logs diagnostics to stderr so Claude Desktop's MCP logs can show HTTP status/connection failures without corrupting the stdio JSON-RPC stream.
