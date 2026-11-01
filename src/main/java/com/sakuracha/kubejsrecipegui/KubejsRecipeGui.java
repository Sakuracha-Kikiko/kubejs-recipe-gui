package com.sakuracha.kubejsrecipegui;

import com.sakuracha.kubejsrecipegui.registry.ModContent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * 模组入口：只负责注册内容，界面逻辑在客户端包内。
 */
@Mod(KubejsRecipeGui.MOD_ID)
public final class KubejsRecipeGui {
    public static final String MOD_ID = "kubejs_recipe_gui";

    public KubejsRecipeGui(IEventBus modBus, ModContainer container) {
        ModContent.register(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
