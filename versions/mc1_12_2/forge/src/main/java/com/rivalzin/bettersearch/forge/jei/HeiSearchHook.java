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
        bridge = new JeiSearchBridge(this::elements);
    }

    static boolean install(Object filter, Field elementSearchField) throws Exception {
        Field ready = findField(filter.getClass(), "afterBlock");
        if (ready != null && !ready.getBoolean(filter)) {
            return false;
        }
        Object engine = elementSearchField.get(filter);
        if (engine == null || isWrapped(engine)) {
            return false;
        }
        Field prefixes = findField(engine.getClass(), "prefixedSearchables");
        if (prefixes == null) {
            if (findField(engine.getClass(), "elementInfoList") == null) {
                throw new NoSuchFieldException("Unsupported HEI search engine: " + engine.getClass().getName());
            }
            elementSearchField.set(filter, wrap(engine, elementSearchField.getType(), true));
            return true;
        }
        Class<?> prefixType = Class.forName("mezz.jei.search.PrefixInfo", false, engine.getClass().getClassLoader());
        Object noPrefix = prefixType.getField("NO_PREFIX").get(null);
        Object searchable = ((Map<?, ?>) prefixes.get(engine)).get(noPrefix);
        if (searchable == null) {
            return false;
        }
        Field index = findField(searchable.getClass(), "searchIndex");
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
        } else if ("getSearchResults".equals(name)) {
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
