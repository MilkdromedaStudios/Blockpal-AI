package com.milkdromeda.blockpal.client;

import com.milkdromeda.blockpal.ModEntities;
import com.milkdromeda.blockpal.client.assist.ClientPossession;
import com.milkdromeda.blockpal.client.assist.ScreenWatcher;
import com.milkdromeda.blockpal.client.gui.AdminScreen;
import com.milkdromeda.blockpal.client.gui.AiConfigScreen;
import com.milkdromeda.blockpal.client.gui.AssistantChatScreen;
import com.milkdromeda.blockpal.client.gui.BotManagerScreen;
import com.milkdromeda.blockpal.client.gui.HostScreen;
import com.milkdromeda.blockpal.client.gui.PlayerSettingsScreen;
import com.milkdromeda.blockpal.client.gui.PossessionConsoleScreen;
import com.milkdromeda.blockpal.client.gui.PossessionDriveScreen;
import com.milkdromeda.blockpal.client.gui.TutorialScreen;
import com.milkdromeda.blockpal.client.host.HostManager;
import com.milkdromeda.blockpal.client.render.AiAssistantEntityModel;
import com.milkdromeda.blockpal.client.render.AiAssistantEntityRenderer;
import com.milkdromeda.blockpal.client.render.RuntimeSkins;
import com.milkdromeda.blockpal.network.AdminSyncPayload;
import com.milkdromeda.blockpal.network.BotListSyncPayload;
import com.milkdromeda.blockpal.network.ConfigSyncPayload;
import com.milkdromeda.blockpal.network.OpenTutorialPayload;
import com.milkdromeda.blockpal.network.PlayerPrefsSyncPayload;
import com.milkdromeda.blockpal.network.PossessionSyncPayload;
import com.milkdromeda.blockpal.network.VoiceSpeakPayload;
import com.milkdromeda.blockpal.client.voice.TextToSpeech;
import com.milkdromeda.blockpal.client.voice.VoiceClient;
import com.milkdromeda.blockpal.client.voice.VoicePlayback;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class AiAssistantClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(
                AiAssistantEntityModel.LAYER,
                AiAssistantEntityModel::createModelData
        );

        EntityRendererRegistry.register(
                ModEntities.AI_ASSISTANT,
                AiAssistantEntityRenderer::new
        );

        // Server sent us the current config (via /ai menu) — open the settings screen.
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    FpsGuardian.setPreset(payload.data().performancePreset());
                    context.client().setScreenAndShow(new AiConfigScreen(payload.data()));
                }));

        // Server sent an admin snapshot (via /ai admin menu, or after an action) —
        // open/refresh the admin panel. Only admins ever receive this packet.
        ClientPlayNetworking.registerGlobalReceiver(AdminSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(new AdminScreen(payload.data()))));

        // Server sent the personal preferences snapshot (via /ai mymenu, or after a
        // save) — open/refresh the per-player settings screen.
        ClientPlayNetworking.registerGlobalReceiver(PlayerPrefsSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(new PlayerSettingsScreen(payload))));

        // Server asked us to open the how-to tutorial (first join, or /ai tutorial).
        ClientPlayNetworking.registerGlobalReceiver(OpenTutorialPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(new TutorialScreen())));

        // Server sent the bot list (via /ai bots or the Bots panel tab) — open/refresh
        // the visual Bots manager, keeping the current selection across refreshes.
        ClientPlayNetworking.registerGlobalReceiver(BotListSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(
                                new BotManagerScreen(payload.data(), BotManagerScreen.lastSelected()))));

        // Possession-mode update: open the console (open=true) or append a live status
        // line to an already-open console. Handled in place so typing isn't interrupted.
        ClientPlayNetworking.registerGlobalReceiver(PossessionSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        PossessionConsoleScreen.handleSync(context.client(),
                                payload.open(), payload.active(), payload.line())));

        // Your agent (or one shared with you) said something — speak it out loud.
        // The server has already limited delivery to allowed listeners and
        // turn-taken linked agents; playback here is one utterance at a time.
        ClientPlayNetworking.registerGlobalReceiver(VoiceSpeakPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        VoiceClient.onSpeak(context.client(),
                                payload.speaker(), payload.voice(), payload.text())));

        // Client-side assistant loop: drives off-server possession (only where allowed)
        // and lets the "mini wiki" watcher offer the occasional private survival tip.
        // Both are client-only, so they work on any server — even without Blockpal.
        // The voice tick polls the push-to-talk key (hold to record, release to send).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPossession.tick();
            ScreenWatcher.tick();
            VoiceClient.tick(client);
        });

        // Extreme frame-rate watchdog: auto-disable the mod if FPS collapses.
        FpsGuardian.register();

        // Make the custom-skins folder (config/blockpal/skins/) and scan it.
        RuntimeSkins.init();

        // Client-side helper command to list / reload skins dropped into that folder.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("aiskins")
                        .executes(ctx -> listSkins(ctx.getSource()))
                        .then(ClientCommands.literal("list")
                                .executes(ctx -> listSkins(ctx.getSource())))
                        .then(ClientCommands.literal("reload")
                                .executes(ctx -> {
                                    RuntimeSkins.reload();
                                    return listSkins(ctx.getSource());
                                }))));

        // Client-side assistant commands. These are CLIENT commands so they exist even
        // on servers that don't run Blockpal (where the server-side /ai tree is absent):
        //   /aichat            — open the private AI chat box
        //   /aidrive           — open the off-server possession console
        //   /aidrive <text>    — start/queue an instruction for it (text-driven)
        //   /aidrive stop      — hand control back to you
        //   /aitips on|off     — toggle the private on-screen tips
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("aichat")
                    .executes(ctx -> { openChat(ctx.getSource().getClient(), null); return 1; }));

            dispatcher.register(ClientCommands.literal("aidrive")
                    .executes(ctx -> {
                        Minecraft mc = ctx.getSource().getClient();
                        mc.execute(() -> mc.setScreenAndShow(new PossessionDriveScreen()));
                        return 1;
                    })
                    .then(ClientCommands.literal("stop").executes(ctx -> {
                        ClientPossession.stop();
                        ctx.getSource().sendFeedback(Component.literal("§7Driving stopped — you have control again."));
                        return 1;
                    }))
                    .then(ClientCommands.argument("instruction", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String status = ClientPossession.start(StringArgumentType.getString(ctx, "instruction"));
                                ctx.getSource().sendFeedback(Component.literal(status));
                                return 1;
                            })));

            // Client-local voice controls (the server side lives under /ai voice):
            //   /aivoice            — status: key, default voice, responses on/off
            //   /aivoice on|off     — hear your agent speak (text-to-speech) or not
            //   /aivoice key <code> — rebind push-to-talk (GLFW key code; 86 = V)
            //   /aivoice voice <id> — your default TTS voice (alloy, nova, onyx…)
            //   /aivoice stop       — make it stop talking right now
            //   /aivoice test <txt> — hear a line with your current default voice
            dispatcher.register(ClientCommands.literal("aivoice")
                    .executes(ctx -> voiceStatus(ctx.getSource()))
                    .then(ClientCommands.literal("on").executes(ctx -> setVoiceResponses(ctx.getSource(), true)))
                    .then(ClientCommands.literal("off").executes(ctx -> setVoiceResponses(ctx.getSource(), false)))
                    .then(ClientCommands.literal("stop").executes(ctx -> {
                        VoicePlayback.stopAll();
                        ctx.getSource().sendFeedback(Component.literal("§7Okay, quiet now."));
                        return 1;
                    }))
                    .then(ClientCommands.literal("key")
                            .then(ClientCommands.argument("code", IntegerArgumentType.integer(1, 512))
                                    .executes(ctx -> {
                                        com.milkdromeda.blockpal.config.ModConfig cfg =
                                                com.milkdromeda.blockpal.config.ModConfig.get();
                                        cfg.voicePushToTalkKey = IntegerArgumentType.getInteger(ctx, "code");
                                        cfg.save();
                                        ctx.getSource().sendFeedback(Component.literal(
                                                "§bPush-to-talk key set to GLFW code §f" + cfg.voicePushToTalkKey
                                                        + "§b (86=V, 66=B, 71=G — see GLFW key codes)."));
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("voice")
                            .then(ClientCommands.argument("id", StringArgumentType.word())
                                    .executes(ctx -> {
                                        com.milkdromeda.blockpal.config.ModConfig cfg =
                                                com.milkdromeda.blockpal.config.ModConfig.get();
                                        cfg.ttsVoice = StringArgumentType.getString(ctx, "id")
                                                .toLowerCase(java.util.Locale.ROOT);
                                        cfg.save();
                                        ctx.getSource().sendFeedback(Component.literal(
                                                "§bDefault agent voice set to §f" + cfg.ttsVoice
                                                        + "§b. Per-bot voices: §f/ai voice set <id>§b."));
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("test")
                            .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        TextToSpeech.speak(StringArgumentType.getString(ctx, "text"), "");
                                        ctx.getSource().sendFeedback(Component.literal("§7Synthesizing…"));
                                        return 1;
                                    }))));

            dispatcher.register(ClientCommands.literal("aitips")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal("§bBlockpal tips are "
                                + (com.milkdromeda.blockpal.config.ModConfig.get().assistantTips ? "§aON" : "§7off")
                                + " §7— toggle with §f/aitips on|off§7."));
                        return 1;
                    })
                    .then(ClientCommands.literal("on").executes(ctx -> setTips(ctx.getSource(), true)))
                    .then(ClientCommands.literal("off").executes(ctx -> setTips(ctx.getSource(), false))));
        });

        // Inject a tiny "✦" button into any screen where the mouse is free (pause menu,
        // inventory, chests and other containers) so the chat box is one click away
        // without overlapping the vanilla/other-mod widgets. It sits in the top-right
        // corner and is deliberately mini (14×14).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen
                    || screen instanceof AbstractContainerScreen<?>) {
                Button chat = Button.builder(Component.literal("✦"),
                                b -> openChat(client, screen))
                        .bounds(scaledWidth - 18, 2, 14, 14)
                        .build();
                chat.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Blockpal AI chat")));
                Screens.getWidgets(screen).add(chat);
            }
        });

        // "Host with Blockpal": open a server (Minecraft + Fabric + latest Geyser +
        // Floodgate) so Bedrock friends can join your Java world. Client-only, so a
        // Bedrock player has no way to host — they can only join. Reachable from the
        // pause menu (singleplayer) and via /aihost.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("aihost")
                        .executes(ctx -> openHost(ctx.getSource()))
                        .then(ClientCommands.literal("status").executes(ctx -> hostStatus(ctx.getSource())))
                        .then(ClientCommands.literal("stop").executes(ctx -> {
                            HostManager.get().stop();
                            ctx.getSource().sendFeedback(Component.literal("§eStopping the Blockpal host…"));
                            return 1;
                        }))));

        // Add a "Host with Blockpal" button to the pause menu, but only in singleplayer
        // (hosting a separate server from inside someone else's server makes no sense).
        // Opening it remembers WHICH world you're in, so the screen can offer to host
        // that exact world (copy → play → sync back).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen && client.hasSingleplayerServer()) {
                // Bottom-LEFT corner, hugging the screen edge. The pause menu's own
                // buttons are a vertically-centered column, so a centered position
                // near the bottom (the old scaledHeight - 52) landed right on top of
                // "Save and Quit to Title" at larger GUI scales — and client overlays
                // like Lunar's crowd the center even more. The corner is always clear.
                Button host = Button.builder(Component.literal("Host with Blockpal"),
                                b -> {
                                    captureSourceWorld(client);
                                    client.setScreenAndShow(new HostScreen(screen));
                                })
                        .bounds(4, scaledHeight - 24, 130, 20)
                        .build();
                Screens.getWidgets(screen).add(host);
            }
            // While a host is active (or a world sync is waiting), the title screen gets a
            // re-entry button — after "host current world" leaves the world, this is how
            // you get back to the status screen and the connect addresses.
            if (screen instanceof TitleScreen
                    && (HostManager.get().phase() != HostManager.Phase.IDLE
                        || HostManager.get().isRunning() || HostManager.get().pendingSync())) {
                Button host = Button.builder(Component.literal("Blockpal Host…"),
                                b -> client.setScreenAndShow(new HostScreen(screen)))
                        .bounds(4, 4, 110, 20)
                        .build();
                Screens.getWidgets(screen).add(host);
            }
        });
    }

    /** Remembers the open singleplayer world so the Host screen can offer to host it. */
    private static void captureSourceWorld(net.minecraft.client.Minecraft client) {
        try {
            if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
                java.nio.file.Path root = client.getSingleplayerServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath().normalize();
                String name = root.getFileName() == null ? "world" : root.getFileName().toString();
                HostManager.get().setSourceWorld(root, name);
            }
        } catch (Exception ignored) {
            // No world captured — the screen simply offers fresh-world hosting.
        }
    }

    /** Opens the private AI chat box, remembering the screen to return to (if any). */
    private static void openChat(Minecraft client, Screen parent) {
        client.execute(() -> client.setScreenAndShow(new AssistantChatScreen(parent)));
    }

    private static int voiceStatus(FabricClientCommandSource src) {
        com.milkdromeda.blockpal.config.ModConfig cfg = com.milkdromeda.blockpal.config.ModConfig.get();
        src.sendFeedback(Component.literal(
                "§6=== Blockpal voice (this client) ===\n"
                + "§7Agent speech: " + (cfg.voiceResponses ? "§aON" : "§7off") + " §7— toggle with §f/aivoice on|off\n"
                + "§7Push-to-talk key: §fGLFW " + cfg.voicePushToTalkKey + (cfg.voicePushToTalkKey == 86 ? " (V)" : "")
                + " §7— hold it (no menu open) to talk to YOUR companion\n"
                + "§7Default voice: §f" + cfg.ttsVoice + " §7— change with §f/aivoice voice <id>\n"
                + "§7Transcription: §f" + cfg.sttModel + "\n"
                + "§7Sharing/linking lives server-side: §f/ai voice§7."));
        return 1;
    }

    private static int setVoiceResponses(FabricClientCommandSource src, boolean on) {
        com.milkdromeda.blockpal.config.ModConfig cfg = com.milkdromeda.blockpal.config.ModConfig.get();
        cfg.voiceResponses = on;
        cfg.save();
        if (!on) VoicePlayback.stopAll();
        src.sendFeedback(Component.literal("§bAgent speech: " + (on ? "§aON" : "§7off")));
        return 1;
    }

    private static int setTips(FabricClientCommandSource src, boolean on) {
        com.milkdromeda.blockpal.config.ModConfig cfg = com.milkdromeda.blockpal.config.ModConfig.get();
        cfg.assistantTips = on;
        cfg.save();
        src.sendFeedback(Component.literal("§bBlockpal tips: " + (on ? "§aON" : "§7off")));
        return 1;
    }

    private static int openHost(FabricClientCommandSource src) {
        src.getClient().execute(() -> {
            captureSourceWorld(src.getClient());
            src.getClient().setScreenAndShow(new HostScreen(null));
        });
        return 1;
    }

    private static int hostStatus(FabricClientCommandSource src) {
        HostManager h = HostManager.get();
        StringBuilder sb = new StringBuilder("§6Blockpal host: §f" + h.phase().label + " §7— " + h.status());
        if (h.isRunning()) {
            sb.append("\n§eJava: §f").append(h.localIp()).append(":").append(h.javaPort())
                    .append(" §7(LAN) / §f").append(h.publicIp()).append(":").append(h.javaPort()).append(" §7(internet)");
            sb.append("\n§eBedrock: §f").append(h.localIp()).append(" port ").append(h.bedrockPort())
                    .append(" §7(LAN) / §f").append(h.publicIp()).append(" port ").append(h.bedrockPort()).append(" §7(internet)");
        }
        final String out = sb.toString();
        src.sendFeedback(Component.literal(out));
        return 1;
    }

    private static int listSkins(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src) {
        Set<String> names = RuntimeSkins.names();
        if (names.isEmpty()) {
            src.sendFeedback(Component.literal(
                    "§eNo custom skins yet. §7Drop a 64×64 PNG into:\n§f" + RuntimeSkins.SKIN_DIR
                            + "\n§7then run §f/aiskins reload§7, and apply it with §f/ai skin <name>§7."));
        } else {
            src.sendFeedback(Component.literal(
                    "§aCustom skins (" + names.size() + "): §f" + String.join("§7, §f", names)
                            + "\n§7Apply one with §f/ai skin <name>§7."));
        }
        return 1;
    }
}
