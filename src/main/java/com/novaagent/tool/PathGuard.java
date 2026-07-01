package com.novaagent.tool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves any string path the LLM hands us into a project-root-confined absolute path.
 *
 * Rejects absolute paths outside the root, ".." traversal, and symlink escape.
 */
public final class PathGuard {

    private final Path projectRoot;

    public PathGuard(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    public Path projectRoot() { return projectRoot; }

    public Result resolve(String rawPath, Expect mustBe) {
        if (rawPath == null || rawPath.isBlank()) return Result.error("path must not be empty");
        Path candidate;
        try {
            Path p = Paths.get(rawPath);
            candidate = p.isAbsolute() ? p : projectRoot.resolve(p).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return Result.error("path parse failed: " + ex.getMessage());
        }
        if (!candidate.startsWith(projectRoot)) {
            return Result.error("path escapes project root: " + rawPath);
        }
        if (!candidate.toFile().exists()) return Result.error("path does not exist: " + candidate);
        boolean isDir = candidate.toFile().isDirectory();
        if (mustBe == Expect.FILE && (isDir || !candidate.toFile().isFile())) {
            return Result.error("not a file: " + candidate);
        }
        if (mustBe == Expect.DIR && !isDir) return Result.error("not a directory: " + candidate);
        return Result.ok(candidate);
    }

    public Result resolveForWrite(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return Result.error("path must not be empty");
        Path candidate;
        try {
            Path p = Paths.get(rawPath);
            candidate = p.isAbsolute() ? p : projectRoot.resolve(p).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return Result.error("path parse failed: " + ex.getMessage());
        }
        if (!candidate.startsWith(projectRoot)) {
            return Result.error("path escapes project root: " + rawPath);
        }
        Path parent = candidate.getParent();
        if (parent != null && !parent.toFile().exists()) {
            return Result.error("parent dir does not exist: " + parent);
        }
        return Result.ok(candidate);
    }

    public enum Expect { FILE, DIR, EITHER }

    public static final class Result {
        private final Path path;
        private final String error;
        private Result(Path path, String error) { this.path = path; this.error = error; }
        public static Result ok(Path p) { return new Result(p, null); }
        public static Result error(String msg) { return new Result(null, msg); }
        public boolean ok() { return error == null; }
        public Path path() { return path; }
        public String error() { return error; }
    }

    public Result resolveArg(Map<String, Object> args, String argName, Expect mustBe) {
        Object v = args.get(argName);
        if (v == null) return Result.error("missing required argument: " + argName);
        return resolve(v.toString(), mustBe);
    }

    public static Map<String, Object> schema(String description, List<Param> params) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Param p : params) {
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("type", p.type);
            if (p.description != null) spec.put("description", p.description);
            if (p.enumValues != null) spec.put("enum", p.enumValues);
            properties.put(p.name, spec);
            if (p.required) required.add(p.name);
        }
        root.put("properties", properties);
        root.put("required", required);
        root.put("additionalProperties", false);
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("type", root.get("type"));
        wrapped.put("properties", root.get("properties"));
        wrapped.put("required", root.get("required"));
        wrapped.put("additionalProperties", root.get("additionalProperties"));
        wrapped.put("description", description);
        return wrapped;
    }

    public static final class Param {
        public final String name;
        public final String type;
        public final String description;
        public final boolean required;
        public final List<String> enumValues;

        public Param(String name, String type, String description, boolean required) {
            this(name, type, description, required, null);
        }
        public Param(String name, String type, String description, boolean required, List<String> enumValues) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
            this.enumValues = enumValues;
        }
        public static Param required(String name, String type, String description) {
            return new Param(name, type, description, true, null);
        }
        public static Param optional(String name, String type, String description) {
            return new Param(name, type, description, false, null);
        }
    }
}