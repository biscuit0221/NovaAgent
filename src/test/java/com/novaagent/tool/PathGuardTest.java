package com.novaagent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathGuardTest {

    @Test
    void resolveRelativeInsideProject(@TempDir Path tmp) throws IOException {
        Path root = tmp.toAbsolutePath();
        Files.writeString(root.resolve("hello.txt"), "hi");
        PathGuard guard = new PathGuard(root);
        var res = guard.resolve("hello.txt", PathGuard.Expect.FILE);
        assertTrue(res.ok(), res.error());
        assertEquals(root.resolve("hello.txt"), res.path());
    }

    @Test
    void resolveRejectsDotDotTraversal(@TempDir Path tmp) throws IOException {
        Path root = tmp.toAbsolutePath();
        Files.writeString(root.resolve("a.txt"), "hi");
        PathGuard guard = new PathGuard(root);
        var res = guard.resolve("../escape.txt", PathGuard.Expect.EITHER);
        assertFalse(res.ok(), "expected resolution to fail");
    }

    @Test
    void toolRegistryDispatchesAndRejectsUnknown() {
        ToolRegistry reg = new ToolRegistry();
        reg.register(new ReadFileTool(new PathGuard(Path.of("."))));
        assertTrue(reg.contains("read_file"));
        assertFalse(reg.contains("nope"));
        assertThrows(IllegalArgumentException.class, () -> reg.invoke("nope", Map.of()));
    }

    @Test
    void schemaBuilderProducesJsonShape(@TempDir Path tmp) {
        Map<String, Object> schema = PathGuard.schema("desc",
            List.of(PathGuard.Param.required("path", "string", "p"),
                    PathGuard.Param.optional("n", "integer", "n")));
        assertEquals("object", schema.get("type"));
        Object props = schema.get("properties");
        assertNotNull(props);
        assertTrue(props instanceof Map);
    }
}