package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2NamePredicate;
import com.rivalzin.bettersearch.client.Ae2Search;
import com.rivalzin.bettersearch.client.Ae2SearchAccess;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.function.Predicate;
@Pseudo
@Mixin(targets = "appeng.client.gui.me.search.RepoSearch", remap = false)
public abstract class Ae2RepoSearchMixin implements Ae2SearchAccess {
    @Shadow(remap = false)
    private Long2BooleanMap cache;
    @Unique
    private Ae2Search bettersearch$search;
    @Override
    public void bettersearch$bind(Ae2Search search) {
        if (bettersearch$search != search) {
            bettersearch$search = search;
            cache.clear();
        }
    }
    @Override
    public void bettersearch$invalidate() {
        cache.clear();
    }
    @Inject(method = "getPredicates", at = @At("RETURN"), remap = false)
    private void bettersearch$extend(String query, CallbackInfoReturnable<List<Predicate<Object>>> cir) {
        List<Predicate<Object>> predicates = cir.getReturnValue();
        for (int i = 0; i < predicates.size(); i++) {
            Predicate<Object> original = predicates.get(i);
            if (original instanceof Ae2NamePredicate) {
                String term = ((Ae2NamePredicate) original).bettersearch$term();
                predicates.set(i, original.or(entry -> bettersearch$search != null
                        && bettersearch$search.matches(term, entry)));
            }
        }
    }
}
