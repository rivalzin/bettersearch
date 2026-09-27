package com.rivalzin.bettersearch.forge.jei;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.suffixtree.CombinedSearchTrees;
import mezz.jei.suffixtree.ISearchTree;

final class JeiSearchTree extends CombinedSearchTrees {
    private final CombinedSearchTrees original;
    private final JeiSearchBridge bridge;

    JeiSearchTree(CombinedSearchTrees original, JeiSearchBridge.Elements elements) {
        this.original = original;
        this.bridge = new JeiSearchBridge(elements);
    }

    @Override
    public IntSet search(String word) {
        IntSet theirs = original.search(word);
        int[] ours = bridge.search(word);
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
