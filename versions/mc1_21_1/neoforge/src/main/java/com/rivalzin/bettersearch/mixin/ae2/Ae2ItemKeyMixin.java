package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.ae2.Ae2ItemKeyAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "appeng.api.stacks.AEItemKey", remap = false)
public abstract class Ae2ItemKeyMixin implements Ae2ItemKeyAccess {
}
