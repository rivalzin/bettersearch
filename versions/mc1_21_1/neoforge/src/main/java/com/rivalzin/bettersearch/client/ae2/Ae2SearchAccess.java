package com.rivalzin.bettersearch.client.ae2;

public interface Ae2SearchAccess {
    void setSearchString(String text);
    void bettersearch$bind(Ae2StorageSearch search);
    void bettersearch$clearCache();
}
