package com.sakuracha.kubejsrecipegui.client;

import com.sakuracha.kubejsrecipegui.KubejsRecipeGui;
import com.sakuracha.kubejsrecipegui.registry.ModContent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 只在客户端加载：把菜单接到界面上。
 */
@EventBusSubscriber(modid = KubejsRecipeGui.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModContent.RECIPE_EDITOR_MENU.get(), RecipeEditorScreen::new);
    }
}
