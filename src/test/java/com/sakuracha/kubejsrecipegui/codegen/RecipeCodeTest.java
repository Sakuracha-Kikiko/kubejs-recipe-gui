package com.sakuracha.kubejsrecipegui.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sakuracha.kubejsrecipegui.spec.IngredientSpec;
import com.sakuracha.kubejsrecipegui.spec.OutputSpec;
import com.sakuracha.kubejsrecipegui.spec.RecipeDraft;
import com.sakuracha.kubejsrecipegui.spec.Workstation;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeCodeTest {
    private static RecipeDraft shaped() {
        RecipeDraft draft = RecipeDraft.of(Workstation.CRAFTING);
        draft.input(0).itemId = "minecraft:oak_planks";
        draft.output = new OutputSpec("minecraft:stick", 4, "");
        return draft;
    }

    private static boolean hasProblem(List<RecipeCode.Problem> problems, String key) {
        return problems.stream().anyMatch(problem -> problem.key().equals(key));
    }

    @Test
    void shapedRecipeKeepsPatternAndKey() {
        String json = RecipeCode.recipeJson(shaped());
        assertTrue(json.contains("\"type\":\"minecraft:crafting_shaped\""), json);
        assertTrue(json.contains("\"pattern\":[\"A\"]"), json);
        assertTrue(json.contains("\"A\":{\"item\":\"minecraft:oak_planks\"}"), json);
        assertTrue(json.contains("\"result\":{\"id\":\"minecraft:stick\",\"count\":4}"), json);
    }

    @Test
    void shapedRecipeTrimsEmptyRowsAndColumns() {
        RecipeDraft draft = RecipeDraft.of(Workstation.CRAFTING);
        draft.input(4).itemId = "minecraft:iron_ingot";
        draft.input(8).tagId = "#minecraft:planks";
        draft.output = new OutputSpec("minecraft:anvil", 1, "");
        String json = RecipeCode.recipeJson(draft);
        assertTrue(json.contains("\"pattern\":[\"A \",\" B\"]"), json);
        assertTrue(json.contains("\"A\":{\"item\":\"minecraft:iron_ingot\"}"), json);
        assertTrue(json.contains("\"B\":{\"tag\":\"minecraft:planks\"}"), json);
    }

    @Test
    void shapelessRecipeUsesIngredientList() {
        RecipeDraft draft = RecipeDraft.of(Workstation.CRAFTING);
        draft.shapeless = true;
        draft.input(0).itemId = "minecraft:apple";
        draft.input(5).itemId = "minecraft:sugar";
        draft.output = new OutputSpec("minecraft:cake", 1, "");
        String json = RecipeCode.recipeJson(draft);
        assertTrue(json.contains("\"type\":\"minecraft:crafting_shapeless\""), json);
        assertTrue(json.contains("\"ingredients\":[{\"item\":\"minecraft:apple\"},{\"item\":\"minecraft:sugar\"}]"),
                json);
    }

    @Test
    void cookingRecipeUsesSingleIngredientAndTimes() {
        RecipeDraft draft = RecipeDraft.of(Workstation.FURNACE);
        draft.input(0).itemId = "minecraft:raw_iron";
        draft.output = new OutputSpec("minecraft:iron_ingot", 1, "");
        draft.experience = 0.7F;
        draft.cookingTime = 140;
        String json = RecipeCode.recipeJson(draft);
        assertEquals("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:raw_iron\"},"
                + "\"result\":{\"id\":\"minecraft:iron_ingot\",\"count\":1},\"experience\":0.7,"
                + "\"cookingtime\":140}", json);
    }

    @Test
    void stonecuttingAndSmithingUseTheirOwnShape() {
        RecipeDraft cutting = RecipeDraft.of(Workstation.STONECUTTING);
        cutting.input(0).itemId = "minecraft:stone";
        cutting.output = new OutputSpec("minecraft:stone_slab", 2, "");
        assertTrue(RecipeCode.recipeJson(cutting)
                .startsWith("{\"type\":\"minecraft:stonecutting\",\"ingredient\":"), RecipeCode.recipeJson(cutting));

        RecipeDraft smithing = RecipeDraft.of(Workstation.SMITHING);
        smithing.input(0).itemId = "minecraft:netherite_upgrade_smithing_template";
        smithing.input(1).itemId = "minecraft:diamond_sword";
        smithing.input(2).itemId = "minecraft:netherite_ingot";
        smithing.output = new OutputSpec("minecraft:netherite_sword", 1, "");
        String json = RecipeCode.recipeJson(smithing);
        assertTrue(json.startsWith("{\"type\":\"minecraft:smithing_transform\""), json);
        assertTrue(json.contains("\"template\":{\"item\":\"minecraft:netherite_upgrade_smithing_template\"}"), json);
        assertTrue(json.contains("\"addition\":{\"item\":\"minecraft:netherite_ingot\"}"), json);
    }

    @Test
    void componentsAreKeptOnOutput() {
        RecipeDraft draft = shaped();
        draft.output = new OutputSpec("minecraft:stick", 1, "{\"minecraft:custom_name\":\"\\\"Twig\\\"\"}");
        String json = RecipeCode.recipeJson(draft);
        assertTrue(json.contains("\"components\":{\"minecraft:custom_name\":"), json);
    }

    @Test
    void validationReportsEachProblem() {
        RecipeDraft empty = RecipeDraft.of(Workstation.FURNACE);
        assertTrue(hasProblem(RecipeCode.validate(empty), "gui.kubejs_recipe_gui.error.output_empty"));
        assertTrue(hasProblem(RecipeCode.validate(empty), "gui.kubejs_recipe_gui.error.ingredient_empty"));

        RecipeDraft badCount = shaped();
        badCount.output = new OutputSpec("minecraft:stick", 100, "");
        assertTrue(hasProblem(RecipeCode.validate(badCount), "gui.kubejs_recipe_gui.error.count_range"));

        RecipeDraft mixed = shaped();
        mixed.input(0).tagId = "#minecraft:planks";
        assertTrue(hasProblem(RecipeCode.validate(mixed), "gui.kubejs_recipe_gui.error.ingredient_mixed"));

        RecipeDraft badId = shaped();
        badId.recipeId = "Not a valid id";
        assertTrue(hasProblem(RecipeCode.validate(badId), "gui.kubejs_recipe_gui.error.id_invalid"));

        RecipeDraft noInput = RecipeDraft.of(Workstation.CRAFTING);
        noInput.output = new OutputSpec("minecraft:stick", 1, "");
        assertTrue(hasProblem(RecipeCode.validate(noInput), "gui.kubejs_recipe_gui.error.crafting_needs_input"));

        assertTrue(RecipeCode.validate(shaped()).isEmpty(), RecipeCode.validate(shaped()).toString());
    }

    @Test
    void validDraftPassesValidation() {
        List<RecipeCode.Problem> problems = RecipeCode.validate(shaped());
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void automaticIdIsStableAndNamespaced() {
        String first = RecipeCode.autoId(shaped());
        String second = RecipeCode.autoId(shaped());
        assertEquals(first, second);
        assertTrue(RecipeCode.isValidId(first), first);
        assertTrue(first.startsWith("kubejs:crafting_"), first);
        assertFalse(RecipeCode.isValidId("kubejs:"));
        assertFalse(RecipeCode.isValidId("kubejs:Upper"));
    }

    @Test
    void entryCarriesMetadataAndCustomCall() {
        RecipeDraft draft = shaped();
        String id = RecipeCode.resolveId(draft);
        String entry = RecipeCode.entry(draft, id);
        assertTrue(entry.startsWith("// id: " + id + "\n"), entry);
        assertTrue(entry.contains("// station: crafting\n"), entry);
        assertTrue(entry.contains("// output: minecraft:stick x4\n"), entry);
        assertTrue(entry.contains("// inputs: minecraft:oak_planks\n"), entry);
        assertTrue(entry.contains("event.custom("), entry);
        assertTrue(entry.contains(").id(\"" + id + "\");\n"), entry);
        assertTrue(entry.endsWith(");\n"), entry);
    }

    @Test
    void prettyJsonIndentsWithoutTouchingStrings() {
        String pretty = RecipeCode.prettyJson(shaped());
        assertTrue(pretty.startsWith("{\n  \"type\""), pretty);
        assertTrue(pretty.contains("\n  \"pattern\": ["), pretty);
        assertTrue(pretty.contains("\"minecraft:oak_planks\""), pretty);
        assertTrue(pretty.endsWith("\n}"), pretty);
    }

    @Test
    void ingredientWritesTagWhenSet() {
        RecipeDraft draft = RecipeDraft.of(Workstation.CRAFTING);
        draft.input(0).tagId = "#c:ingots/iron";
        draft.output = new OutputSpec("minecraft:iron_block", 1, "");
        String json = RecipeCode.recipeJson(draft);
        assertTrue(json.contains("\"A\":{\"tag\":\"c:ingots/iron\"}"), json);
    }
}
