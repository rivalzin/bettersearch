package com.rivalzin.bettersearch.mixin.ae2;

import com.rivalzin.bettersearch.client.Ae2PatternGroupAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets = "appeng.api.implementations.blockentities.PatternContainerGroup", remap = false)
public abstract class Ae2PatternGroupMixin implements Ae2PatternGroupAccess {
}
