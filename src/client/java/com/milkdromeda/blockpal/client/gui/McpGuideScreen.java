package com.milkdromeda.blockpal.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>The in-game manual for connecting your own AI to your world.</b>
 *
 * <p>Opened with {@code /ai mcp}. The MCP connection is the easiest way to give a bot a
 * brain — you point an AI app you already pay for at the world instead of pasting an API
 * key into the game — but "easiest" only holds if the setup instructions are right there
 * with the address and token already filled in. So this screen has a tab per app
 * (Claude, ChatGPT, Grok, Gemini), each showing the exact config for <i>this</i> server,
 * with copy buttons for the two things you actually need to paste.
 *
 * <p>The instructions describe where each app keeps its connector settings. Those menus
 * do move between versions — the address, the transport and the token are the parts that
 * matter, and they're the parts shown verbatim.
 */
public class McpGuideScreen extends Screen {

    private static final int W = 340;
    private static final int FIELD_H = 20;

    private final boolean running;
    private final String endpoint;
    private final String sseEndpoint;
    private final String token;
    private final String status;

    private int tab;
    private static final String[] TABS = {"What is this", "Claude", "ChatGPT", "Grok", "Gemini"};

    public McpGuideScreen(boolean running, String endpoint, String sseEndpoint, String token, String status) {
        super(Component.literal("Blockpal — Connect your AI"));
        this.running = running;
        this.endpoint = endpoint == null ? "" : endpoint;
        this.sseEndpoint = sseEndpoint == null ? "" : sseEndpoint;
        this.token = token == null ? "" : token;
        this.status = status == null ? "" : status;
    }

    @Override
    protected void init() {
        addRenderableWidget(TechTheme.centered(this.font, this.width, 8, 12,
                TechTheme.title("Connect your AI (MCP)")));

        // ── tab row ──
        int tabW = 66, gap = 2;
        int rowW = TABS.length * tabW + (TABS.length - 1) * gap;
        int tx = this.width / 2 - rowW / 2;
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            Button b = Button.builder(Component.literal(TABS[i]), btn -> { tab = index; rebuildWidgets(); })
                    .bounds(tx + i * (tabW + gap), 22, tabW, FIELD_H).build();
            b.active = i != tab;   // the current tab reads as pressed
            addRenderableWidget(b);
        }

        // ── body ──
        LinearLayout body = LinearLayout.vertical().spacing(2);
        for (String line : lines()) {
            body.addChild(new StringWidget(W, 11, Component.literal(line), this.font));
        }
        ScrollableLayout scroll = new ScrollableLayout(this.minecraft, body,
                Math.max(FIELD_H, this.height - 50 - 52));
        scroll.setMinWidth(W + 12);
        scroll.arrangeElements();
        scroll.setX(this.width / 2 - (W + 12) / 2);
        scroll.setY(48);
        scroll.visitWidgets(this::addRenderableWidget);

        // ── copy bar ──
        int bw = 110;
        int barW = bw * 3 + gap * 8;
        int bx = this.width / 2 - barW / 2;
        int by = this.height - FIELD_H * 2 - 12;
        addRenderableWidget(Button.builder(Component.literal("Copy address"), b -> copy(endpoint))
                .bounds(bx, by, bw, FIELD_H).build());
        Button copyToken = Button.builder(Component.literal("Copy token"), b -> copy(token))
                .bounds(bx + bw + gap * 4, by, bw, FIELD_H).build();
        copyToken.active = !token.isBlank();
        addRenderableWidget(copyToken);
        addRenderableWidget(Button.builder(Component.literal("Copy config"), b -> copy(configSnippet()))
                .bounds(bx + (bw + gap * 4) * 2, by, bw, FIELD_H).build());

