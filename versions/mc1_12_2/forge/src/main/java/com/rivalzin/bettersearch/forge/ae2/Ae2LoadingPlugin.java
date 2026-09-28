package com.rivalzin.bettersearch.forge.ae2;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"com.rivalzin.bettersearch.forge.ae2"})
public final class Ae2LoadingPlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() { return new String[]{Ae2Transformer.class.getName()}; }
    @Override
    public String getModContainerClass() { return null; }
    @Override
    public String getSetupClass() { return null; }
    @Override
    public void injectData(Map<String, Object> data) { }
    @Override
    public String getAccessTransformerClass() { return null; }
}
