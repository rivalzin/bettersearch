package com.rivalzin.bettersearch.forge.nei;

import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;
import net.minecraft.util.EnumChatFormatting;

public final class NeiSearchProvider implements SearchTokenParser.ISearchParserProvider {
    @Override
    public ItemFilter getFilter(String text) {
        return new LiveFilter(text);
    }

    @Override
    public char getPrefix() {
        // a prefix nobody types, so this parser runs on every search
        return '\u0001';
    }

    @Override
    public EnumChatFormatting getHighlightedColor() {
        return EnumChatFormatting.RESET;
    }

    @Override
    public SearchTokenParser.SearchMode getSearchMode() {
        return SearchTokenParser.SearchMode.ALWAYS;
    }
}
