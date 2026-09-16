package com.rivalzin.bettersearch.forge.nei;

import codechicken.nei.SearchField;
import codechicken.nei.api.ItemFilter;

public final class NeiSearchProviderLegacy implements SearchField.ISearchProvider {
    @Override

    public boolean isPrimary() {
        return false;
    }

    @Override
    public ItemFilter getFilter(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return new LiveFilter(text);
    }
}
