package com.sakuracha.kubejsrecipegui.spec;

/**
 * 生成脚本时，配方 ID 已经存在于目标文件里的处理方式。
 */
public enum WriteMode {
    REPLACE("replace", "gui.kubejs_recipe_gui.button.overwrite"),
    SKIP("skip", "gui.kubejs_recipe_gui.button.skip");

    private final String key;
    private final String langKey;

    WriteMode(String key, String langKey) {
        this.key = key;
        this.langKey = langKey;
    }

    public String key() {
        return this.key;
    }

    public String langKey() {
        return this.langKey;
    }

    public static WriteMode byKey(String key) {
        for (WriteMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return REPLACE;
    }
}
