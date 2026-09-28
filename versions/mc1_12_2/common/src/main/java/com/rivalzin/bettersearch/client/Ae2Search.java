package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StorageInventory;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.client.gui.GuiTextField;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Ae2Search {
    private static final Map<Object, Session> SESSIONS = new IdentityHashMap<>();
    private static final ClassValue<Access> ACCESS = new ClassValue<Access>() {
        @Override
        protected Access computeValue(Class<?> type) {
            return new Access(type);
        }
    };
    private static Object owner;
    private static SearchSettings previousSettings;
    private static long previousLanguage = Long.MIN_VALUE;
    private static boolean refreshing;

    private Ae2Search() {
    }

    public static void tick() {
        Object current = screen();
        if (owner != current) {
            for (Session value : SESSIONS.values()) {
                value.close();
            }
            SESSIONS.clear();
            owner = current;
            previousSettings = null;
        }
        if (SESSIONS.isEmpty()) {
            return;
        }
        updateSettings();
    }

    private static void updateSettings() {
        SearchSettings settings = settings();
        ensureLanguages(settings);
        long language = languageRevision();
        if (previousSettings == null || !settings.equals(previousSettings)
                || previousLanguage != language) {
            previousSettings = settings;
            previousLanguage = language;
            for (Session session : new ArrayList<>(SESSIONS.values())) {
                session.refresh();
            }
        }
    }

    public static void beginRepository(Object repository) {
        begin(repository, false);
    }

    public static void beginInterface(Object terminal) {
        begin(terminal, true);
    }

    private static void begin(Object source, boolean patterns) {
        try {
            Object current = screen();
            if (owner != current) {
                tick();
            }
            if (owner == null || source == null) {
                return;
            }
            Session session = SESSIONS.get(source);
            if (session == null) {
                session = new Session(source, patterns);
                SESSIONS.put(source, session);
            }
            session.begin();
        } catch (RuntimeException | LinkageError error) {
            report(error);
        }
    }

    public static boolean repositoryMatch(boolean original, Object source, Object entry) {
        if (original) {
            return true;
        }
        try {
            Session session = SESSIONS.get(source);
            return session != null && session.matches(new Key(entry, 0));
        } catch (RuntimeException | LinkageError error) {
            report(error);
            return false;
        }
    }

    public static boolean patternMatch(boolean original, Object source, Object pattern, String query) {
        if (original || !(pattern instanceof ItemStack)) {
            return original;
        }
        try {
            Session session = SESSIONS.get(source);
            if (session != null) {
                for (ItemStack output : outputs((ItemStack) pattern)) {
                    if (session.matches(new Key(output, 1))) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException | LinkageError error) {
            report(error);
        }
        return false;
    }

    public static boolean contains(String name, CharSequence query, Object source) {
        if (name.contains(query)) {
            return true;
        }
        Session session = SESSIONS.get(source);
        return session != null && session.matches(new Key(name.toLowerCase(Locale.ROOT), 2));
    }

    private static void report(Throwable error) {
        FailurePolicy.rethrowFatal(error);
        BetterSearch.LOGGER.debug("[{}] AE2 search fallback: {}", BetterSearch.MOD_NAME, error.toString());
    }

    private static final class Session {
        private final Object source;
        private final Object screen = owner;
        private final boolean patterns;
        private final StorageInventory<Key> inventory = new StorageInventory<>();
        private final Map<Key, Key> canonical = new HashMap<>();
        private final StorageSearchSession<Key> search;
        private Predicate<Key> matcher = value -> false;
        private boolean closed;

        private Session(Object source, boolean patterns) {
            this.source = source;
            this.patterns = patterns;
            search = new StorageSearchSession<>(Ae2Search::execute, ForkJoinPool.commonPool(), Ae2Search::report);
        }

        private void begin() {
            SearchSettings settings = settings();
            settings.enabled &= settings.searchAe2;
            ensureLanguages(settings);
            String query = patterns ? interfaceQuery(source) : String.valueOf(call(source, "getSearchString"));
            if (settings.enabled && StorageSearchSession.isPlainTerm(query)) {
                List<Object> entries = new ArrayList<>();
                if (patterns) {
                    Object byId = field(source, "byId");
                    if (byId instanceof Map<?, ?>) {
                        for (Object record : ((Map<?, ?>) byId).values()) {
                            Object name = call(record, "getSearchName");
                            if (name == null) {
                                name = call(record, "getName");
                            }
                            if (name != null) {
                                entries.add(new Key(String.valueOf(name).toLowerCase(Locale.ROOT), 2));
                            }
                            Object contents = record instanceof Iterable<?> ? record : call(record, "getInventory");
                            if (contents instanceof Iterable<?>) {
                                for (Object value : (Iterable<?>) contents) {
                                    if (value instanceof ItemStack) {
                                        for (ItemStack output : outputs((ItemStack) value)) {
                                            entries.add(new Key(output, 1));
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Object cache = field(source, "cachedSearches");
                    if (cache instanceof Map<?, ?>) {
                        ((Map<?, ?>) cache).clear();
                    }
                } else {
                    Object values = field(source, "list");
                    if (!(values instanceof Iterable<?>)) {
                        Object map = field(source, "entries");
                        values = map instanceof Map<?, ?> ? ((Map<?, ?>) map).values() : null;
                    }
                    if (values instanceof Iterable<?>) {
                        for (Object value : (Iterable<?>) values) {
                            Object stack = call(value, "getStack");
                            entries.add(new Key(stack == null ? value : stack, 0));
                        }
                    }
                }
                inventory.update(entries, value -> (Key) value, Key::copy);
                canonical.clear();
                for (Key value : inventory.values()) {
                    canonical.put(value, value);
                }
            }
            List<Key> values = inventory.values();
            search.begin(values, values.size(), 0, languageRevision(), query, settings,
                    () -> prepare(values, settings), this::refresh);
            matcher = search.matcher(query);
        }

        private boolean matches(Key key) {
            return !closed && owner == screen && matcher.test(canonical.get(key));
        }

        private Supplier<SearchIndex<Key>> prepare(List<Key> values, SearchSettings settings) {
            List<String> codes = LangTable.activeCodes(settings);
            return EntrySnapshot.capture(values, key -> {
                try {
                    EntrySnapshot<Key> entry = new EntrySnapshot<>(key);
                    if (key.kind == 2) {
                        return entry.add((String) key.value, SearchField.SOURCE_NATIVE);
                    }
                    ItemStack stack = key.item();
                    if (stack != null && !empty(stack)) {
                        CreativeIndex.fill(entry, stack, settings, codes, stack.getDisplayName(), tooltip(stack, settings));
                    } else {
                        fillFluid(entry, key.value, settings, codes);
                    }
                    return entry;
                } catch (RuntimeException | LinkageError error) {
                    report(error);
                    return null;
                }
            }, false);
        }

        private void refresh() {
            if (closed || screen() != screen || refreshing) {
                return;
            }
            refreshing = true;
            try {
                call(source, patterns ? "refreshList" : "updateView");
            } catch (RuntimeException | LinkageError error) {
                report(error);
            } finally {
                refreshing = false;
            }
        }

        private void close() {
            closed = true;
            search.close();
            inventory.clear();
            canonical.clear();
            matcher = value -> false;
        }
    }

    private static final class Key {
        private final Object value;
        private final int kind;
        private final int hash;

        private Key(Object value, int kind) {
            this.value = Objects.requireNonNull(value);
            this.kind = kind;
            if (kind == 1) {
                ItemStack stack = (ItemStack) value;
                hash = 31 * (31 * System.identityHashCode(stack.getItem()) + damage(stack))
                        + Objects.hashCode(tag(stack));
            } else {
                hash = value.hashCode();
            }
        }

        private Key copy() {
            if (kind == 1) {
                return new Key(((ItemStack) value).copy(), kind);
            }
            if (kind == 2) {
                return this;
            }
            return new Key(Objects.requireNonNull(call(value, "copy")), kind);
        }

        private ItemStack item() {
            if (kind == 1) {
                return (ItemStack) value;
            }
            Object stack = call(value, "getDefinition");
            if (!(stack instanceof ItemStack)) {
                stack = call(value, "createItemStack");
            }
            if (!(stack instanceof ItemStack)) {
                stack = call(value, "getItemStack");
            }
            return stack instanceof ItemStack ? (ItemStack) stack : null;
        }

        @Override
        public int hashCode() {
            return 31 * hash + kind;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return kind == key.kind && (kind == 1
                    ? same((ItemStack) value, (ItemStack) key.value) : value.equals(key.value));
        }
    }

    private static void fillFluid(EntrySnapshot<?> entry, Object stack, SearchSettings settings, List<String> codes) {
        Object fluid = call(stack, "getFluid");
        Object volume = call(stack, "getFluidStack");
        Object name = call(volume, "getLocalizedName");
        if (name == null) {
            name = call(volume, "getDisplayName");
        }
        if (name == null) {
            name = call(volume, "getName");
        }
        if (name == null) {
            name = call(fluid, "getLocalizedName", volume);
        }
        Object attributes = call(fluid, "getAttributes");
        if (name == null && attributes != null) {
            name = call(attributes, "getDisplayName", volume);
        }
        String nativeName = text(name);
        entry.add(nativeName, SearchField.SOURCE_NATIVE);
        Object translationKey = call(volume, "getTranslationKey");
        if (translationKey == null) {
            translationKey = call(fluid, "getUnlocalizedName", volume);
        }
        if (translationKey == null) {
            translationKey = call(fluid, "getUnlocalizedName");
        }
        if (translationKey == null && attributes != null) {
            translationKey = call(attributes, "getTranslationKey", volume);
        }
        if (translationKey == null) {
            translationKey = componentKey(name);
        }
        if (settings.crossLanguage && translationKey != null) {
            String key = String.valueOf(translationKey);
            for (String code : codes) {
                String translated = translation(code, key);
                if (translated == null) {
                    translated = translation(code, key + ".name");
                }
                entry.add(translated, "en_us".equalsIgnoreCase(code)
                        ? SearchField.SOURCE_ENGLISH : SearchField.SOURCE_FOREIGN);
            }
        }
        Object id = call(fluid, "getRegistryName");
        if (id == null) {
            Object fluidEntry = field(fluid, "entry");
            id = call(fluidEntry, "getId");
        }
        if (id == null) {
            Object registeredName = call(fluid, "getName");
            if (registeredName instanceof String) {
                id = registeredName;
            }
        }
        if (nativeName.isEmpty()) {
            throw new IllegalStateException("AE2 fluid has no supported display name: " + stack.getClass().getName());
        }
        if (id != null && settings.searchItemIds) {
            entry.add(String.valueOf(id).replace(':', ' ').replace('_', ' '), SearchField.SOURCE_ID);
        }
    }

    private static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        Method method = ACCESS.get(target.getClass()).method(name, args);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException error) {
            Throwable cause = error.getCause();
            if (cause != null) {
                FailurePolicy.rethrowFatal(cause);
            }
            throw new IllegalStateException("AE2 method " + name, error);
        }
    }

    private static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        Field field = ACCESS.get(target.getClass()).field(name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("AE2 field " + name, error);
        }
    }

    private static final class Access {
        private final Class<?> type;
        private final Map<String, Field> fields = new HashMap<>();
        private final Map<String, Method> methods = new HashMap<>();

        private Access(Class<?> type) {
            this.type = type;
        }

        private synchronized Field field(String name) {
            if (fields.containsKey(name)) {
                return fields.get(name);
            }
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                try {
                    Field value = current.getDeclaredField(name);
                    value.setAccessible(true);
                    fields.put(name, value);
                    return value;
                } catch (NoSuchFieldException ignored) {
                    continue;
                }
            }
            fields.put(name, null);
            return null;
        }

        private synchronized Method method(String name, Object[] args) {
            if (args.length > 1 || args.length == 1 && args[0] == null) {
                return null;
            }
            String signature = name + (args.length == 0 ? "()" : "(" + args[0].getClass().getName() + ")");
            if (methods.containsKey(signature)) {
                return methods.get(signature);
            }
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                if (args.length == 0) {
                    try {
                        Method value = current.getDeclaredMethod(name);
                        value.setAccessible(true);
                        methods.put(signature, value);
                        return value;
                    } catch (NoSuchMethodException ignored) {
                        continue;
                    }
                }
                for (Class<?> argument = args[0].getClass(); argument != null; argument = argument.getSuperclass()) {
                    try {
                        Method value = current.getDeclaredMethod(name, argument);
                        value.setAccessible(true);
                        methods.put(signature, value);
                        return value;
                    } catch (NoSuchMethodException ignored) {
                        continue;
                    }
                }
            }
            methods.put(signature, null);
            return null;
        }
    }

    private static Object screen() { return Minecraft.getMinecraft().currentScreen; }
    private static void execute(Runnable task) { Minecraft.getMinecraft().addScheduledTask(task); }
    private static SearchSettings settings() { return ModConfig.settings(); }
    private static void ensureLanguages(SearchSettings settings) { if (settings.enabled && settings.searchAe2) LangTable.ensure(settings); }
    private static long languageRevision() { return LangTable.stamp(); }
    private static String translation(String code, String key) { return LangTable.get(code, key); }
    private static String interfaceQuery(Object source) { Object box = field(source, "searchField"); return box instanceof GuiTextField ? ((GuiTextField) box).getText() : ""; }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static Object componentKey(Object value) { return null; }
    private static boolean empty(ItemStack stack) { return stack == null || stack.isEmpty(); }
    private static int damage(ItemStack stack) { return stack.getItemDamage(); }
    private static Object tag(ItemStack stack) { return stack.getTagCompound(); }
    private static boolean same(ItemStack left, ItemStack right) { return ItemStack.areItemsEqual(left, right) && ItemStack.areItemStackTagsEqual(left, right); }
    private static List<String> tooltip(ItemStack stack, SearchSettings settings) {
        if (!settings.searchTooltips || Minecraft.getMinecraft().player == null) return null;
        List<String> result = new ArrayList<>();
        for (Object line : stack.getTooltip(Minecraft.getMinecraft().player, net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL)) result.add(String.valueOf(line));
        return result;
    }
    private static List<ItemStack> outputs(ItemStack pattern) {
        if (empty(pattern) || !pattern.hasTagCompound()) return Collections.emptyList();
        NBTTagList out = pattern.getTagCompound().getTagList("out", 10);
        List<ItemStack> result = new ArrayList<>(out.tagCount());
        for (int i = 0; i < out.tagCount(); i++) {
            ItemStack stack = new ItemStack(out.getCompoundTagAt(i));
            if (!empty(stack)) result.add(stack);
        }
        return result;
    }
}
