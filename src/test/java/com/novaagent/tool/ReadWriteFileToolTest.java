package com.novaagent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadWriteFileToolTest {

    @Test
    void writeThenReadRoundTrip(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("src"));
        PathGuard guard = new PathGuard(tmp);
        WriteFileTool writer = new WriteFileTool(guard);
        ReadFileTool reader = new ReadFileTool(guard);

        String r1 = writer.invoke(Map.of(
            "path", "src/hi.txt",
            "content", "Hello, NovaAgent!"));
        assertTrue(r1.startsWith("OK"), "got: " + r1);

        String r2 = reader.invoke(Map.of("path", "src/hi.txt"));
        assertTrue(r2.contains("Hello, NovaAgent!"), "got: " + r2);
    }

    @Test
    void writeRejectsPathOutsideProject(@TempDir Path tmp) {
        PathGuard guard = new PathGuard(tmp);
        WriteFileTool writer = new WriteFileTool(guard);
        String r = writer.invoke(Map.of("path", "/tmp/outside.txt", "content", "no"));
        assertFalse(r.startsWith("OK"), "expected rejection, got: " + r);
    }

    @Test
    void globFindsCreatedFile(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("src"));
        Files.writeString(tmp.resolve("src/A.java"), "class A {}");
        Files.writeString(tmp.resolve("src/B.java"), "class B {}");
        PathGuard guard = new PathGuard(tmp);
        GlobFilesTool tool = new GlobFilesTool(guard);
        String r = tool.invoke(Map.of("pattern", "**/src/*.java"));
        assertTrue(r.contains("A.java"));
        assertTrue(r.contains("B.java"));
    }

    @Test
    void grepFindsSubstringInFile(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("src"));
        Files.writeString(tmp.resolve("src/A.java"),
            "class A {\n  // TODO: improve\n  int x;\n}\n");
        PathGuard guard = new PathGuard(tmp);
        GrepCodeTool tool = new GrepCodeTool(guard);
        String r = tool.invoke(Map.of("pattern", "TODO"));
        assertTrue(r.contains("A.java") && r.contains("TODO"), "got: " + r);
    }

    @Test
    void listDirReportsEntries(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("a.txt"), "a");
        Files.createDirectories(tmp.resolve("sub"));
        PathGuard guard = new PathGuard(tmp);
        ListDirTool tool = new ListDirTool(guard);
        String r = tool.invoke(Map.of());
        assertTrue(r.contains("a.txt"));
        assertTrue(r.contains("sub"));
    }

    @Test
    void unknownToolNameThrows() {
        ToolRegistry reg = new ToolRegistry();
        assertThrows(IllegalArgumentException.class, () -> reg.invoke("nope", Map.of()));
    }
}