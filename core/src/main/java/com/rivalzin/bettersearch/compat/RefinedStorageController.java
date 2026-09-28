package com.rivalzin.bettersearch.compat;

import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StorageInventory;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class RefinedStorageController {
    public interface Platform {
        Object screen();
        SearchSettings settings();
        long languageRevision(SearchSettings settings);
        String query(Object screen);
        void fill(EntrySnapshot<Object> entry, Object stack, SearchSettings settings);
        Executor client();
        Executor worker();
        void report(Throwable error);
    }

    private static final ClassValue<Access> ACCESS = new ClassValue<Access>() {
        @Override
        protected Access computeValue(Class<?> type) {
            return new Access(type);
        }
    };
    private final Platform platform;
    private final Map<Object, Session> sessions = new IdentityHashMap<>();
    private final ThreadLocal<Session> context = new ThreadLocal<>();
    private Object owner;

    public RefinedStorageController(Platform platform) {
        this.platform = platform;
    }

    public void begin(Object view) {
        try {
            changeScreen();
            if (owner == null || view == null) {
                return;
            }
            Object screen = field(view, "screen");
            if (screen == null) {
                screen = field(view, "gui");
            }
            if (screen != owner) {
                return;
            }
            Session session = sessions.get(view);
            if (session == null) {
                session = new Session(view);
                sessions.put(view, session);
            }
            session.begin();
        } catch (RuntimeException | LinkageError error) {
            report(error);
        }
    }

    public <T> Predicate<T> bind(Object view, Predicate<T> original) {
        begin(view);
        return value -> test(original, value, view);
    }

    public <T> boolean test(Predicate<T> original, T value, Object view) {
        Session previous = context.get();
        Session session = sessions.get(view);
        if (session != null && owner == platform.screen() && !session.closed) {
            context.set(session);
        } else {
            context.remove();
        }
        try {
            return original.test(value);
        } finally {
            if (previous == null) {
                context.remove();
            } else {
                context.set(previous);
            }
        }
    }

    public boolean matches(boolean original, String term, Object value) {
        if (original) {
            return true;
        }
        try {
            Session session = context.get();
            return session != null && !session.closed && StorageSearchSession.isPlainTerm(term)
                    && session.terms.computeIfAbsent(term, session.search::matcher)
                    .test(session.inventory.key(value));
        } catch (RuntimeException | LinkageError error) {
            report(error);
            return false;
        }
    }

    public void changed(Object view) {
        Session session = sessions.get(view);
        if (session != null) {
            session.dirty = true;
        }
    }

    public void delta(Object view) {
        Session session = sessions.get(view);
        Object map = field(view, "map");
        if (session != null && map instanceof Map<?, ?>
                && ((Map<?, ?>) map).size() != session.inventory.values().size()) {
            session.dirty = true;
        }
    }

    public void tick() {
        changeScreen();
        for (Session session : new ArrayList<>(sessions.values())) {
            try {
                SearchSettings settings = platform.settings();
                long language = platform.languageRevision(settings);
                if (session.ready || session.dirty || !settings.equals(session.previousSettings)
                        || language != session.previousLanguage) {
                    call(session.view, "sort");
                }
            } catch (RuntimeException | LinkageError error) {
                report(error);
            }
        }
    }

    public void closeScreen(Object screen) {
        if (owner == screen) {
            close();
        }
    }

    public void close() {
        for (Session session : sessions.values()) {
            session.close();
        }
        sessions.clear();
        owner = null;
        context.remove();
    }

    private void changeScreen() {
        Object current = platform.screen();
        if (owner != current) {
            close();
            owner = current;
        }
    }

    private void report(Throwable error) {
        FailurePolicy.rethrowFatal(error);
        platform.report(error);
    }

    private final class Session {
        private final Object view;
        private final Object screen = owner;
        private final StorageInventory<Object> inventory = new StorageInventory<>();
        private final StorageSearchSession<Object> search;
        private final Map<String, Predicate<Object>> terms = new HashMap<>();
        private Map<Object, Object> representatives = new HashMap<>();
        private SearchSettings previousSettings;
        private long previousLanguage = Long.MIN_VALUE;
        private String previousQuery;
        private boolean dirty = true;
        private boolean ready;
        private boolean closed;

        private Session(Object view) {
            this.view = view;
            search = new StorageSearchSession<>(platform.client(), platform.worker(),
                    RefinedStorageController.this::report);
        }

        private void begin() {
            SearchSettings settings = platform.settings();
            long language = platform.languageRevision(settings);
            String query = platform.query(screen);
            Object value = field(view, "map");
            if (!(value instanceof Map<?, ?>)) {
                return;
            }
            List<Object> previous = inventory.values();
            if (dirty) {
                Map<?, ?> map = (Map<?, ?>) value;
                Map<Object, Object> next = new HashMap<>();
                Map<Object, Object> identities = new IdentityHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    next.put(entry.getKey(), entry.getValue());
                    identities.put(entry.getValue(), entry.getKey());
                }
                representatives = next;
                inventory.update(new ArrayList<>(map.values()), identities::get, key -> key);
            }
            List<Object> keys = inventory.values();
            Map<Object, Object> captured = representatives;
            if (keys != previous || !query.equals(previousQuery) || ready
                    || !settings.equals(previousSettings) || language != previousLanguage) {
                terms.clear();
            }
            ready = false;
            dirty = false;
            previousSettings = settings;
            previousLanguage = language;
            previousQuery = query;
            search.begin(keys, keys.size(), 0, language, query, settings,
                    () -> prepare(keys, captured, settings), () -> {
                        if (!closed && owner == screen && platform.screen() == screen) {
                            ready = true;
                        }
                    });
        }

        private Supplier<SearchIndex<Object>> prepare(List<Object> keys,
                                                      Map<Object, Object> representatives,
                                                      SearchSettings settings) {
            return EntrySnapshot.capture(keys, key -> {
                try {
                    EntrySnapshot<Object> entry = new EntrySnapshot<>(key);
                    platform.fill(entry, representatives.get(key), settings);
                    return entry;
                } catch (RuntimeException | LinkageError error) {
                    report(error);
                    return null;
                }
            }, false);
        }

        private void close() {
            closed = true;
            search.close();
            inventory.clear();
            representatives.clear();
            terms.clear();
            previousSettings = null;
            previousQuery = null;
        }
    }

    public static Object call(Object target, String name) {
        if (target == null) {
            return null;
        }
        Method method = ACCESS.get(target.getClass()).method(name);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException error) {
            Throwable cause = error instanceof InvocationTargetException
                    ? ((InvocationTargetException) error).getCause() : error;
            FailurePolicy.rethrowFatal(cause);
            throw new IllegalStateException("Refined Storage method failed: " + name, cause);
        }
    }

    private static Object field(Object target, String name) {
        Field field = ACCESS.get(target.getClass()).field(name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Refined Storage field failed: " + name, error);
        }
    }

    private static final class Access {
        private final Class<?> type;
        private final Map<String, Method> methods = new HashMap<>();
        private final Map<String, Field> fields = new HashMap<>();

        private Access(Class<?> type) {
            this.type = type;
        }

        private Method method(String name) {
            if (!methods.containsKey(name)) {
                Method found = null;
                try {
                    found = type.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
                methods.put(name, found);
            }
            return methods.get(name);
        }

        private Field field(String name) {
            if (!fields.containsKey(name)) {
                Field found = null;
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    try {
                        found = current.getDeclaredField(name);
                        found.setAccessible(true);
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                fields.put(name, found);
            }
            return fields.get(name);
        }
    }
}
