package com.sakuracha.kubejsrecipegui.block;

import com.sakuracha.kubejsrecipegui.menu.RecipeEditorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 配方编辑器方块：右键打开编辑界面。界面本身没有真实物品槽，打开时只需要把坐标带给服务端。
 */
public class RecipeEditorBlock extends Block {
    public RecipeEditorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> new RecipeEditorMenu(containerId, inventory, pos),
                            Component.translatable("container.kubejs_recipe_gui.recipe_editor")),
                    buffer -> buffer.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
