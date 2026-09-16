package com.rivalzin.bettersearch.tools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.rivalzin.bettersearch.core.CommandAliases;
import com.rivalzin.bettersearch.core.CommandFuzzy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

public class BetterSearchEnterTest {
    private static final int MAX_NAME_SWAPS = 4;

    private static int falhas;

    public static void main(String[] args) {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();

        dispatcher.register(literal("gamemode")
                .then(literal("survival")).then(literal("creative"))
                .then(literal("adventure")).then(literal("spectator")));
        dispatcher.register(literal("difficulty")
                .then(literal("peaceful")).then(literal("easy"))
                .then(literal("normal")).then(literal("hard")));

        dispatcher.register(literal("gamerule")
                .then(literal("advance_time").then(argument("v", bool())))
                .then(literal("keep_inventory").then(argument("v", bool())))
                .then(literal("mob_griefing").then(argument("v", bool())))
                .then(literal("random_tick_speed").then(argument("v", integer()))));
        dispatcher.register(literal("summon")
                .then(literal("zombified_piglin")).then(literal("zombie")));

        igual(dispatcher, "/gamemode 1", "/gamemode creative");
        igual(dispatcher, "/gamemode 0", "/gamemode survival");
        igual(dispatcher, "/gamemode 2", "/gamemode adventure");
        igual(dispatcher, "/gamemode 3", "/gamemode spectator");
        igual(dispatcher, "/difficulty 0", "/difficulty peaceful");
        igual(dispatcher, "/difficulty 3", "/difficulty hard");

        igual(dispatcher, "/gamerule doDaylightCycle true", "/gamerule advance_time true");
        igual(dispatcher, "/gamerule keepInventory false", "/gamerule keep_inventory false");
        igual(dispatcher, "/gamerule randomTickSpeed 10", "/gamerule random_tick_speed 10");
        igual(dispatcher, "/gamerule mobGriefing true", "/gamerule mob_griefing true");
        igual(dispatcher, "/summon zombie_pigman", "/summon zombified_piglin");

        igual(dispatcher, "/gamemode creative", "/gamemode creative");
        igual(dispatcher, "/gamerule advance_time true", "/gamerule advance_time true");
        igual(dispatcher, "/summon zombie", "/summon zombie");

        igual(dispatcher, "/gamerule doDaylightCicle true", "/gamerule doDaylightCicle true");
        igual(dispatcher, "/gamemode creativ", "/gamemode creativ");
        igual(dispatcher, "/gamemode 7", "/gamemode 7");
        igual(dispatcher, "/gamemode banana", "/gamemode banana");

        igual(dispatcher, "/", "/");
        igual(dispatcher, "//", "//");
        igual(dispatcher, "/gamemode ", "/gamemode ");
        igual(dispatcher, "/gamemode 1 1 1", "/gamemode 1 1 1");
        igual(dispatcher, "oi tudo bem", "oi tudo bem");
        igual(dispatcher, "/naoexiste 1", "/naoexiste 1");

        igual(dispatcher, "/gamerule 1", "/gamerule 1");
        igual(dispatcher, "/summon 1", "/summon 1");

        System.out.println(falhas == 0
                ? "OK - 26 linhas do Enter conferem."
                : "FALHOU - " + falhas + " linha(s) erradas.");
        if (falhas > 0) {
            System.exit(1);
        }
    }

    private static void igual(CommandDispatcher<Object> dispatcher, String entrada, String esperado) {
        String obtido = rewriteOnSend(dispatcher, entrada);
        if (!esperado.equals(obtido)) {
            falhas++;
            System.out.println("  FALHA  " + entrada + "  ->  " + obtido
                    + "   (esperado " + esperado + ")");
        }
    }

    private static String rewriteOnSend(CommandDispatcher<Object> dispatcher, String input) {
        if (input == null || input.length() < 2 || input.charAt(0) != '/') {
            return input;
        }
        Object source = new Object();
        String line = input;
        for (int pass = 0; pass < MAX_NAME_SWAPS; pass++) {
            ParseResults<Object> parse = parseLine(dispatcher, source, line);
            if (parse.getExceptions().isEmpty() && parse.getReader().getRemaining().trim().isEmpty()) {
                return line;
            }
            String swapped = swapOneName(dispatcher, source, parse, line);
            if (swapped == null) {
                return input;
            }
            line = swapped;
        }
        return input;
    }

    private static ParseResults<Object> parseLine(
            CommandDispatcher<Object> dispatcher,
            Object source, String line) {
        StringReader reader = new StringReader(line);
        if (reader.canRead() && reader.peek() == '/') {
            reader.skip();
        }
        return dispatcher.parse(reader, source);
    }

    private static String swapOneName(CommandDispatcher<Object> dispatcher,
                                      Object source,
                                      ParseResults<Object> parse, String line) {
        int[] span = wrongWord(line, line.length(), parse);
        if (span == null) {
            return null;
        }
        StringReader reader = new StringReader(line.substring(0, span[0]));
        if (reader.canRead() && reader.peek() == '/') {
            reader.skip();
        }

        Suggestions pool = dispatcher
                .getCompletionSuggestions(dispatcher.parse(reader, source), span[0])
                .getNow(null);
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        List<Suggestion> options = pool.getList();
        List<String> texts = new ArrayList<>(options.size());
        for (Suggestion option : options) {
            texts.add(option.getText());
        }
        List<String> named = CommandAliases.matches(line.substring(span[0], span[1]), texts);

        if (named.size() != 1) {
            return null;
        }
        return line.substring(0, span[0]) + named.get(0) + line.substring(span[1]);
    }

    private static int[] wrongWord(String input, int cursor, ParseResults<Object> parse) {
        int at = cursor;
        int stopped = -1;
        Map<CommandNode<Object>, CommandSyntaxException> errors = parse.getExceptions();
        if (errors != null && !errors.isEmpty()) {
            for (CommandSyntaxException error : errors.values()) {
                stopped = Math.max(stopped, error.getCursor());
            }
        } else if (!parse.getReader().getRemaining().isEmpty()) {

            stopped = parse.getReader().getCursor();
        }
        if (stopped >= 0) {
            at = Math.min(stopped, input.length());
        }
        int start = CommandFuzzy.wordStart(input, at);
        int end = Math.max(CommandFuzzy.wordEnd(input, at), Math.min(cursor, input.length()));
        if (end > input.length()) {
            end = input.length();
        }

        end = Math.min(end, CommandFuzzy.wordEnd(input, start));

        if (end <= start) {
            return null;
        }
        return new int[]{start, end};
    }
}
