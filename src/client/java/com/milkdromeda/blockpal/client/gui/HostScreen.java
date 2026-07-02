package com.milkdromeda.blockpal.client.gui;

import com.milkdromeda.blockpal.client.host.HostManager;
import com.milkdromeda.blockpal.client.host.TunnelManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * "Host with Blockpal" — the Java-only screen that downloads + launches a
 * Bedrock-capable server (Minecraft + Fabric + latest Geyser + Floodgate) and
 * shows the connect addresses. Reached from the pause menu or {@code /aihost}.
 *
 * <p>It's a thin view over {@link HostManager}: the manager does the work on
 * background threads, and this screen polls its status snapshot each tick.
 */
public class HostScreen extends Screen {

    private final Screen parent;
    private StringWidget statusLabel, javaLabel, bedrockLabel, logLabel;
    private Button startBtn, stopBtn;
    private StringWidget tunnelStatusLabel, claimLabel;
    private Button tunnelStartBtn, tunnelStopBtn, copyClaimBtn;

    public HostScreen(Screen parent) {
        super(Component.literal("Host with Blockpal"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        HostManager host = HostManager.get();
        int cx = this.width / 2;
        int w = 320;
        int left = cx - w / 2;
        int y = 32;

        addRenderableWidget(new StringWidget(0, 8, this.width, 12, this.title, this.font));

        // Status + addresses (refreshed in tick()).
        statusLabel = addRenderableWidget(new StringWidget(left, y, w, 12, Component.empty(), this.font));
        y += 18;
        javaLabel = addRenderableWidget(new StringWidget(left, y, w, 12, Component.empty(), this.font));
        y += 14;
        bedrockLabel = addRenderableWidget(new StringWidget(left, y, w, 12, Component.empty(), this.font));
        y += 22;

        // Copy buttons for the LAN addresses (the common case).
        int half = (w - 8) / 2;
        Button copyJava = Button.builder(Component.literal("Copy Java (LAN)"),
                        b -> copy(host.localIp() + ":" + host.javaPort()))
                .bounds(left, y, half, 20).build();
        Button copyBedrock = Button.builder(Component.literal("Copy Bedrock (LAN)"),
                        b -> copy(host.localIp() + "  port " + host.bedrockPort()))
                .bounds(left + half + 8, y, half, 20).build();
        copyJava.setTooltip(Tooltip.create(Component.literal("Copies your LAN address to the clipboard.")));
        addRenderableWidget(copyJava);
        addRenderableWidget(copyBedrock);
        y += 28;

        // Minecraft EULA — a server may not start until this is accepted.
        CycleButton<Boolean> eula = CycleButton.onOffBuilder(host.eulaAccepted())
                .create(left, y, w, 20, Component.literal("Minecraft EULA accepted"),
                        (btn, val) -> host.setEulaAccepted(val));
        eula.setTooltip(Tooltip.create(Component.literal(
                "Required before any server starts. Turning this on accepts the Minecraft EULA (https://aka.ms/MinecraftEULA).")));
        addRenderableWidget(eula);
        y += 26;

        // Start / Stop.
        startBtn = Button.builder(Component.literal("Start hosting"), b -> host.start())
                .bounds(left, y, half, 20).build();
        stopBtn = Button.builder(Component.literal("Stop"), b -> host.stop())
                .bounds(left + half + 8, y, half, 20).build();
        addRenderableWidget(startBtn);
        addRenderableWidget(stopBtn);
        y += 28;

        // No-port-forward tunnel (playit.gg) — optional.
        TunnelManager tun = TunnelManager.get();
        tunnelStatusLabel = addRenderableWidget(new StringWidget(left, y, w, 11, Component.empty(), this.font));
        y += 13;
        tunnelStartBtn = Button.builder(Component.literal("Start tunnel (no port-forward)"), b -> tun.start())
                .bounds(left, y, half, 20).build();
        tunnelStopBtn = Button.builder(Component.literal("Stop tunnel"), b -> tun.stop())
                .bounds(left + half + 8, y, half, 20).build();
        tunnelStartBtn.setTooltip(Tooltip.create(Component.literal(
                "Runs the playit.gg tunnel so friends can join without you forwarding ports or sharing your IP. "
                        + "A free, one-time setup link appears below on first run.")));
        addRenderableWidget(tunnelStartBtn);
        addRenderableWidget(tunnelStopBtn);
        y += 22;
        claimLabel = addRenderableWidget(new StringWidget(left, y, w - 56, 11, Component.empty(), this.font));
        copyClaimBtn = Button.builder(Component.literal("Copy link"), b -> copy(tun.claimUrl()))
                .bounds(left + w - 52, y, 52, 18).build();
        addRenderableWidget(copyClaimBtn);
        y += 22;

        // Safety warning (kept in view, in red).
        addRenderableWidget(redLine(left, y, w,
                "⚠ The addresses below are YOUR computer's — only share with people you trust.")); y += 12;
        addRenderableWidget(redLine(left, y, w,
                "Internet friends need ports 25565 (Java) & 19132/UDP (Bedrock) forwarded — OR use the tunnel above.")); y += 12;
        addRenderableWidget(redLine(left, y, w,
                "Auto-downloads Minecraft, Fabric, Geyser & Floodgate from their official sites.")); y += 18;

        // Latest log line.
        logLabel = addRenderableWidget(new StringWidget(left, y, w, 12, Component.empty(), this.font));
        y += 20;

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx - 50, Math.min(y, this.height - 28), 100, 20).build());

        refresh();
    }

