package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class GrepCodeTool implements Tool {

    private static final int MAX_MATCHES = 100;
    private static final List<String> CODE_EXTS = List.of(
        ".java", ".kt", ".scala", ".py", ".go", ".rs",
        ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs",
        ".c", ".cpp", ".cc", ".h", ".hpp",
        ".rb", ".php", ".swift", ".m", ".mm",
        ".md", ".txt", ".json", ".yml", ".yaml", ".xml", ".html", ".css",
        ".sql", ".sh", ".bat", ".ps1");
    private static final List<String> SKIP_DIRS = List.of(
        "target", "node_modules", ".git", ".idea", ".vscode", "build", "dist", "out", ".novaagent");

    private final PathGuard guard;

    public GrepCodeTool(PathGuard guard) { this.guard = guard; }

    @Override
    public ToolSpec.FunctionSpec spec() {
        Map<String, Object> schema = PathGuard.schema(
            "Search code/text files inside the project root.",
            List.of(
                PathGuard.Param.required("pattern", "string", "Search pattern; treated as regex when is_regex=true"),
                PathGuard.Param.optional("path", "string", "Search root; defaults to project root"),
                PathGuard.Param.optional("is_regex", "boolean", "Use regex; defaults to false (case-insensitive substring)"),
                PathGuard.Param.optional("context", "integer", "Lines of context around matches; defaults to 1")
            ));
        return new ToolSpec.FunctionSpec("grep_code",
            "Search inside the project root with case-insensitive substring or regex.",
            schema);
    }

    @Override
    public String invoke(Map<String, Object> args) {
        Object patternObj = args.get("pattern");
        if (patternObj == null || patternObj.toString().isBlank()) return "missing required argument: pattern";
        PathGuard.Result dirRes;
        Object pathObj = args.get("path");
        if (pathObj == null || pathObj.toString().isBlank()) {
            dirRes = PathGuard.Result.ok(guard.projectRoot());
        } else {
            dirRes = guard.resolve(pathObj.toString(), PathGuard.Expect.EITHER);
        }
        if (!dirRes.ok()) return dirRes.error();
        return runInternal(args, patternObj.toString(), dirRes.path());
    }

    private String runInternal(Map<String, Object> args, String query, Path root) {
        boolean isRegex = Boolean.TRUE.equals(args.get("is_regex"));
        int context = args.get("context") == null ? 1 : Math.max(0, ((Number) args.get("context")).intValue());
        Pattern compiled;
        if (isRegex) {
            try { compiled = Pattern.compile(query, Pattern.MULTILINE); }
            catch (PatternSyntaxException ex) { return "regex error: " + ex.getMessage(); }
        } else {
            compiled = null;
        }

        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = new ArrayList<>();
            walk.forEach(p -> {
                if (Files.isDirectory(p)) return;
                String rel = root.relativize(p).toString();
                for (String skip : SKIP_DIRS) {
                    if (rel.contains(skip + "/") || rel.equals(skip)) return;
                }
                if (!hasCodeExt(p.getFileName().toString())) return;
                files.add(p);
            });
            files.sort(Comparator.comparing(Path::toString));

            StringBuilder out = new StringBuilder();
            int matches = 0;
            outer:
            for (Path file : files) {
                if (matches >= MAX_MATCHES) {
                    out.append("... [truncated to first ").append(MAX_MATCHES).append(" matches]\n");
                    break;
                }
                List<String> lines;
                try { lines = Files.readAllLines(file); }
                catch (IOException e) { continue; }
                for (int i = 0; i < lines.size(); i++) {
                    if (matches >= MAX_MATCHES) break outer;
                    String line = lines.get(i);
                    boolean hit;
                    if (isRegex) hit = compiled.matcher(line).find();
                    else hit = line.toLowerCase().contains(query.toLowerCase());
                    if (!hit) continue;
                    if (matches == 0) out.append("matches:\n");
                    matches++;
                    int start = Math.max(0, i - context);
                    int end = Math.min(lines.size() - 1, i + context);
                    out.append(root.relativize(file)).append(':').append(i + 1).append(":\n");
                    for (int j = start; j <= end; j++) {
                        out.append("  ").append(j + 1).append(" | ").append(lines.get(j)).append('\n');
                    }
                }
            }
            if (matches == 0) return "no matches for " + (isRegex ? "(regex) " : "(literal) ") + query;
            return out.toString();
        } catch (IOException e) {
            return "search failed: " + e.getMessage();
        }
    }

    private static boolean hasCodeExt(String name) {
        for (String ext : CODE_EXTS) if (name.endsWith(ext)) return true;
        return false;
    }
}