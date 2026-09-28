package com.rivalzin.bettersearch.client.ae2;

public interface Ae2PatternRecordAccess {
    String getSearchName();
    long getServerId();
    Iterable<?> bettersearch$inventory();
}
