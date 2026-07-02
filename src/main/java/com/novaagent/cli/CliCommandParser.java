package com.novaagent.cli;

import java.util.Locale;

public final class CliCommandParser {

    public enum Command {
        HELP("help"),
        TOOLS("tools"),
        CLEAR("clear"),
        CONFIG("config"),
        MODEL("model"),
        PLAN("plan"),
        EXIT("exit"),
        QUIT("quit"),
        UNKNOWN("unknown");

        public final String name;
        Command(String name) { this.name = name; }
    }

    public static final class Parsed {
        public final Command command;
        public final String arg;
        public final String rawInput;

        private Parsed(Command c, String arg, String rawInput) {
            this.command = c;
            this.arg = arg;
            this.rawInput = rawInput;
        }
    }

    private CliCommandParser() {}

    public static Parsed parse(String line) {
        if (line == null) return new Parsed(Command.UNKNOWN, "", "");
        String trimmed = line.trim();
        if (!trimmed.startsWith("/")) return new Parsed(Command.UNKNOWN, trimmed, line);
        String body = trimmed.substring(1);
        String[] parts = body.split("\\s+", 2);
        String head = parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length == 2 ? parts[1].trim() : "";
        Command cmd;
        try { cmd = Command.valueOf(head.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { cmd = Command.UNKNOWN; }
        return new Parsed(cmd, rest, line);
    }
}