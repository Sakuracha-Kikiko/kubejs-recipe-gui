package com.sakuracha.kubejsrecipegui.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sakuracha.kubejsrecipegui.spec.DeleteMode;
import com.sakuracha.kubejsrecipegui.spec.WriteMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptStoreTest {
    private static ScriptStore.Entry entry(String id, String station, String output, String inputs, String marker) {
        String text = "// id: " + id + "\n"
                + "// station: " + station + "\n"
                + "// output: " + output + "\n"
                + "// inputs: " + inputs + "\n"
                + "event.custom({\"type\":\"" + marker + "\"}).id(\"" + id + "\");\n";
        return new ScriptStore.Entry(id, station, output, inputs, text);
    }

    private static ScriptStore.Entry stick() {
        return entry("kubejs:crafting_stick", "crafting", "minecraft:stick x4", "minecraft:oak_planks", "a");
    }

    @Test
    void firstWriteCreatesFileSkeleton() {
        ScriptStore.MergeResult result = ScriptStore.merge(null, stick(), WriteMode.SKIP, DeleteMode.NONE, "");
        assertEquals(ScriptStore.Outcome.CREATED, result.outcome());
        String content = result.content();
        assertTrue(content.contains(ScriptStore.MARK_BEGIN), content);
        assertTrue(content.contains(ScriptStore.MARK_END), content);
        assertTrue(content.contains("ServerEvents.recipes(event => {"), content);
        assertTrue(content.contains("// id: kubejs:crafting_stick"), content);
        assertTrue(content.endsWith("})\n"), content);
    }

    @Test
    void secondRecipeIsAppendedInsideTheMarkerBlock() {
        String first = ScriptStore.merge(null, stick(), WriteMode.SKIP, DeleteMode.NONE, "").content();
        ScriptStore.Entry cake = entry("kubejs:crafting_cake", "crafting", "minecraft:cake", "minecraft:apple", "b");
        ScriptStore.MergeResult result = ScriptStore.merge(first, cake, WriteMode.SKIP, DeleteMode.NONE, "");
        assertEquals(ScriptStore.Outcome.CREATED, result.outcome());
        assertTrue(result.content().contains("kubejs:crafting_stick"), result.content());
        assertTrue(result.content().contains("kubejs:crafting_cake"), result.content());
        assertEquals(1, countOccurrences(result.content(), ScriptStore.MARK_BEGIN));
    }

    @Test
    void existingIdIsSkippedWhenModeIsSkip() {
        String first = ScriptStore.merge(null, stick(), WriteMode.SKIP, DeleteMode.NONE, "").content();
        ScriptStore.Entry changed = entry("kubejs:crafting_stick", "crafting", "minecraft:stick x8",
                "minecraft:oak_planks", "changed");
        ScriptStore.MergeResult result = ScriptStore.merge(first, changed, WriteMode.SKIP, DeleteMode.NONE, "");
        assertEquals(ScriptStore.Outcome.SKIPPED, result.outcome());
        assertEquals(first, result.content());
        assertFalse(result.content().contains("changed"));
    }

    @Test
    void existingIdIsReplacedWhenModeIsReplace() {
        String first = ScriptStore.merge(null, stick(), WriteMode.SKIP, DeleteMode.NONE, "").content();
        ScriptStore.Entry changed = entry("kubejs:crafting_stick", "crafting", "minecraft:stick x8",
                "minecraft:oak_planks", "changed");
        ScriptStore.MergeResult result = ScriptStore.merge(first, changed, WriteMode.REPLACE, DeleteMode.NONE, "");
        assertEquals(ScriptStore.Outcome.REPLACED, result.outcome());
        assertTrue(result.content().contains("changed"), result.content());
        assertFalse(result.content().contains("\"type\":\"a\""), result.content());
        assertEquals(1, countOccurrences(result.content(), "// id: kubejs:crafting_stick"));
    }

    @Test
    void sameTypeDeletionRemovesPreviousRecipesOfThatStation() {
        String first = ScriptStore.merge(null, stick(), WriteMode.SKIP, DeleteMode.NONE, "").content();
        ScriptStore.Entry furnace = entry("kubejs:furnace_iron", "furnace", "minecraft:iron_ingot",
                "minecraft:raw_iron", "c");
        String withFurnace = ScriptStore.merge(first, furnace, WriteMode.SKIP, DeleteMode.NONE, "").content();

        ScriptStore.Entry anotherCrafting = entry("kubejs:crafting_cake", "crafting", "minecraft:cake",
                "minecraft:apple", "d");
        ScriptStore.MergeResult result = ScriptStore.merge(withFurnace, anotherCrafting, WriteMode.REPLACE,
                DeleteMode.SAME_TYPE, "");
        assertEquals(ScriptStore.Outcome.REPLACED, result.outcome());
        assertEquals(List.of("kubejs:crafting_stick"), result.removed());
        assertFalse(result.content().contains("kubejs:crafting_stick"), result.content());
        assertTrue(result.content().contains("kubejs:furnace_iron"), result.content());
        assertTrue(result.content().contains("kubejs:crafting_cake"), result.content());
    }

    @Test
    void deletionByOutputIgnoresTheCountSuffix() {
        String first = ScriptStore.merge(null, stick(), WriteMode.SKIP, DeleteMode.NONE, "").content();
        ScriptStore.Entry replacement = entry("kubejs:crafting_twig", "crafting", "minecraft:stick x1",
                "minecraft:birch_planks", "e");
        ScriptStore.MergeResult result = ScriptStore.merge(first, replacement, WriteMode.REPLACE,
                DeleteMode.BY_OUTPUT, "minecraft:stick");
        assertEquals(List.of("kubejs:crafting_stick"), result.removed());
        assertFalse(result.content().contains("kubejs:crafting_stick"), result.content());
    }

    @Test
    void deletionByTagLooksAtIngredientSummary() {
        String first = ScriptStore.merge(null, stick(), WriteMode.SKIP, DeleteMode.NONE, "").content();
        ScriptStore.Entry tagged = entry("kubejs:crafting_planks", "crafting", "minecraft:oak_planks",
                "#minecraft:planks", "f");
        String withTagged = ScriptStore.merge(first, tagged, WriteMode.SKIP, DeleteMode.NONE, "").content();
        ScriptStore.Entry replacement = entry("kubejs:crafting_other", "crafting", "minecraft:stick x2",
                "minecraft:birch_planks", "g");
        ScriptStore.MergeResult result = ScriptStore.merge(withTagged, replacement, WriteMode.REPLACE,
                DeleteMode.BY_TAG, "minecraft:planks");
        assertEquals(List.of("kubejs:crafting_planks"), result.removed());
        assertFalse(result.content().contains("kubejs:crafting_planks"), result.content());
    }

    @Test
    void existingFileWithoutMarkersGetsAnAppendedBlock() {
        String manual = "ServerEvents.recipes(event => {\n  // hand written\n})\n";
        ScriptStore.MergeResult result = ScriptStore.merge(manual, stick(), WriteMode.SKIP, DeleteMode.NONE, "");
        assertEquals(ScriptStore.Outcome.CREATED, result.outcome());
        assertTrue(result.content().startsWith(manual), result.content());
        assertTrue(result.content().contains(ScriptStore.MARK_BEGIN), result.content());
        assertTrue(result.content().contains("// hand written"), result.content());
    }

    @Test
    void parseReadsBackMetadataAndText() {
        String content = ScriptStore.merge(null, stick(), WriteMode.SKIP, DeleteMode.NONE, "").content();
        int begin = content.indexOf(ScriptStore.MARK_BEGIN) + ScriptStore.MARK_BEGIN.length();
        int end = content.indexOf(ScriptStore.MARK_END);
        List<ScriptStore.Entry> entries = ScriptStore.parse(content.substring(begin, end));
        assertEquals(1, entries.size());
        ScriptStore.Entry parsed = entries.get(0);
        assertEquals("kubejs:crafting_stick", parsed.id());
        assertEquals("crafting", parsed.station());
        assertEquals("minecraft:stick x4", parsed.output());
        assertEquals("minecraft:oak_planks", parsed.inputs());
        assertTrue(parsed.text().contains("event.custom("), parsed.text());
    }

    @Test
    void writeIsAtomicAndRespectsTheSizeLimit(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(ScriptStore.RELATIVE_PATH);
        ScriptStore.write(file, "hello\n");
        assertEquals("hello\n", Files.readString(file));
        assertFalse(Files.exists(dir.resolve(ScriptStore.RELATIVE_PATH + ".tmp")));

        String huge = "x".repeat(ScriptStore.MAX_FILE_BYTES + 1);
        assertThrows(IOException.class, () -> ScriptStore.write(file, huge));
        assertEquals("hello\n", Files.readString(file));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
