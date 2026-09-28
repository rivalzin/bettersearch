package com.rivalzin.bettersearch.client.refinedstorage;

import com.rivalzin.bettersearch.FailurePolicy;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class RsApi {
    private static final ClassValue<Method> AMOUNTS = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals("getAmount") && method.getParameterCount() == 1
                        && method.getReturnType() == long.class) {
                    method.trySetAccessible();
                    return method;
                }
            }
            throw new IllegalStateException("Missing Refined Storage repository amount API");
        }
    };
    private static final ClassValue<Map<String, Method>> METHODS = new ClassValue<>() {
        @Override
        protected Map<String, Method> computeValue(Class<?> type) {
            Map<String, Method> methods = new HashMap<>();
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0) {
                    method.trySetAccessible();
                    methods.put(method.getName(), method);
                }
            }
            return methods;
        }
    };
    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> fields = new HashMap<>();
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.getName().equals("backingList") || field.getName().equals("stickyResources")
                            || field.getName().equals("mapper")) {
                        field.trySetAccessible();
                        fields.putIfAbsent(field.getName(), field);
                    }
                }
            }
            return fields;
        }
    };

    private RsApi() {
    }

    public static Object call(Object owner, String name) {
        if (owner == null) {
            return null;
        }
        Method method = METHODS.get(owner.getClass()).get(name);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(owner);
        } catch (IllegalAccessException | InvocationTargetException error) {
            Throwable cause = error instanceof InvocationTargetException
                    ? ((InvocationTargetException) error).getCause() : error;
            FailurePolicy.rethrowFatal(cause);
            throw new IllegalStateException("Refined Storage API call failed: " + name, cause);
        }
    }

    private static Object field(Object owner, String name) {
        Field field = FIELDS.get(owner.getClass()).get(name);
        if (field == null) {
            throw new IllegalStateException("Missing Refined Storage repository field: " + name);
        }
        try {
            return field.get(owner);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Refined Storage repository field is inaccessible: " + name, error);
        }
    }

    public static RsRepositoryAccess repository(Object menu) {
        Object repository = call(menu, "getRepository");
        return repository instanceof RsRepositoryAccess ? (RsRepositoryAccess) repository : null;
    }

    public static long amount(Object repository, Object resource) {
        try {
            return ((Number) AMOUNTS.get(repository.getClass()).invoke(repository, resource)).longValue();
        } catch (IllegalAccessException | InvocationTargetException error) {
            Throwable cause = error instanceof InvocationTargetException
                    ? ((InvocationTargetException) error).getCause() : error;
            FailurePolicy.rethrowFatal(cause);
            throw new IllegalStateException("Refined Storage repository amount API failed", cause);
        }
    }

    public static List<Object> entries(Object repository) {
        Object backing = call(field(repository, "backingList"), "getAll");
        Object sticky = field(repository, "stickyResources");
        Object mapping = field(repository, "mapper");
        if (!(backing instanceof Collection<?>) || !(sticky instanceof Collection<?>)
                || !(mapping instanceof Function<?, ?>)) {
            throw new IllegalStateException("Incompatible Refined Storage repository API");
        }
        LinkedHashSet<Object> keys = new LinkedHashSet<>((Collection<?>) backing);
        keys.addAll((Collection<?>) sticky);
        List<Object> result = new ArrayList<>(keys.size());
        Function<Object, ?> mapper = (Function<Object, ?>) mapping;
        for (Object key : keys) {
            Object mapped = mapper.apply(key);
            if (mapped != null) {
                result.add(mapped);
            }
        }
        return result;
    }

    public static String term(Object literal) {
        Object text = call(call(literal, "token"), "content");
        return text instanceof String ? (String) text : "";
    }
}
