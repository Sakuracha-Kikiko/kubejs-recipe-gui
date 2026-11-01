package com.sakuracha.kubejsrecipegui.spec;

/**
 * 编辑器支持的工作站（也就是配方类型分组）。
 */
public enum Workstation {
    CRAFTING("crafting", 9),
    FURNACE("furnace", 1),
    BLAST_FURNACE("blast_furnace", 1),
    SMOKER("smoker", 1),
    CAMPFIRE("campfire", 1),
    STONECUTTING("stonecutting", 1),
    SMITHING("smithing", 3);

    private final String key;
    private final int slots;

    Workstation(String key, int slots) {
        this.key = key;
        this.slots = slots;
    }

    public String key() {
        return this.key;
    }

    public int slotCount() {
        return this.slots;
    }

    public boolean isCrafting() {
        return this == CRAFTING;
    }

    public boolean isCooking() {
        return this == FURNACE || this == BLAST_FURNACE || this == SMOKER || this == CAMPFIRE;
    }

    public boolean isSmithing() {
        return this == SMITHING;
    }

    public boolean isStonecutting() {
        return this == STONECUTTING;
    }

    /** 烹饪与切石只有单个原料格，需要对玩家说明这一点。 */
    public boolean singleInput() {
        return this.slots == 1;
    }

    public String langKey() {
        return "gui.kubejs_recipe_gui.station." + this.key;
    }

    public static Workstation byKey(String key) {
        for (Workstation station : values()) {
            if (station.key.equals(key)) {
                return station;
            }
        }
        return null;
    }

    public static String[] keys() {
        Workstation[] values = values();
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].key;
        }
        return result;
    }
}