        addRenderableWidget(Button.builder(Component.literal("Done ✓"), b -> onClose())
                .bounds(this.width / 2 - 60, this.height - FIELD_H - 6, 120, FIELD_H).build());
    }

    private void copy(String text) {
        if (this.minecraft != null && text != null && !text.isBlank()) {
            this.minecraft.keyboardHandler.setClipboard(text);
        }
    }

    // ── content ─────────────────────────────────────────────────────────────────

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(running ? "§aServer running — §f" + status : "§cNot running — §f" + status);
        out.add("§7Address: §f" + endpoint);
        out.add(token.isBlank() ? "§7No token required (token check is off)."
                : "§7Token: §f" + masked());
        out.add("");
        switch (tab) {
            case 0 -> overview(out);
            case 1 -> claude(out);
            case 2 -> chatgpt(out);
            case 3 -> grok(out);
            default -> gemini(out);
        }
        return out;
    }

    private String masked() {
        // Shown masked so a stream/screenshot doesn't leak it; Copy token gives the real one.
        if (token.length() <= 10) return "•".repeat(token.length());
        return token.substring(0, 6) + "•".repeat(Math.max(4, token.length() - 10))
                + token.substring(token.length() - 4);
    }

    private void overview(List<String> out) {
        out.add("§l§bWhat this does");
        out.add("§fInstead of putting an AI key into the game,");
        out.add("§fthe game offers §btools §fto an AI app you");
        out.add("§falready use. The app looks through your");
        out.add("§fcompanion's eyes and writes little scripts");
        out.add("§fthat walk, mine, build and use chests.");
        out.add("");
        out.add("§l§bWhat the AI can and can't do");
        out.add("§a✔ §fsee a picture of what the bot sees");
        out.add("§a✔ §fpress its movement keys and mouse buttons");
        out.add("§a✔ §fopen chests and furnaces within reach");
        out.add("§c✘ §fteleport, fly, or place blocks it isn't holding");
        out.add("§c✘ §fsee through walls or read the whole map");
        out.add("");
        out.add("§l§bBefore you start");
        out.add("§71. This must be the chosen AI connection:");
        out.add("§f   /ai connection mcp §7(only one may be on)");
        out.add("§72. The AI app must run on §fthis same machine§7,");
        out.add("§7   unless you turn on §f/ai mcp remote on§7.");
        out.add("§73. Keep the token secret — it drives your bots.");
        out.add("");
        out.add("§7Older apps may need the SSE address instead:");
        out.add("§f" + sseEndpoint);
    }

    private void claude(List<String> out) {
        out.add("§l§bClaude Desktop");
        out.add("§fSettings → Developer → Edit Config, then add");
        out.add("§fthis to §7claude_desktop_config.json§f:");
        out.add("");
        for (String line : claudeConfig().split("\n")) out.add("§7" + line);
        out.add("");
        out.add("§fRestart Claude. Blockpal appears under the");
        out.add("§ftools icon. Say: §a\"look through Ethan's eyes");
        out.add("§aand chop the nearest tree\"§f.");
        out.add("");
        out.add("§l§bClaude Code §7(terminal)");
        out.add("§a claude mcp add --transport http blockpal \\");
        out.add("§a   " + endpoint + " \\");
        out.add("§a   --header \"Authorization: Bearer <token>\"");
        out.add("");
        out.add("§7Claude Desktop talks to §fremote§7 servers through");
        out.add("§7the §fmcp-remote§7 bridge (that's the npx line) —");
        out.add("§7it needs Node.js installed.");
    }

    private void chatgpt(List<String> out) {
        out.add("§l§bChatGPT");
        out.add("§fChatGPT connects to MCP servers as a");
        out.add("§b\"connector\"§f, set up in Settings → Connectors.");
        out.add("§fAdd a custom connector with:");
        out.add("");
        out.add("§7 URL:   §f" + endpoint);
        out.add("§7 Auth:  §fBearer token");
        out.add("§7 Token: §fthe one above (Copy token)");
        out.add("");
        out.add("§e⚠ Important:");
        out.add("§fChatGPT runs in OpenAI's cloud, so it cannot");
        out.add("§freach §flocalhost§f on your PC. You must expose");
        out.add("§fthis address to the internet first, e.g. with a");
        out.add("§ftunnel (ngrok/Cloudflare), and turn on");
        out.add("§a  /ai mcp remote on");
        out.add("");
        out.add("§7Anything you expose is reachable by anyone who");
        out.add("§7learns the URL §fand§7 the token — keep the token");
        out.add("§7on, and stop the tunnel when you're done.");
        out.add("");
        out.add("§7Desktop apps that run §fon§7 your PC (Claude");
        out.add("§7Desktop, Gemini CLI) don't need any of that.");
    }

    private void grok(List<String> out) {
        out.add("§l§bGrok (xAI)");
        out.add("§fGrok speaks MCP through its connector/tool");
        out.add("§fsettings, and the xAI API can be given an MCP");
        out.add("§fserver directly. Point either at:");
        out.add("");
        out.add("§7 URL:   §f" + endpoint);
        out.add("§7 Header:§fAuthorization: Bearer <token>");
        out.add("");
        out.add("§e⚠ §fLike ChatGPT, grok.com runs in the cloud and");
        out.add("§fcannot see §flocalhost§f — expose the address with");
        out.add("§fa tunnel and turn on §a/ai mcp remote on§f first.");
        out.add("");
        out.add("§fIf you're calling the xAI API from your own");
        out.add("§fscript, pass this server in the request's MCP");
        out.add("§fserver list with the same URL and header.");
    }

    private void gemini(List<String> out) {
        out.add("§l§bGemini / Google AI Studio");
        out.add("§fThe §bGemini CLI§f runs on your own machine, so it");
        out.add("§freaches this server with no tunnel. Add it to");
        out.add("§f~/.gemini/settings.json:");
        out.add("");
        for (String line : geminiConfig().split("\n")) out.add("§7" + line);
        out.add("");
        out.add("§fThen ask it to look through your bot's eyes.");
        out.add("");
        out.add("§fIn §bGoogle AI Studio§f, add this under the tools /");
        out.add("§fMCP section of your app, using the same URL and");
        out.add("§fBearer token. AI Studio runs in Google's cloud,");
        out.add("§fso it needs the address exposed to the internet");
        out.add("§f(§a/ai mcp remote on§f + a tunnel), exactly like");
        out.add("§fChatGPT.");
    }

    /** The snippet the "Copy config" button hands over, tailored to the open tab. */
    private String configSnippet() {
        return switch (tab) {
            case 2, 3 -> endpoint + "\nAuthorization: Bearer " + token;
            case 4 -> geminiConfig();
            default -> claudeConfig();
        };
    }

    private String claudeConfig() {
        return """
            {
              "mcpServers": {
                "blockpal": {
                  "command": "npx",
                  "args": ["-y", "mcp-remote", "%s",
                           "--header", "Authorization: Bearer %s"]
                }
              }
            }""".formatted(endpoint, token);
    }

    private String geminiConfig() {
        return """
            {
              "mcpServers": {
                "blockpal": {
                  "httpUrl": "%s",
                  "headers": { "Authorization": "Bearer %s" }
                }
              }
            }""".formatted(endpoint, token);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        TechTheme.backdrop(g, this.width, this.height);
        TechTheme.panel(g, this.width / 2 - (W + 12) / 2 - 10, 2,
                this.width / 2 + (W + 12) / 2 + 10, this.height - 2);
        TechTheme.rule(g, this.width / 2 - 150, this.width / 2 + 150, 44);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
