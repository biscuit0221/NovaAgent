package com.novaagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaagent.llm.ToolSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class WebSearchTool implements Tool {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String endpoint;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final RateLimiter limiter = new RateLimiter(30, 60_000);
    private final PrivateIpFilter ipFilter = new PrivateIpFilter();

    public WebSearchTool(String apiKey, String endpoint) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
    }

    @Override
    public ToolSpec.FunctionSpec spec() {
        Map<String, Object> schema = PathGuard.schema(
            "Search the public web and return short summaries with URLs.",
            List.of(
                PathGuard.Param.required("query", "string", "Search keywords"),
                PathGuard.Param.optional("top_k", "integer", "How many results; 1-20, defaults to 5")
            ));
        return new ToolSpec.FunctionSpec("web_search",
            "Run a web search using the configured provider; returns candidates.",
            schema);
    }

    @Override
    public String invoke(Map<String, Object> args) {
        Object queryObj = args.get("query");
        if (queryObj == null || queryObj.toString().isBlank()) return "missing required argument: query";
        if (apiKey == null || apiKey.isBlank()) return "Web Search key is not configured.";
        if (!limiter.acquire()) return "rate-limited (30/min); please retry shortly.";
        int topK = 5;
        Object tk = args.get("top_k");
        if (tk instanceof Number n) topK = Math.max(1, Math.min(20, n.intValue()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", queryObj.toString());
        body.put("top_k", topK);
        Request req;
        try {
            req = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return "request body serialization failed: " + ex.getMessage();
        }
        try (Response resp = http.newCall(req).execute()) {
            try (ResponseBody rb = resp.body()) {
                if (!resp.isSuccessful()) {
                    return "search failed, HTTP " + resp.code() + ": " + (rb == null ? "" : rb.string());
                }
                if (rb == null) return "search failed: empty response body";
                JsonNode root = mapper.readTree(rb.byteStream());
                JsonNode arr = root.path("results");
                if (!arr.isArray() || arr.size() == 0) arr = root.path("data").path("web_pages");
                if (!arr.isArray() || arr.size() == 0) return "no results.";
                StringBuilder out = new StringBuilder();
                int kept = 0;
                for (JsonNode r : arr) {
                    String title = r.path("title").asText("");
                    String url = r.path("url").asText(r.path("link").asText(""));
                    String content = r.path("content").asText(r.path("snippet").asText(""));
                    if (url.isBlank()) continue;
                    if (ipFilter.isBlocked(url)) continue;
                    kept++;
                    out.append("- ");
                    if (!title.isBlank()) out.append(title).append('\n');
                    out.append("  ").append(url).append('\n');
                    if (!content.isBlank()) out.append("  ").append(content).append('\n');
                    if (kept >= topK) break;
                }
                if (kept == 0) return "all URLs were filtered out by the private-IP blocklist.";
                out.insert(0, "Search results (" + kept + "):\n");
                return out.toString();
            }
        } catch (IOException e) {
            return "search request failed: " + e.getMessage();
        }
    }

    static final class RateLimiter {
        private final int maxTokensPerWindow;
        private final long windowMs;
        private long windowStart;
        private int tokensUsed;

        RateLimiter(int max, long windowMs) {
            this.maxTokensPerWindow = max;
            this.windowMs = windowMs;
            this.windowStart = System.currentTimeMillis();
            this.tokensUsed = 0;
        }

        synchronized boolean acquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                windowStart = now;
                tokensUsed = 0;
            }
            if (tokensUsed >= maxTokensPerWindow) return false;
            tokensUsed++;
            return true;
        }
    }

    static final class PrivateIpFilter {
        boolean isBlocked(String url) {
            String lower = url.toLowerCase();
            if (lower.startsWith("file://")) return true;
            if (lower.startsWith("http://10.") || lower.startsWith("http://192.168.")
                || lower.startsWith("http://127.") || lower.startsWith("http://localhost")
                || lower.startsWith("http://0.0.0.0")) return true;
            if (lower.startsWith("https://localhost") || lower.startsWith("https://127.")) return true;
            return false;
        }
    }
}