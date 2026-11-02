package com.sakuracha.kubejsrecipegui.menu;

import com.sakuracha.kubejsrecipegui.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 编辑器菜单。原料与产出格是界面自绘的“引用格”，不占用真实物品槽；底部玩家背包则是真实槽位，
 * 这样拿起、放下、右键分半都走原版的容器同步逻辑。
 */
public class RecipeEditorMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public RecipeEditorMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModContent.RECIPE_EDITOR_MENU.get(), containerId);
        this.pos = pos;
        this.addPlayerSlots(playerInventory);
    }

    /** 客户端由 {@code IMenuTypeExtension} 用方块坐标重建菜单。 */
    public RecipeEditorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, data.readBlockPos());
    }

    /**
     * 只登记玩家背包：主背包 3×9（槽位 9–35）在前，快捷栏（槽位 0–8）在后。
     * 槽位坐标由界面自绘，这里不参与原版的槽位布局。
     */
    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 0, 0));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 0, 0));
        }
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
