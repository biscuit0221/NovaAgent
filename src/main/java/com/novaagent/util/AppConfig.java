package com.novaagent.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration loader. Priority (highest -> lowest):
 *   1. NOVAAGENT_* env vars
 *   2. ~/.novaagent/config.properties
 *   3. ./  .env  (project root; ignored if absent)
 *   4. classpath:/application.properties  (built-in defaults)
 */
public final class AppConfig {

    private final Properties props = new Properties();

    public AppConfig() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {}

        Path dotEnv = Paths.get(System.getProperty("user.dir")).resolve(".env");
        if (Files.exists(dotEnv)) {
            Properties env = new Properties();
            try (InputStream in = Files.newInputStream(dotEnv)) {
                env.load(in);
            } catch (IOException e) {
                System.err.println("[novaagent] WARN: failed to load " + dotEnv);
            }
            for (String key : env.stringPropertyNames()) {
                String value = env.getProperty(key).trim();
                String propKey = dotEnvKeyToProp(key);
                if (propKey != null) props.setProperty(propKey, value);
            }
        }

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

    private static String dotEnvKeyToProp(String envKey) {
        return switch (envKey) {
            case "NOVAAGENT_API_KEY"        -> "api.key";
            case "NOVAAGENT_BASE_URL"       -> "api.baseUrl";
            case "NOVAAGENT_MODEL"          -> "api.model";
            case "NOVAAGENT_WEBSEARCH_KEY"  -> "websearch.key";
            case "NOVAAGENT_WEBSEARCH_URL"  -> "websearch.url";
            default -> null;
        };
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
                "No LLM API key configured. Add NOVAAGENT_API_KEY=... to .env in the project root, "
              + "or set the NOVAAGENT_API_KEY environment variable, "
              + "or write api.key=... to ~/.novaagent/config.properties.");
        }
        return key;
    }

    public String baseUrl() { return get("api.baseUrl", "https://open.bigmodel.cn/api/coding/paas/v4"); }
    public String model() { return get("api.model", "glm-5.1"); }
    public String webSearchKey() { return get("websearch.key"); }
    public String webSearchUrl() { return get("websearch.url", "https://open.bigmodel.cn/api/paas/v4/web_search"); }
}