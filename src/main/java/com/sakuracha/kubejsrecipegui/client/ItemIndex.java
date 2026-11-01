package com.sakuracha.kubejsrecipegui.client;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 客户端物品表：物品选择浮层与幻影槽图标都用它把字符串 ID 映射成图标。
 */
public final class ItemIndex {
    private static List<String> all;

    private ItemIndex() {
    }

    /** 全部已注册物品的 ID，按字典序排列，只计算一次。 */
    public static List<String> all() {
        if (all == null) {
            all = BuiltInRegistries.ITEM.keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .toList();
        }
        return all;
    }

    /** 非法或未知的 ID 一律返回空栈，界面照常显示空格。 */
    public static ItemStack stack(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
