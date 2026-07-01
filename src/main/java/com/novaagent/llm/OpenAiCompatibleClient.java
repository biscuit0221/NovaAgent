package com.novaagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OpenAI-compatible Chat Completions client backed by OkHttp.
 *
 * Phase 1 default: GLM-5.1 via Zhipu Coding endpoint. The same class
 * also works for DeepSeek, StepFun, Kimi, Agnes, and any other
 * OpenAI-compatible provider.
 */
public final class OpenAiCompatibleClient implements LlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String providerName;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int requestTimeoutSeconds;

    private OpenAiCompatibleClient(Builder b) {
        this.providerName = b.providerName;
        this.baseUrl = stripTrailingSlash(b.baseUrl);
        this.model = b.model;
        this.apiKey = b.apiKey;
        this.requestTimeoutSeconds = b.requestTimeoutSeconds;
        OkHttpClient.Builder hb = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(Math.max(60, b.requestTimeoutSeconds), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true);
        this.http = hb.build();
    }

    private static String stripTrailingSlash(String s) {
        if (s == null || s.isBlank()) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public static Builder newBuilder() { return new Builder(); }

    @Override public String name() { return providerName; }
    @Override public String model() { return model; }

    @Override
    public ChatCompletionResult chat(List<ChatMessage> messages,
                                     List<ToolSpec> tools,
                                     boolean stream,
                                     Consumer<String> onDelta) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("temperature", 0.2);
        root.put("top_p", 0.95);
        if (tools != null && !tools.isEmpty()) {
            ArrayNode arr = root.putArray("tools");
            for (ToolSpec spec : tools) arr.add(mapper.valueToTree(spec.toMap()));
        }
        ArrayNode msgArr = root.putArray("messages");
        for (ChatMessage m : messages) msgArr.add(mapper.valueToTree(m.toMap()));
        root.put("stream", stream);

        RequestBody body = RequestBody.create(root.toString(), JSON);
        Request req = new Request.Builder()
            .url(baseUrl + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", stream ? "text/event-stream" : "application/json")
            .post(body)
            .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new LlmException("LLM HTTP " + resp.code() + ": " + readErrorBody(resp));
            }
            if (stream) return parseStream(resp, onDelta);
            return parseNonStream(resp);
        } catch (IOException e) {
            throw new LlmException("LLM call failed: " + e.getMessage(), e);
        }
    }
    private ChatCompletionResult parseNonStream(Response resp) throws IOException {
        try (ResponseBody body = resp.body()) {
            if (body == null) return new ChatCompletionResult("", List.of(), ChatCompletionResult.STOP, 0, 0, 0);
            JsonNode root = mapper.readTree(body.byteStream());
            JsonNode choice = root.path("choices").path(0);
            JsonNode msg = choice.path("message");
            String content = textOrEmpty(msg.path("content"));
            List<ToolSpec.ToolCall> toolCalls = parseToolCallsFromMessage(msg);
            String finishReason = textOrEmpty(choice.path("finish_reason"));
            if (finishReason.isEmpty()) {
                finishReason = toolCalls.isEmpty() ? ChatCompletionResult.STOP : ChatCompletionResult.TOOL_CALLS;
            }
            return new ChatCompletionResult(
                content,
                toolCalls,
                finishReason,
                root.path("usage").path("prompt_tokens").asInt(0),
                root.path("usage").path("completion_tokens").asInt(0),
                root.path("usage").path("total_tokens").asInt(0));
        }
    }
    private ChatCompletionResult parseStream(Response resp, Consumer<String> onDelta) throws IOException {
        try (ResponseBody body = resp.body()) {
            if (body == null) return new ChatCompletionResult("", List.of(), ChatCompletionResult.STOP, 0, 0, 0);
            BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            StreamToolCallAccumulator toolAcc = new StreamToolCallAccumulator();
            String finishReason = ChatCompletionResult.STOP;
            int prompt = 0, completion = 0, total = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if ("[DONE]".equals(payload)) break;
                JsonNode node;
                try {
                    node = mapper.readTree(payload);
                } catch (IOException ex) {
                    LOG.warn("skip malformed SSE frame: {}", payload);
                    continue;
                }
                JsonNode choice = node.path("choices").path(0);
                if (choice.isMissingNode() || choice.isNull()) continue;
                JsonNode delta = choice.path("delta");
                String deltaContent = textOrEmpty(delta.path("content"));
                if (!deltaContent.isEmpty()) {
                    content.append(deltaContent);
                    if (onDelta != null) {
                        try { onDelta.accept(deltaContent); } catch (RuntimeException ignored) {}
                    }
                }
                if (delta.has("tool_calls")) toolAcc.accept(delta.path("tool_calls"));
                String fr = textOrEmpty(choice.path("finish_reason"));
                if (!fr.isEmpty()) finishReason = fr;
                JsonNode usage = node.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    prompt = usage.path("prompt_tokens").asInt(prompt);
                    completion = usage.path("completion_tokens").asInt(completion);
                    total = usage.path("total_tokens").asInt(total);
                }
            }
            List<ToolSpec.ToolCall> toolCalls = toolAcc.finish();
            if (!toolCalls.isEmpty()) finishReason = ChatCompletionResult.TOOL_CALLS;
            return new ChatCompletionResult(content.toString(), toolCalls, finishReason, prompt, completion, total);
        }
    }
    private List<ToolSpec.ToolCall> parseToolCallsFromMessage(JsonNode msg) {
        JsonNode arr = msg.path("tool_calls");
        if (!arr.isArray() || arr.size() == 0) return List.of();
        List<ToolSpec.ToolCall> out = new ArrayList<>();
        for (JsonNode node : arr) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = mapper.convertValue(node, Map.class);
                out.add(ToolSpec.ToolCall.fromMap(map));
            } catch (RuntimeException ex) {
                LOG.warn("skip malformed tool_call: {}", node);
            }
        }
        return out;
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        return node.asText("");
    }

    private static String readErrorBody(Response resp) {
        try (ResponseBody body = resp.body()) {
            if (body == null) return "(no body)";
            return body.string();
        } catch (IOException e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }
    /**
     * Stream-time tool_calls accumulator. SSE frames may split a single
     * tool call across multiple chunks; we merge by index.
     */
    private static final class StreamToolCallAccumulator {
        private final Map<Integer, StringBuilder> idByIndex = new HashMap<>();
        private final Map<Integer, StringBuilder> nameByIndex = new HashMap<>();
        private final Map<Integer, StringBuilder> argsByIndex = new HashMap<>();

        void accept(JsonNode arr) {
            if (!arr.isArray()) return;
            for (JsonNode node : arr) {
                int index = node.path("index").asInt(0);
                idByIndex.computeIfAbsent(index, k -> new StringBuilder())
                    .append(textOrEmpty(node.path("id")));
                JsonNode fn = node.path("function");
                if (!fn.isMissingNode() && !fn.isNull()) {
                    nameByIndex.computeIfAbsent(index, k -> new StringBuilder())
                        .append(textOrEmpty(fn.path("name")));
                    argsByIndex.computeIfAbsent(index, k -> new StringBuilder())
                        .append(textOrEmpty(fn.path("arguments")));
                }
            }
        }

        List<ToolSpec.ToolCall> finish() {
            if (idByIndex.isEmpty()) return List.of();
            List<ToolSpec.ToolCall> out = new ArrayList<>();
            List<Integer> sorted = new ArrayList<>(idByIndex.keySet());
            Collections.sort(sorted);
            for (Integer idx : sorted) {
                String id = idByIndex.get(idx).toString();
                if (id.isEmpty()) id = "tool_call_" + idx + "_" + System.nanoTime();
                String name = nameByIndex.getOrDefault(idx, new StringBuilder()).toString();
                String args = argsByIndex.getOrDefault(idx, new StringBuilder()).toString();
                out.add(new ToolSpec.ToolCall(id, ToolSpec.TYPE_FUNCTION,
                    new ToolSpec.FunctionCall(name, args)));
            }
            return out;
        }
    }
    /**
     * Builder for OpenAiCompatibleClient.
     */
    public static final class Builder {
        private String providerName = "glm";
        private String baseUrl = "https://open.bigmodel.cn/api/coding/paas/v4";
        private String model = "glm-5.1";
        private String apiKey;
        private int requestTimeoutSeconds = 60;

        public Builder providerName(String v) { this.providerName = v; return this; }
        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder requestTimeoutSeconds(int v) { this.requestTimeoutSeconds = v; return this; }

        public OpenAiCompatibleClient build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("apiKey is required");
            }
            return new OpenAiCompatibleClient(this);
        }
    }
}