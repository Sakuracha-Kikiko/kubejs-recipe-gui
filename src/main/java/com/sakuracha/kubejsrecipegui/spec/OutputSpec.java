package com.sakuracha.kubejsrecipegui.spec;

/**
 * 产出物品：物品 ID、数量与可选的数据组件 JSON。
 */
public final class OutputSpec {
    public String itemId = "";
    public int count = 1;
    /** 原样嵌入 {@code result.components} 的 JSON 对象文本，空串表示不写。 */
    public String components = "";

    public OutputSpec() {
    }

    public OutputSpec(String itemId, int count, String components) {
        this.itemId = itemId == null ? "" : itemId;
        this.count = count;
        this.components = components == null ? "" : components;
    }

    public boolean isEmpty() {
        return this.itemId.isBlank();
    }

    public OutputSpec copy() {
        return new OutputSpec(this.itemId, this.count, this.components);
    }

    public String display() {
        if (isEmpty()) {
            return "";
        }
        return this.count > 1 ? this.itemId + " x" + this.count : this.itemId;
    }
}
