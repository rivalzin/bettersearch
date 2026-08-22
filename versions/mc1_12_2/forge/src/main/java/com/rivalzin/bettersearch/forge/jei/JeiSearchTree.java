package com.rivalzin.bettersearch.forge.jei;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.suffixtree.CombinedSearchTrees;
import mezz.jei.suffixtree.ISearchTree;

final class JeiSearchTree extends CombinedSearchTrees {
    private final CombinedSearchTrees original;
    private final IngredientFilter filter;

    JeiSearchTree(CombinedSearchTrees original, IngredientFilter filter) {
        this.original = original;
        this.filter = filter;
    }

    @Override
    public IntSet search(String word) {
        IntSet theirs = original.search(word);
        int[] ours = JeiSearchBridge.search(word, filter);
        if (ours == null || ours.length == 0) {
            return theirs;
        }
        IntSet union = new IntOpenHashSet(ours.length + (theirs == null ? 0 : theirs.size()));
        if (theirs != null) {
            union.addAll(theirs);
        }
        for (int index : ours) {
            union.add(index);
        }
        return union;
    }

    @Override
    public void addSearchTree(ISearchTree searchTree) {
        original.addSearchTree(searchTree);
    }
}
