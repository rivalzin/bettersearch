package com.rivalzin.bettersearch.async;

import java.util.function.Supplier;

public interface StagedSupplier<V> extends Supplier<V> {
    boolean advance();
}
