package com.sakuracha.kubejsrecipegui.spec;

/**
 * 写入新配方前，先按条件清理脚本里旧配方的策略。
 */
public enum DeleteMode {
    NONE("none", false, "gui.kubejs_recipe_gui.delete.none"),
    SAME_TYPE("type", false, "gui.kubejs_recipe_gui.delete.type"),
    BY_OUTPUT("output", true, "gui.kubejs_recipe_gui.delete.output"),
    BY_TAG("tag", true, "gui.kubejs_recipe_gui.delete.tag"),
    BY_ID("id", true, "gui.kubejs_recipe_gui.delete.id");

    private final String key;
    private final boolean needsValue;
    private final String langKey;

    DeleteMode(String key, boolean needsValue, String langKey) {
        this.key = key;
        this.needsValue = needsValue;
        this.langKey = langKey;
    }

    public String key() {
        return this.key;
    }

    public boolean needsValue() {
        return this.needsValue;
    }

    public String langKey() {
        return this.langKey;
    }

    public DeleteMode next() {
        DeleteMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static DeleteMode byKey(String key) {
        for (DeleteMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return NONE;
    }
}
