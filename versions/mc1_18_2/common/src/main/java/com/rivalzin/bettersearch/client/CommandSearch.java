package com.rivalzin.bettersearch.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.CommandAliases;
import com.rivalzin.bettersearch.core.CommandFuzzy;
import com.rivalzin.bettersearch.core.QuickMatcher;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CommandSearch {
    // one and two letter words are almost always right, do not "fix" them
    private static final int MIN_WORD_LENGTH = 2;

    private static volatile boolean correctionOffered;

    private CommandSearch() {
    }

    public static boolean isEnabled() {
        SearchSettings settings = BetterSearchClient.settings();
        return BetterSearchClient.isEnabled()
                // only the tab list, the server never gets asked
                && (settings.searchPlayerNames || settings.searchCommandItems
                    || settings.fixCommandErrors || settings.fixVersionNames);
    }

    // red = nothing found, gold = we have a spelling for it
    private static final Style STUCK = Style.EMPTY.withColor(ChatFormatting.RED);

    private static final Style FIXABLE = Style.EMPTY.withColor(ChatFormatting.GOLD);

    public static Style unparsedStyle() {
        SearchSettings settings = BetterSearchClient.settings();
        return correctionOffered
                && BetterSearchClient.isEnabled()
                && (settings.fixCommandErrors || settings.fixVersionNames)
                ? FIXABLE : STUCK;
    }

    public static Suggestions augmentCommand(ParseResults<SharedSuggestionProvider> parse,
                                             int cursor, Suggestions original) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!isEnabled() || parse == null || original == null) {
                return original;
            }
            String input = parse.getReader().getString();
            int safeCursor = Math.max(0, Math.min(cursor, input.length()));

            SuggestionContext<SharedSuggestionProvider> context =
                    parse.getContext().findSuggestionContext(safeCursor);
            int start = Math.max(0, Math.min(context.startPos, safeCursor));
            int partyStart = partyTargetStart(input, safeCursor);
            boolean partyTarget = partyStart >= 0;
            if (partyTarget) {
                start = Math.max(start, partyStart);
            }
            String word = input.substring(start, safeCursor);
            if (word.length() < MIN_WORD_LENGTH) {
                return original;
            }

            boolean wantsItems = false;
            boolean wantsBlocks = false;
            boolean wantsPlayers = false;
            for (CommandNode<SharedSuggestionProvider> child : context.parent.getChildren()) {
                if (child instanceof ArgumentCommandNode<?, ?> argument) {
                    ArgumentType<?> type = argument.getType();
                    if (isItemLike(type)) {
                        wantsItems = true;
                    } else if (isBlockLike(type)) {
                        wantsBlocks = true;
                    } else if (isPlayerLike(type)) {
                        wantsPlayers = true;
                    }
                }
            }

            List<String> additions = new ArrayList<>();
            if ((wantsItems || wantsBlocks) && settings.searchCommandItems) {
                List<ResourceLocation> ids = wantsItems
                        ? CommandItemIndex.search(word)
                        : CommandItemIndex.searchBlocks(word);
                if (ids != null) {
                    for (ResourceLocation id : ids) {
                        additions.add(id.toString());
                    }
                }
            }
            if ((wantsPlayers || partyTarget) && settings.searchPlayerNames) {
                additions.addAll(matchNames(onlinePlayerNames(), word, settings));
            }
            return merge(original, additions, start, safeCursor, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] command suggestions unchanged: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return original;
        }
    }

    public static CompletableFuture<Suggestions> augmentCommandAsync(
            ParseResults<SharedSuggestionProvider> parse, int cursor, Suggestions original) {
        try {
            Suggestions merged = augmentCommand(parse, cursor, original);
            SearchSettings settings = BetterSearchClient.settings();
            if (merged == null || !merged.isEmpty() || parse == null
                    || !BetterSearchClient.isEnabled()
                    || (!settings.fixCommandErrors && !settings.fixVersionNames)) {
                correctionOffered = false;
                return CompletableFuture.completedFuture(merged == null ? original : merged);
            }
            return correct(parse, cursor, merged);
        } catch (Throwable t) {
            correctionOffered = false;
            BetterSearch.LOGGER.debug("[{}] command fix skipped: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return CompletableFuture.completedFuture(original);
        }
    }

    private static CompletableFuture<Suggestions> correct(ParseResults<SharedSuggestionProvider> parse,
                                                          int cursor, Suggestions original) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            correctionOffered = false;
            return CompletableFuture.completedFuture(original);
        }
        String input = parse.getReader().getString();
        int safeCursor = Math.max(0, Math.min(cursor, input.length()));

        int[] span = wrongWord(input, safeCursor, parse);
        if (span == null) {
            correctionOffered = false;
            return CompletableFuture.completedFuture(original);
        }
        final int start = span[0];
        final int end = span[1];
        final String word = input.substring(start, end);

        CommandDispatcher<SharedSuggestionProvider> dispatcher = minecraft.player.connection.getCommands();
        StringReader reader = new StringReader(input.substring(0, start));
        if (reader.canRead() && reader.peek() == '/') {
            reader.skip();
        }
        ParseResults<SharedSuggestionProvider> stub =
                dispatcher.parse(reader, minecraft.player.connection.getSuggestionsProvider());

        return dispatcher.getCompletionSuggestions(stub, start)
                .thenApply(pool -> build(word, start, end, pool, original))
                .exceptionally(t -> {
                    correctionOffered = false;
                    return original;
                });
    }

    // a line rarely holds more than one renamed name, and this stops the loop from ever spinning
    private static final int MAX_NAME_SWAPS = 4;

    /**
     * The line the player is about to send, with the names this version renamed swapped for the
     * ones it takes. Only a whole name is swapped and only when the swap makes the line parse,
     * so a typo still reaches the game and is refused: that guess stays a suggestion.
     */
    public static String rewriteOnSend(String input) {
        try {
            if (input == null || input.length() < 2 || input.charAt(0) != '/'
                    || !BetterSearchClient.isEnabled()
                    || !BetterSearchClient.settings().fixVersionNames) {
                return input;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return input;
            }
            CommandDispatcher<SharedSuggestionProvider> dispatcher = minecraft.player.connection.getCommands();
            SharedSuggestionProvider source = minecraft.player.connection.getSuggestionsProvider();
            String line = input;
            for (int pass = 0; pass < MAX_NAME_SWAPS; pass++) {
                ParseResults<SharedSuggestionProvider> parse = parseLine(dispatcher, source, line);
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
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] line sent as typed: {}", BetterSearch.MOD_NAME, t.toString());
            return input;
        }
    }

    private static ParseResults<SharedSuggestionProvider> parseLine(
            CommandDispatcher<SharedSuggestionProvider> dispatcher,
            SharedSuggestionProvider source, String line) {
        StringReader reader = new StringReader(line);
        if (reader.canRead() && reader.peek() == '/') {
            reader.skip();
        }
        return dispatcher.parse(reader, source);
    }

    private static String swapOneName(CommandDispatcher<SharedSuggestionProvider> dispatcher,
                                      SharedSuggestionProvider source,
                                      ParseResults<SharedSuggestionProvider> parse, String line) {
        int[] span = wrongWord(line, line.length(), parse);
        if (span == null) {
            return null;
        }
        StringReader reader = new StringReader(line.substring(0, span[0]));
        if (reader.canRead() && reader.peek() == '/') {
            reader.skip();
        }
        // getNow: the client tree answers without waiting, and a line is not worth a stall
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
        // two answers is not a choice the mod gets to make for the player
        if (named.size() != 1) {
            return null;
        }
        return line.substring(0, span[0]) + named.get(0) + line.substring(span[1]);
    }

    private static Suggestions build(String word, int start, int end,
                                     Suggestions pool, Suggestions original) {
        if (pool == null || pool.isEmpty()) {
            correctionOffered = false;
            return original;
        }
        List<Suggestion> options = pool.getList();
        List<String> texts = new ArrayList<>(options.size());
        for (Suggestion option : options) {
            texts.add(option.getText());
        }
        SearchSettings settings = BetterSearchClient.settings();
        int limit = settings.commandSuggestionLimit;
        List<String> chosen = new ArrayList<>();
        if (settings.fixVersionNames) {
            // a name this version renamed comes first: /gamemode 1 here means creative, and
            // whoever typed zombie_pigman wants this version's zombified_piglin
            chosen.addAll(CommandAliases.matches(word, texts));
            for (String near : CommandAliases.starting(word, texts)) {
                if (!chosen.contains(near)) {
                    chosen.add(near);
                }
            }
        }
        if (settings.fixCommandErrors) {
            for (String guess : CommandFuzzy.best(word, texts, limit)) {
                if (!chosen.contains(guess)) {
                    chosen.add(guess);
                }
            }
        }
        if (chosen.size() > limit) {
            chosen = chosen.subList(0, limit);
        }
        if (chosen.isEmpty()) {
            correctionOffered = false;
            return original;
        }
        StringRange range = StringRange.between(start, end);
        List<Suggestion> corrected = new ArrayList<>(chosen.size());
        for (String text : chosen) {
            corrected.add(new Suggestion(range, text));
        }
        correctionOffered = true;
        return new Suggestions(range, corrected);
    }

    private static int[] wrongWord(String input, int cursor, ParseResults<SharedSuggestionProvider> parse) {
        int at = cursor;
        int stopped = -1;
        Map<CommandNode<SharedSuggestionProvider>, CommandSyntaxException> errors = parse.getExceptions();
        if (errors != null && !errors.isEmpty()) {
            for (CommandSyntaxException error : errors.values()) {
                stopped = Math.max(stopped, error.getCursor());
            }
        } else if (!parse.getReader().getRemaining().isEmpty()) {
            // a literal that simply does not match throws nothing: brigadier stops the reader
            // on that word, and the end of the line is not where that word is
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
        // one letter is too little to guess from, and CommandFuzzy refuses it on its own;
        // the alias table matches whole words, so /gamemode 1 has to get past here
        if (end <= start) {
            return null;
        }
        return new int[]{start, end};
    }

    public static Suggestions augmentChat(String input, int cursor, Suggestions original) {
        correctionOffered = false;
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchPlayerNames || original == null) {
                return original;
            }
            int safeCursor = Math.max(0, Math.min(cursor, input.length()));
            int start = lastWordIndex(input, safeCursor);
            String word = input.substring(start, safeCursor);
            if (word.length() < MIN_WORD_LENGTH) {
                return original;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null) {
                return original;
            }

            Collection<String> pool = minecraft.player.connection.getSuggestionsProvider().getOnlinePlayerNames();
            return merge(original, matchNames(pool, word, settings), start, safeCursor, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] chat suggestions unchanged: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return original;
        }
    }

    private static boolean isItemLike(ArgumentType<?> type) {
        return type instanceof ItemArgument
                || type instanceof ItemPredicateArgument;
    }

    private static boolean isBlockLike(ArgumentType<?> type) {
        return type instanceof BlockStateArgument
                || type instanceof BlockPredicateArgument;
    }

    private static boolean isPlayerLike(ArgumentType<?> type) {
        return type instanceof EntityArgument
                || type instanceof GameProfileArgument
                || type instanceof ScoreHolderArgument;
    }

    private static final Set<String> PARTY_COMMANDS =
            Set.of("invite", "group", "grupo", "party");

    private static int partyTargetStart(String input, int cursor) {
        int begin = input.startsWith("/") ? 1 : 0;
        int space = input.indexOf(' ', begin);
        if (space < 0 || space >= cursor) {
            return -1;
        }
        if (!PARTY_COMMANDS.contains(input.substring(begin, space).toLowerCase(Locale.ROOT))) {
            return -1;
        }
        return input.lastIndexOf(' ', cursor - 1) + 1;
    }

    private static Collection<String> onlinePlayerNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return List.of();
        }
        return minecraft.player.connection.getSuggestionsProvider().getOnlinePlayerNames();
    }

    private static List<String> matchNames(Collection<String> pool, String word, SearchSettings settings) {
        QuickMatcher.Session session = new QuickMatcher.Session(word, settings);
        List<String> matched = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (String candidate : pool) {
            int score = session.score(candidate);
            if (score != QuickMatcher.NO_MATCH) {
                int at = 0;
                while (at < scores.size() && scores.get(at) >= score) {
                    at++;
                }
                matched.add(at, candidate);
                scores.add(at, score);
            }
        }
        return matched;
    }

    private static Suggestions merge(Suggestions original, List<String> additions,
                                     int start, int cursor, SearchSettings settings) {
        if (additions.isEmpty()) {
            return original;
        }
        List<Suggestion> existing = original.getList();
        Set<String> seen = new HashSet<>(existing.size() + additions.size());
        for (Suggestion suggestion : existing) {
            seen.add(suggestion.getText());
        }

        StringRange range = StringRange.between(start, cursor);
        // ours matched input[start, cursor): a different span would replace the wrong text
        if (!existing.isEmpty() && !range.equals(original.getRange())) {
            return original;
        }
        List<Suggestion> merged = new ArrayList<>(existing);
        int added = 0;
        for (String text : additions) {
            if (added >= settings.commandSuggestionLimit) {
                break;
            }
            if (seen.add(text)) {
                merged.add(new Suggestion(range, text));
                added++;
            }
        }
        return added == 0 ? original : new Suggestions(range, merged);
    }

    private static int lastWordIndex(String text, int cursor) {
        int index = cursor;
        while (index > 0 && !Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }
}
