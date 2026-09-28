package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.ae2.Ae2NameAccess;
import com.rivalzin.bettersearch.client.ae2.Ae2SearchAccess;
import com.rivalzin.bettersearch.client.ae2.Ae2StorageSearch;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.search.RepoSearch", remap = false)
public abstract class Ae2SearchMixin implements Ae2SearchAccess {
    @Shadow
    @Final
    private Long2BooleanMap cache;
    @Shadow
    private Predicate<Object> search;
    @Shadow
    private String searchString;
    @Unique
    private Ae2StorageSearch bettersearch$storage;

    @Shadow
    private Predicate<Object> fromString(String text) {
        throw new AssertionError();
    }

    @Override
    public void bettersearch$bind(Ae2StorageSearch storage) {
        if (bettersearch$storage != storage) {
            bettersearch$storage = storage;
            search = fromString(searchString);
            cache.clear();
        }
    }

    @Override
    public void bettersearch$clearCache() {
        cache.clear();
    }

    @ModifyArg(method = "getPredicates", at = @At(value = "INVOKE",
            target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"), index = 0, require = 5, allow = 5)
    private Object bettersearch$extendName(Object predicate) {
        if (bettersearch$storage == null || !(predicate instanceof Ae2NameAccess)) {
            return predicate;
        }
        String term = ((Ae2NameAccess) predicate).bettersearch$term();
        Predicate<?> nativePredicate = (Predicate<?>) predicate;
        Ae2StorageSearch storage = bettersearch$storage;
        return ((Predicate<Object>) nativePredicate).or(value -> storage.matches(value, term));
    }
}
