package it.unimi.dsi.fastutil.ints;

public interface IntSet extends IntCollection {
    boolean add(int k);

    boolean addAll(IntCollection c);

    int size();
}
