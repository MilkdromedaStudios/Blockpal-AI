package com.milkdromeda.blockpal.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.milkdromeda.blockpal.ai.AiConnection;
import com.milkdromeda.blockpal.config.ModConfig;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>Blockpal's MCP server — the easy way to give the bot a brain.</b>
 *
 * <p>Every other AI connection asks you to paste an API key into the game and pay for
 * tokens twice. This one turns the relationship around: the mod <i>hosts</i> a
 * <a href="https://modelcontextprotocol.io">Model Context Protocol</a> server, and an AI
 * app you already have — Claude, ChatGPT, Grok, Gemini — connects to your world as a
 * tool. No key is stored in the game, the AI is whatever you already pay for, and you
 * can watch it think in the app's own window while the bot moves in yours.
 *
 * <p>Two transports are served, because clients differ:
 * <ul>
 *   <li><b>Streamable HTTP</b> — {@code POST /mcp} with a JSON-RPC body, the current
 *       spec, used by most modern clients and by {@code mcp-remote};</li>
 *   <li><b>HTTP + SSE</b> — {@code GET /sse} opens the event stream and replies with the
 *       endpoint to post to ({@code POST /messages?sessionId=…}); the older transport a
 *       number of clients still speak.</li>
 * </ul>
 *
 * <p><b>Safety.</b> It binds to loopback only unless {@code mcpAllowRemote} is turned on,
 * and every request must carry {@code Authorization: Bearer <token>} (a {@code ?token=}
 * query parameter is accepted for clients that can't set headers). The token is
 * generated on first start, kept obfuscated at rest like the API key, and shown in-game
 * with {@code /ai mcp} — never logged.
 */
public final class McpServer {

    /** MCP revision this server implements. Sent back verbatim in {@code initialize}. */
    public static final String PROTOCOL_VERSION = "2025-06-18";

    private static final Gson GSON = new Gson();

    private static HttpServer http;
    private static ScheduledExecutorService keepAlive;
    private static MinecraftServer minecraft;
    private static volatile String status = "not started";
    private static volatile int boundPort = -1;

    /** Open SSE streams (legacy transport), keyed by the session id we handed the client. */
    private static final Map<String, OutputStream> SSE = new ConcurrentHashMap<>();

    private McpServer() {}

    // ── lifecycle ───────────────────────────────────────────────────────────────

    /** Starts the server if the AI connection is MCP; stops it otherwise. Safe to re-call. */
    public static synchronized void sync(MinecraftServer server) {
        minecraft = server;
        if (ModConfig.get().connection() == AiConnection.MCP) {
            start(server);
        } else {
            stop();
        }
    }

    public static synchronized void start(MinecraftServer server) {
        minecraft = server;
        ModConfig cfg = ModConfig.get();
        if (http != null && boundPort == cfg.mcpPort) return;   // already listening correctly
        stop();
        cfg.ensureMcpToken();

        try {
            InetAddress bind = cfg.mcpAllowRemote
                    ? new InetSocketAddress(cfg.mcpPort).getAddress()
                    : InetAddress.getLoopbackAddress();
            http = HttpServer.create(new InetSocketAddress(bind, cfg.mcpPort), 8);
            http.createContext("/mcp", McpServer::handleStreamable);
            http.createContext("/sse", McpServer::handleSse);
            http.createContext("/messages", McpServer::handleSseMessage);
            http.createContext("/", McpServer::handleRoot);
            // Cached, not fixed: an SSE handler parks for the life of its stream, so a
            // fixed pool would be exhausted by a few open streams and every later POST
            // would hang waiting for a thread.
            http.setExecutor(Executors.newCachedThreadPool(daemonFactory()));
            http.start();
            boundPort = cfg.mcpPort;
            status = "listening on " + endpoint();
            System.out.println("[Blockpal] MCP server " + status
                    + " (bind " + (cfg.mcpAllowRemote ? "all interfaces" : "localhost only") + ")");

            keepAlive = Executors.newSingleThreadScheduledExecutor(daemonFactory());
            keepAlive.scheduleAtFixedRate(McpServer::pingStreams, 20, 20, TimeUnit.SECONDS);
        } catch (IOException e) {
            status = "couldn't start on port " + cfg.mcpPort + ": " + e.getMessage();
            http = null;
            boundPort = -1;
            System.err.println("[Blockpal] MCP server failed to start: " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        if (keepAlive != null) {
            keepAlive.shutdownNow();
            keepAlive = null;
        }
        for (OutputStream out : SSE.values()) {
            try { out.close(); } catch (IOException ignored) { /* the client went away */ }
        }
        SSE.clear();
        if (http != null) {
            http.stop(0);
            http = null;
            status = "stopped";
            boundPort = -1;
        }
    }

    public static boolean isRunning() {
        return http != null;
    }

    public static String status() {
        return status;
    }

    /** The URL a client should be pointed at, e.g. {@code http://localhost:25569/mcp}. */
    public static String endpoint() {
        ModConfig cfg = ModConfig.get();
        String host = cfg.mcpAllowRemote ? "<this-machine's-ip>" : "localhost";
        return "http://" + host + ":" + cfg.mcpPort + "/mcp";
    }

    /** The legacy SSE URL, for clients that only speak the older transport. */
    public static String sseEndpoint() {
        ModConfig cfg = ModConfig.get();
        String host = cfg.mcpAllowRemote ? "<this-machine's-ip>" : "localhost";
        return "http://" + host + ":" + cfg.mcpPort + "/sse";
    }

    // ── HTTP handlers ───────────────────────────────────────────────────────────

    private static void handleRoot(HttpExchange exchange) throws IOException {
        // A human poking the port in a browser should get a pointer, not a blank page.
        String body = "Blockpal MCP server.\nMCP endpoint: " + endpoint()
                + "\nLegacy SSE endpoint: " + sseEndpoint()
                + "\nRun /ai mcp in-game for setup instructions and your access token.\n";
        send(exchange, 200, "text/plain; charset=utf-8", body);
    }

    private static void handleStreamable(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                cors(exchange);
                send(exchange, 204, "text/plain", "");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Some clients probe GET /mcp for a stream; point them at /sse instead.
                send(exchange, 405, "application/json",
                        GSON.toJson(error(null, -32000, "Use POST for JSON-RPC, or GET " + sseEndpoint()
                                + " for the SSE transport.")));
                return;
            }
            if (!authorised(exchange)) {
                send(exchange, 401, "application/json",
                        GSON.toJson(error(null, -32001, "Missing or wrong access token. "
                                + "Send 'Authorization: Bearer <token>' — run /ai mcp in-game to see it.")));
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(body.isBlank() ? "{}" : body);

            // Batches are legal JSON-RPC; handle them so a batching client isn't broken.
            if (parsed.isJsonArray()) {
                var results = new com.google.gson.JsonArray();
                for (JsonElement el : parsed.getAsJsonArray()) {
                    JsonObject r = dispatch(el.isJsonObject() ? el.getAsJsonObject() : new JsonObject());
                    if (r != null) results.add(r);
                }
                if (results.isEmpty()) { send(exchange, 202, "application/json", ""); return; }
                send(exchange, 200, "application/json", GSON.toJson(results));
                return;
            }

            JsonObject response = dispatch(parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject());
            if (response == null) {            // it was a notification — nothing to answer
                send(exchange, 202, "application/json", "");
                return;
            }
            send(exchange, 200, "application/json", GSON.toJson(response));
        } catch (Exception e) {
            send(exchange, 500, "application/json",
                    GSON.toJson(error(null, -32603, "Blockpal error: " + String.valueOf(e.getMessage()))));
        }
    }

    /** Legacy transport: hold an event stream open and tell the client where to post. */
    private static void handleSse(HttpExchange exchange) throws IOException {
        if (!authorised(exchange)) {
            send(exchange, 401, "text/plain", "Missing or wrong access token (see /ai mcp in-game).");
            return;
        }
        String session = UUID.randomUUID().toString();
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "text/event-stream; charset=utf-8");
        headers.add("Cache-Control", "no-cache");
        headers.add("Connection", "keep-alive");
        headers.add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);

        OutputStream out = exchange.getResponseBody();
        SSE.put(session, out);
        writeEvent(out, "endpoint", "/messages?sessionId=" + session);
        // The handler must not return, or the stream closes. It parks until the client
        // disconnects (a write fails) or the server shuts the stream down.
        try {
            while (SSE.containsKey(session)) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            SSE.remove(session);
            try { out.close(); } catch (IOException ignored) { /* already gone */ }
        }
    }

    /** Legacy transport: JSON-RPC arrives here, the answer goes back over the SSE stream. */
    private static void handleSseMessage(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            cors(exchange);
            send(exchange, 204, "text/plain", "");
            return;
        }
        if (!authorised(exchange)) {
            send(exchange, 401, "text/plain", "Missing or wrong access token.");
            return;
        }
        String session = queryParam(exchange, "sessionId");
        OutputStream stream = session == null ? null : SSE.get(session);
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject response;
        try {
            JsonElement parsed = JsonParser.parseString(body.isBlank() ? "{}" : body);
            response = dispatch(parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject());
        } catch (Exception e) {
            response = error(null, -32700, "Bad JSON: " + e.getMessage());
        }
        send(exchange, 202, "text/plain", "");
        if (response != null && stream != null) {
            writeEvent(stream, "message", GSON.toJson(response));
        }
    }

    // ── JSON-RPC ────────────────────────────────────────────────────────────────

    /** @return the response object, or null when the message was a notification. */
    private static JsonObject dispatch(JsonObject request) {
        String method = str(request, "method");
        JsonElement id = request.get("id");
        boolean notification = id == null || id.isJsonNull();

        if (method == null) {
            return notification ? null : error(id, -32600, "No method given.");
        }
        if (method.startsWith("notifications/")) return null;

        try {
            switch (method) {
                case "initialize" -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("protocolVersion", PROTOCOL_VERSION);
                    JsonObject caps = new JsonObject();
                    caps.add("tools", new JsonObject());
                    result.add("capabilities", caps);
                    JsonObject info = new JsonObject();
                    info.addProperty("name", "blockpal");
                    info.addProperty("title", "Blockpal — Minecraft companion");
                    info.addProperty("version", "1.0.0");
                    result.add("serverInfo", info);
                    result.addProperty("instructions", McpTools.instructions());
                    return ok(id, result);
                }
                case "ping" -> {
                    return ok(id, new JsonObject());
                }
                case "tools/list" -> {
                    JsonObject result = new JsonObject();
                    result.add("tools", McpTools.catalogue());
                    return ok(id, result);
                }
                case "tools/call" -> {
                    JsonObject params = request.has("params") && request.get("params").isJsonObject()
                            ? request.getAsJsonObject("params") : new JsonObject();
                    String tool = str(params, "name");
                    JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                            ? params.getAsJsonObject("arguments") : new JsonObject();
                    return ok(id, McpTools.call(minecraft, tool, args));
                }
                // Answering these keeps strict clients happy even though we have none.
                case "resources/list" -> {
                    JsonObject result = new JsonObject();
                    result.add("resources", new com.google.gson.JsonArray());
                    return ok(id, result);
                }
                case "prompts/list" -> {
                    JsonObject result = new JsonObject();
                    result.add("prompts", new com.google.gson.JsonArray());
                    return ok(id, result);
                }
                default -> {
                    return notification ? null : error(id, -32601, "Unknown method: " + method);
                }
            }
        } catch (Exception e) {
            return error(id, -32603, "Blockpal error: " + String.valueOf(e.getMessage()));
        }
    }

    private static JsonObject ok(JsonElement id, JsonObject result) {
        JsonObject o = new JsonObject();
        o.addProperty("jsonrpc", "2.0");
        if (id != null) o.add("id", id);
        o.add("result", result);
        return o;
    }

    private static JsonObject error(JsonElement id, int code, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        JsonObject o = new JsonObject();
        o.addProperty("jsonrpc", "2.0");
        if (id != null) o.add("id", id); else o.add("id", com.google.gson.JsonNull.INSTANCE);
        o.add("error", err);
        return o;
    }

    // ── plumbing ────────────────────────────────────────────────────────────────

    private static boolean authorised(HttpExchange exchange) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.mcpRequireToken) return true;
        String expected = cfg.mcpToken();
        if (expected.isBlank()) return true;    // not generated yet — don't lock everyone out
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header != null) {
            String value = header.trim();
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) value = value.substring(7).trim();
            if (expected.equals(value)) return true;
        }
        // Some clients can't attach headers; a query token is accepted (and documented
        // as the weaker option, since URLs end up in logs).
        return expected.equals(queryParam(exchange, "token"));
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void cors(HttpExchange exchange) {
        Headers h = exchange.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Headers", "Content-Type, Authorization, Mcp-Session-Id, MCP-Protocol-Version");
        h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    private static void send(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        Headers h = exchange.getResponseHeaders();
        h.add("Content-Type", contentType);
        h.add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } else {
            exchange.close();
        }
    }

    private static void writeEvent(OutputStream out, String event, String data) {
        try {
            synchronized (out) {
                out.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            // The client hung up; the stream is dropped by its own handler loop.
        }
    }

    /** Comment frames keep proxies from timing an idle SSE stream out. */
    private static void pingStreams() {
        for (Map.Entry<String, OutputStream> e : SSE.entrySet()) {
            try {
                synchronized (e.getValue()) {
                    e.getValue().write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    e.getValue().flush();
                }
            } catch (IOException ex) {
                SSE.remove(e.getKey());
            }
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "blockpal-mcp-" + n.incrementAndGet());
            t.setDaemon(true);   // never hold the game open on shutdown
            return t;
        };
    }
}
