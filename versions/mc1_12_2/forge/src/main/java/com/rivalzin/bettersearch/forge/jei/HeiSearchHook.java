package com.rivalzin.bettersearch.forge.jei;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HeiSearchHook implements InvocationHandler {
    private static final ClassValue<Field> READY_FIELDS = fields("afterBlock");
    private static final ClassValue<Field> INDEX_FIELDS = fields("searchIndex");
    private static final ClassValue<EngineAccess> ENGINES = new ClassValue<EngineAccess>() {
        @Override
        protected EngineAccess computeValue(Class<?> type) {
            return new EngineAccess(type);
        }
    };
    private final Object original;
    private final Method allElements;
    private final Field tokenText;
    private final Field tokenPrefix;
    private final Object noPrefix;
    private final JeiSearchBridge bridge;
    private List<?> elements;

    private HeiSearchHook(Object original, Class<?> contract, boolean lowMemory) throws Exception {
        this.original = original;
        if (lowMemory) {
            allElements = contract.getMethod("getAllIngredients");
            Class<?> tokenType = Class.forName("mezz.jei.search.TokenInfo", false, contract.getClassLoader());
            tokenText = tokenType.getField("token");
            tokenPrefix = tokenType.getField("prefixInfo");
            noPrefix = tokenPrefix.getType().getField("NO_PREFIX").get(null);
        } else {
            allElements = contract.getMethod("getAllElements", Set.class);
            tokenText = null;
            tokenPrefix = null;
            noPrefix = null;
        }
        bridge = new JeiSearchBridge(this::elements, "hei");
    }

    static boolean install(Object filter, Field elementSearchField) throws Exception {
        Field ready = READY_FIELDS.get(filter.getClass());
        if (ready != null && !ready.getBoolean(filter)) {
            return false;
        }
        Object engine = elementSearchField.get(filter);
        if (engine == null || isWrapped(engine)) {
            return false;
        }
        EngineAccess access = ENGINES.get(engine.getClass());
        if (access.prefixes == null) {
            if (!access.lowMemory) {
                throw new NoSuchFieldException("Unsupported HEI search engine: " + engine.getClass().getName());
            }
            elementSearchField.set(filter, wrap(engine, elementSearchField.getType(), true));
            return true;
        }
        Object searchable = ((Map<?, ?>) access.prefixes.get(engine)).get(access.noPrefix());
        if (searchable == null) {
            return false;
        }
        Field index = INDEX_FIELDS.get(searchable.getClass());
        if (index == null || !index.getType().isInterface()) {
            throw new NoSuchFieldException("HEI search index API is unavailable");
        }
        Object original = index.get(searchable);
        if (original == null || isWrapped(original)) {
            return false;
        }
        index.set(searchable, wrap(original, index.getType(), false));
        return true;
    }

    private static Object wrap(Object original, Class<?> contract, boolean lowMemory) throws Exception {
        return Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract},
                new HeiSearchHook(original, contract, lowMemory));
    }

    private static boolean isWrapped(Object value) {
        return Proxy.isProxyClass(value.getClass()) && Proxy.getInvocationHandler(value) instanceof HeiSearchHook;
    }

    private static ClassValue<Field> fields(String name) {
        return new ClassValue<Field>() {
            @Override
            protected Field computeValue(Class<?> type) {
                return findField(type, name);
            }
        };
    }

    private static final class EngineAccess {
        final Class<?> type;
        final Field prefixes;
        final boolean lowMemory;
        private Object noPrefix;

        EngineAccess(Class<?> type) {
            this.type = type;
            prefixes = findField(type, "prefixedSearchables");
            lowMemory = prefixes == null && findField(type, "elementInfoList") != null;
        }

        synchronized Object noPrefix() throws Exception {
            if (noPrefix == null) {
                Class<?> prefixType = Class.forName("mezz.jei.search.PrefixInfo", false, type.getClassLoader());
                noPrefix = prefixType.getField("NO_PREFIX").get(null);
            }
            return noPrefix;
        }
    }

    static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private synchronized List<?> elements() throws Exception {
        if (elements == null) {
            if (tokenText == null) {
                Set<Object> all = identitySet();
                allElements.invoke(original, all);
                elements = new ArrayList<>(all);
            } else {
                elements = new ArrayList<>((Collection<?>) allElements.invoke(original));
            }
        }
        return elements;
    }

    private void changed() {
        synchronized (this) {
            elements = null;
        }
        bridge.invalidate();
    }

    private void addMatches(String word, Set<Object> results) {
        results.addAll(bridge.searchElements(word));
    }

    private static Set<Object> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            if ("equals".equals(name)) {
                return proxy == arguments[0];
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            return "Better Search HEI adapter (" + original.getClass().getName() + ')';
        }
        Object result;
        try {
            result = method.invoke(original, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
        if ("put".equals(name) || "add".equals(name) || "addAll".equals(name)) {
            changed();
        } else if ("getSearchResults".equals(name) && !HeiLookupScope.active()) {
            if (tokenText == null) {
                addMatches((String) arguments[0], (Set<Object>) arguments[1]);
            } else if (tokenPrefix.get(arguments[0]) == noPrefix) {
                Set<Object> union = identitySet();
                union.addAll((Set<?>) result);
                addMatches((String) tokenText.get(arguments[0]), union);
                result = union;
            }
        }
        return result;
    }
}