    private StringWidget redLine(int x, int y, int w, String text) {
        return new StringWidget(x, y, w, 11,
                Component.literal(text).withStyle(ChatFormatting.RED), this.font);
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    private void refresh() {
        HostManager host = HostManager.get();
        if (statusLabel != null) {
            statusLabel.setMessage(Component.literal(host.phase().label + " — " + host.status())
                    .withStyle(host.isRunning() ? ChatFormatting.GREEN
                            : host.phase() == HostManager.Phase.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW));
        }
        boolean show = host.isRunning();
        if (javaLabel != null) {
            javaLabel.setMessage(Component.literal(show
                    ? "Java:  LAN " + host.localIp() + ":" + host.javaPort()
                            + "   •   Internet " + host.publicIp() + ":" + host.javaPort()
                    : "Java address shows here once the server is running."));
        }
        if (bedrockLabel != null) {
            bedrockLabel.setMessage(Component.literal(show
                    ? "Bedrock:  LAN " + host.localIp() + " port " + host.bedrockPort()
                            + "   •   Internet " + host.publicIp() + " port " + host.bedrockPort()
                    : "Bedrock address shows here once the server is running."));
        }
        if (logLabel != null) {
            List<String> log = host.recentLog();
            String last = log.isEmpty() ? "" : log.get(log.size() - 1);
            if (last.length() > 64) last = last.substring(last.length() - 64);
            logLabel.setMessage(Component.literal(last).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (startBtn != null) startBtn.active = !host.isRunning() && !host.isBusy() && host.eulaAccepted();
        if (stopBtn != null) stopBtn.active = host.isRunning() || host.isBusy();

        TunnelManager tun = TunnelManager.get();
        if (tunnelStatusLabel != null) {
            tunnelStatusLabel.setMessage(Component.literal("Tunnel: " + tun.phase().label + " — " + tun.status())
                    .withStyle(tun.phase() == TunnelManager.Phase.RUNNING ? ChatFormatting.GREEN
                            : tun.phase() == TunnelManager.Phase.ERROR ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        if (claimLabel != null) {
            claimLabel.setMessage(tun.claimUrl().isEmpty()
                    ? Component.literal("Setup link appears here on first run.").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.literal(tun.claimUrl()).withStyle(ChatFormatting.AQUA));
        }
        if (tunnelStartBtn != null) tunnelStartBtn.active = !tun.isRunning() && !tun.isBusy();
        if (tunnelStopBtn != null) tunnelStopBtn.active = tun.isRunning();
        if (copyClaimBtn != null) copyClaimBtn.active = !tun.claimUrl().isEmpty();
    }

    private void copy(String text) {
        if (this.minecraft != null) this.minecraft.keyboardHandler.setClipboard(text);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
