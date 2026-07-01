package com.novaagent.util;

/**
 * ASCII banner for "NovaAgent".
 */
public final class Banner {

    private Banner() {}

    private static final String[] LINES = {
        "███    ██  ██████  ██    ██  █████  ██████  ██████  ███████ ██   ██ ████████ ",
        " ██   ██  ██       ██    ██ ██   ██ ██   ██ ██   ██ ██       ██  ██     ██    ",
        "  ██ ██   █████   ██    ██ ███████ ██████  ██████  █████    ███       ██    ",
        "   ███    ██       ██    ██ ██   ██ ██      ██   ██ ██       ██  ██    ██    ",
        "    ██    ██████    ██████  ██   ██ ██      ██   ██ ███████ ██   ██   ██    ",
        "                                                                           "
    };

    public static void print(String model, String mode) {
        for (String line : LINES) System.out.println(Ansi.cyan(line));
        System.out.println();
        System.out.println(Ansi.brightCyan("              NovaAgent v0.1.0  -  Phase 1 (ReAct)"));
        System.out.println(Ansi.dim("              Model: " + model + "   Mode: " + mode));
        System.out.println();
    }
}