package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class GlobFilesTool implements Tool {

    private static final int MAX_RESULTS = 200;
    private static final List<String> SKIP_DIRS = List.of(
        "target", "node_modules", ".git", ".idea", ".vscode", "build", "dist", "out");

    private final PathGuard guard;

    public GlobFilesTool(PathGuard guard) { this.guard = guard; }

    @Override
    public ToolSpec.FunctionSpec spec() {
        Map<String, Object> schema = PathGuard.schema(
            "Match file paths inside the project root using a glob pattern.",
            List.of(
                PathGuard.Param.required("pattern", "string", "Glob pattern, e.g. src/**/*.java"),
                PathGuard.Param.optional("path", "string", "Search root; defaults to project root")
            ));
        return new ToolSpec.FunctionSpec("glob_files",
            "Glob-match files inside the project root.",
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
            dirRes = guard.resolve(pathObj.toString(), PathGuard.Expect.DIR);
        }
        if (!dirRes.ok()) return dirRes.error();
        String pattern = patternObj.toString();
        if (!pattern.contains("*") && !pattern.contains("?")) {
            PathGuard.Result fileRes = guard.resolve(pattern, PathGuard.Expect.EITHER);
            if (fileRes.ok()) return fileRes.path().toString();
            return "no match for: " + pattern;
        }
        PathMatcher matcher;
        try { matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern); }
        catch (IllegalArgumentException ex) { return "invalid glob: " + ex.getMessage(); }
        Path root = dirRes.path();
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> matched = new ArrayList<>();
            walk.forEach(p -> {
                if (!matcher.matches(p)) return;
                Path relative = root.relativize(p);
                for (String skip : SKIP_DIRS) {
                    if (relative.toString().contains(skip + "/") || relative.toString().equals(skip)) return;
                }
                matched.add(p);
            });
            matched.sort(Comparator.comparing(Path::toString));
            StringBuilder out = new StringBuilder();
            out.append("matched ").append(matched.size()).append(" entries:\n");
            int cap = Math.min(matched.size(), MAX_RESULTS);
            for (int i = 0; i < cap; i++) out.append("  ").append(root.relativize(matched.get(i))).append('\n');
            if (matched.size() > MAX_RESULTS) out.append("... [truncated to first ").append(MAX_RESULTS).append("]\n");
            return out.toString();
        } catch (IOException e) {
            return "glob failed: " + e.getMessage();
        }
    }
}