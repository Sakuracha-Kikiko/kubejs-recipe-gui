package com.sakuracha.kubejsrecipegui.registry;

import com.sakuracha.kubejsrecipegui.KubejsRecipeGui;
import com.sakuracha.kubejsrecipegui.block.RecipeEditorBlock;
import com.sakuracha.kubejsrecipegui.menu.RecipeEditorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块、物品、菜单与创造模式标签页的注册。
 */
public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(KubejsRecipeGui.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KubejsRecipeGui.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, KubejsRecipeGui.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KubejsRecipeGui.MOD_ID);

    public static final DeferredBlock<RecipeEditorBlock> RECIPE_EDITOR =
            BLOCKS.registerBlock("recipe_editor", RecipeEditorBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));

    public static final DeferredItem<BlockItem> RECIPE_EDITOR_ITEM =
            ITEMS.registerSimpleBlockItem("recipe_editor", RECIPE_EDITOR);

    public static final DeferredHolder<MenuType<?>, MenuType<RecipeEditorMenu>> RECIPE_EDITOR_MENU =
            MENUS.register("recipe_editor", () -> IMenuTypeExtension.create(RecipeEditorMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.kubejs_recipe_gui.main"))
                    .icon(() -> new ItemStack(RECIPE_EDITOR_ITEM.get()))
                    .displayItems((parameters, output) -> output.accept(RECIPE_EDITOR_ITEM.get()))
                    .build());

    private ModContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }
}
