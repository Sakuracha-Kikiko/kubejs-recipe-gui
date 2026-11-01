package com.sakuracha.kubejsrecipegui.menu;

import com.sakuracha.kubejsrecipegui.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 编辑器菜单。界面不使用真实物品槽，但 JEI 配方转移与 {@code /reload} 之后的界面重建都要求它是真正的
 * {@link AbstractContainerMenu}，所以这里保持一个零槽位的菜单。
 */
public class RecipeEditorMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public RecipeEditorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModContent.RECIPE_EDITOR_MENU.get(), containerId);
        this.pos = pos;
    }

    /** 客户端由 {@code IMenuTypeExtension} 用方块坐标重建菜单。 */
    public RecipeEditorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, data.readBlockPos());
    }

    public BlockPos pos() {
        return this.pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D) <= 64.0D;
    }
}
