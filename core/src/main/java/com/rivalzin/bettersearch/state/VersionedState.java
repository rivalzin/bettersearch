package com.rivalzin.bettersearch.state;

public final class VersionedState<T> {
    private volatile T value;
    private long revision;
    private boolean pending;

    public VersionedState(T initial) {
        value = initial;
    }

    public T value() {
        return value;
    }

    public synchronized long begin() {
        pending = true;
        return ++revision;
    }

    public synchronized void invalidate() {
        revision++;
        pending = false;
    }

    public synchronized void reset(T replacement) {
        invalidate();
        value = replacement;
    }

    public synchronized boolean publish(long expectedRevision, T replacement) {
        if (expectedRevision != revision || !pending) {
            return false;
        }
        value = replacement;
        pending = false;
        return true;
    }

    public synchronized boolean fail(long expectedRevision) {
        if (expectedRevision != revision || !pending) {
            return false;
        }
        pending = false;
        return true;
    }

    public synchronized boolean pending() {
        return pending;
    }
}
