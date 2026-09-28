package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2KeyAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets = "appeng.api.stacks.AEKey", remap = false)
public abstract class Ae2KeyMixin implements Ae2KeyAccess {
}
