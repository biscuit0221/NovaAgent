package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecuteCommandTool implements Tool {

    private static final int MAX_OUTPUT_BYTES = 8 * 1024;
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private final PathGuard guard;

    public ExecuteCommandTool(PathGuard guard) { this.guard = guard; }

    @Override
    public ToolSpec.FunctionSpec spec() {
        Map<String, Object> schema = PathGuard.schema(
            "Run a shell command inside the project root.",
            List.of(
                PathGuard.Param.required("command", "string", "Shell command string"),
                PathGuard.Param.optional("timeout_seconds", "integer", "Timeout; defaults to 60")
            ));
        return new ToolSpec.FunctionSpec("execute_command",
            "Run a shell command in the project root, returning stdout, exit code and timing.",
            schema);
    }

    @Override
    public String invoke(Map<String, Object> args) {
        Object cmdObj = args.get("command");
        if (cmdObj == null || cmdObj.toString().isBlank()) return "missing required argument: command";
        String command = cmdObj.toString();
        if (isBlacklisted(command)) return "command blocked by blacklist: " + command;

        Long timeoutSec = (args.get("timeout_seconds") instanceof Number n) ? n.longValue() : null;
        long timeout = (timeoutSec == null || timeoutSec <= 0) ? DEFAULT_TIMEOUT_SECONDS : timeoutSec;

        ProcessBuilder pb = shellCommand(command);
        pb.directory(guard.projectRoot().toFile());
        pb.redirectErrorStream(true);
        Process proc;
        try { proc = pb.start(); }
        catch (IOException e) { return "failed to start command: " + e.getMessage(); }

        StringBuilder collected = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = proc.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = br.read(buf)) > 0) {
                    synchronized (collected) {
                        if (collected.length() < MAX_OUTPUT_BYTES) {
                            int append = Math.min(n, MAX_OUTPUT_BYTES - collected.length());
                            collected.append(buf, 0, append);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }, "novaagent-cmd-reader");
        reader.setDaemon(true);
        reader.start();

        ScheduledExecutorService killer = Executors.newSingleThreadScheduledExecutor();
        try {
            boolean finished = proc.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                killer.schedule(() -> { if (proc.isAlive()) proc.destroyForcibly(); }, 1, TimeUnit.SECONDS);
                try { proc.waitFor(2, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                return collectResult(proc, collected, true);
            }
            reader.join(2_000);
            return collectResult(proc, collected, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            return "command interrupted: " + e.getMessage();
        } finally {
            killer.shutdownNow();
        }
    }

    private static ProcessBuilder shellCommand(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return new ProcessBuilder("cmd.exe", "/c", command);
        return new ProcessBuilder("bash", "-lc", command);
    }

    private static String collectResult(Process proc, StringBuilder collected, boolean timedOut) {
        StringBuilder out = new StringBuilder();
        synchronized (collected) { out.append(collected); }
        if (collected.length() >= MAX_OUTPUT_BYTES) {
            out.append("\n... [truncated to ").append(MAX_OUTPUT_BYTES).append(" bytes]\n");
        }
        if (timedOut) out.append("\n[timeout: command exceeded its timeout and was killed]\n");
        Integer exit = proc.exitValue();
        out.append("[exit code: ").append(exit == null ? "?" : exit).append("]\n");
        return out.toString();
    }

    public static boolean isBlacklisted(String command) {
        String lower = command.toLowerCase();
        if (lower.contains("rm -rf /") || lower.contains("rm -fr /")) return true;
        if (lower.contains("mkfs") || lower.contains("dd if=")) return true;
        if (lower.contains(":(){ :|:& };:")) return true;
        if (lower.contains("shutdown") || lower.contains("reboot")) return true;
        return false;
    }
}