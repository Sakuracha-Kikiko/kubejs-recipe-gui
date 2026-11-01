package com.sakuracha.kubejsrecipegui.spec;

/**
 * 一个原料格的规格：填物品 ID 或标签 ID，二者只能有一个。
 */
public final class IngredientSpec {
    public String itemId = "";
    public String tagId = "";

    public IngredientSpec() {
    }

    public IngredientSpec(String itemId, String tagId) {
        this.itemId = itemId == null ? "" : itemId;
        this.tagId = tagId == null ? "" : tagId;
    }

    public boolean isTag() {
        return !this.tagId.isBlank();
    }

    public boolean isEmpty() {
        return this.itemId.isBlank() && this.tagId.isBlank();
    }

    public boolean isMixed() {
        return !this.itemId.isBlank() && !this.tagId.isBlank();
    }

    /** 去掉可能带上的前导 #，内部统一使用不带 # 的标签 ID。 */
    public String tagCore() {
        return this.tagId.startsWith("#") ? this.tagId.substring(1) : this.tagId;
    }

    /** 用于比较与生成脚本的规范化文本，例如 {@code minecraft:oak_planks} 或 {@code #minecraft:logs}。 */
    public String display() {
        if (isTag()) {
            return "#" + tagCore();
        }
        return this.itemId;
    }

    public IngredientSpec copy() {
        return new IngredientSpec(this.itemId, this.tagId);
    }

    public void clear() {
        this.itemId = "";
        this.tagId = "";
    }
}
