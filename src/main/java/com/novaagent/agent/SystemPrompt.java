package com.novaagent.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * System prompt loader. Order: project override > user override > built-in default.
 */
public final class SystemPrompt {

    private SystemPrompt() {}

    public static String load(String projectRoot) {
        String projectOverride = readIfExists(projectRoot + "/.novaagent/prompts/phase-1/system.md");
        if (projectOverride != null) return projectOverride;
        String userOverride = readIfExists(System.getProperty("user.home") + "/.novaagent/prompts/phase-1/system.md");
        if (userOverride != null) return userOverride;
        return readDefault();
    }

    private static String readIfExists(String path) {
        try {
            Path p = Path.of(path);
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
        return null;
    }

    private static String readDefault() {
        try (InputStream in = SystemPrompt.class.getResourceAsStream("/prompts/phase-1/system.md")) {
            if (in == null) return fallback();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fallback();
        }
    }

    private static String fallback() {
        return "# NovaAgent · Phase 1\n\n"
            + "You are NovaAgent, a ReAct-style Java coding assistant CLI.\n"
            + "Answer in the user's language; keep code/comments in English unless asked.\n"
            + "Use the available tools when they help. Never invent tool output.\n";
    }
}