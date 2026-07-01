package com.novaagent.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration loader: classpath defaults -> ~/.novaagent/config.properties -> env vars.
 */
public final class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {}

        Path userCfg = Path.of(System.getProperty("user.home"), ".novaagent", "config.properties");
        if (Files.exists(userCfg)) {
            try (InputStream in = Files.newInputStream(userCfg)) {
                Properties user = new Properties();
                user.load(in);
                props.putAll(user);
            } catch (IOException e) {
                System.err.println("[novaagent] WARN: failed to load " + userCfg);
            }
        }

        overlayFromEnv("NOVAAGENT_API_KEY", "api.key");
        overlayFromEnv("NOVAAGENT_BASE_URL", "api.baseUrl");
        overlayFromEnv("NOVAAGENT_MODEL", "api.model");
        overlayFromEnv("NOVAAGENT_WEBSEARCH_KEY", "websearch.key");
        overlayFromEnv("NOVAAGENT_WEBSEARCH_URL", "websearch.url");
    }

    private void overlayFromEnv(String envName, String propKey) {
        String value = System.getenv(envName);
        if (value != null && !value.isBlank()) props.setProperty(propKey, value);
    }

    public String get(String key) {
        return props.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    public String requireApiKey() {
        String key = get("api.key");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                "No LLM API key configured. Set NOVAAGENT_API_KEY env var or write api.key=... to ~/.novaagent/config.properties.");
        }
        return key;
    }

    public String baseUrl() { return get("api.baseUrl", "https://open.bigmodel.cn/api/coding/paas/v4"); }
    public String model() { return get("api.model", "glm-5.1"); }
    public String webSearchKey() { return get("websearch.key"); }
    public String webSearchUrl() { return get("websearch.url", "https://open.bigmodel.cn/api/paas/v4/web_search"); }
}