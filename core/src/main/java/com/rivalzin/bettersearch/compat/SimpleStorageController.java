package com.rivalzin.bettersearch.compat;

import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.StorageInventory;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class SimpleStorageController {
    public interface Platform {
        Object screen();
        SearchSettings settings();
        long languageRevision(SearchSettings settings);
        Object key(Object stack);
        Object snapshot(Object key);
        Supplier<SearchIndex<Object>> prepare(List<Object> items, SearchSettings settings);
        Executor client();
        Executor worker();
        void report(Throwable error);
    }

    private final Platform platform;
    private final StorageInventory<Object> inventory = new StorageInventory<>();
    private StorageSearchSession<Object> session;
    private Object owner;
    private Object widget;
    private Object source;
    private int size = -1;
    private boolean dirty = true;
    private Predicate<Object> matcher;

    public SimpleStorageController(Platform platform) {
        this.platform = Objects.requireNonNull(platform);
    }

    public void begin(Object widget, List<?> stacks, String query) {
        matcher = null;
        Object screen = platform.screen();
        if (owner != screen || this.widget != widget) {
            close();
        }
        if (screen == null || widget == null || stacks == null) {
            return;
        }
        try {
            dirty |= source != stacks || size != stacks.size();
            source = stacks;
            size = stacks.size();
            if (!StorageSearchSession.isPlainTerm(query)) {
                return;
            }
            SearchSettings settings = platform.settings();
            settings.enabled &= settings.searchSimpleStorageNetwork;
            if (!settings.enabled) {
                close();
                return;
            }
            if (session == null) {
                owner = screen;
                this.widget = widget;
                session = new StorageSearchSession<>(platform.client(), platform.worker(), platform::report);
            }
            long languageRevision = platform.languageRevision(settings);
            if (dirty) {
                inventory.update(stacks, platform::key, platform::snapshot);
                dirty = false;
            }
            List<Object> values = inventory.values();
            session.begin(values, values.size(), 0, languageRevision, query, settings,
                    () -> platform.prepare(values, settings), null);
            matcher = session.matcher(query);
        } catch (RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            matcher = null;
            platform.report(error);
        }
    }

    public boolean matches(boolean nativeMatch, Object widget, Object stack) {
        return nativeMatch || this.widget == widget && owner == platform.screen()
                && matcher != null && matcher.test(inventory.key(stack));
    }

    public void changed(Object widget) {
        if (this.widget == widget) {
            dirty = true;
        }
    }

    public void switchScreen(Object nextScreen) {
        if (owner != nextScreen) {
            close();
        }
    }

    public void closeInactive() {
        switchScreen(platform.screen());
    }

    public void close() {
        if (session != null) {
            session.close();
            session = null;
        }
        inventory.clear();
        owner = null;
        widget = null;
        source = null;
        size = -1;
        dirty = true;
        matcher = null;
    }
}
