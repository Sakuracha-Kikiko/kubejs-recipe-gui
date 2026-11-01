package com.sakuracha.kubejsrecipegui.spec;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑器里正在编辑的配方。纯数据对象，不引用游戏类，方便单元测试。
 */
public final class RecipeDraft {
    public Workstation station = Workstation.CRAFTING;
    /** 仅工作台配方有意义：{@code true} 为无序合成。 */
    public boolean shapeless = false;
    public final List<IngredientSpec> inputs = new ArrayList<>();
    public OutputSpec output = new OutputSpec();
    public float experience = 0.35F;
    public int cookingTime = 200;
    public String recipeId = "";
    public WriteMode writeMode = WriteMode.REPLACE;
    public DeleteMode deleteMode = DeleteMode.NONE;
    public String deleteValue = "";

    public RecipeDraft() {
        this.resizeInputs();
    }

    public static RecipeDraft of(Workstation station) {
        RecipeDraft draft = new RecipeDraft();
        draft.station = station;
        draft.resizeInputs();
        return draft;
    }

    /** 切换到另一个工作站时重置原料格数量。 */
    public void station(Workstation station) {
        this.station = station;
        this.shapeless = false;
        this.resizeInputs();
    }

    public void resizeInputs() {
        int wanted = this.station.slotCount();
        while (this.inputs.size() < wanted) {
            this.inputs.add(new IngredientSpec());
        }
        while (this.inputs.size() > wanted) {
            this.inputs.remove(this.inputs.size() - 1);
        }
    }

    public IngredientSpec input(int index) {
        return index >= 0 && index < this.inputs.size() ? this.inputs.get(index) : new IngredientSpec();
    }

    public boolean isCrafting() {
        return this.station.isCrafting();
    }

    public RecipeDraft copy() {
        RecipeDraft copy = new RecipeDraft();
        copy.station = this.station;
        copy.shapeless = this.shapeless;
        copy.inputs.clear();
        for (IngredientSpec input : this.inputs) {
            copy.inputs.add(input.copy());
        }
        copy.output = this.output.copy();
        copy.experience = this.experience;
        copy.cookingTime = this.cookingTime;
        copy.recipeId = this.recipeId;
        copy.writeMode = this.writeMode;
        copy.deleteMode = this.deleteMode;
        copy.deleteValue = this.deleteValue;
        return copy;
    }
}
