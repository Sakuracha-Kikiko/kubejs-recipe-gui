package com.sakuracha.kubejsrecipegui.codegen;

import com.sakuracha.kubejsrecipegui.spec.DeleteMode;
import com.sakuracha.kubejsrecipegui.spec.IngredientSpec;
import com.sakuracha.kubejsrecipegui.spec.OutputSpec;
import com.sakuracha.kubejsrecipegui.spec.RecipeDraft;
import com.sakuracha.kubejsrecipegui.spec.Workstation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 把 {@link RecipeDraft} 变成可直接写入 KubeJS 脚本的文本。
 * 全部方法都是纯函数，不触碰游戏状态，因此可以在单元测试里直接验证。
 */
public final class RecipeCode {
    /** 单条配方文本的字节上限，避免把脚本撑得过大。 */
    public static final int MAX_BYTES = 64 * 1024;
    /** 配方 ID 与物品 ID 都使用 namespace:path 形式。 */
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final String SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private RecipeCode() {
    }

    /** 校验问题：{@code key} 是语言文件键，{@code args} 原样传给格式化。 */
    public record Problem(String key, Object... args) {
    }

    public static List<Problem> validate(RecipeDraft draft) {
        List<Problem> problems = new ArrayList<>();
        OutputSpec output = draft.output;

        if (output.isEmpty()) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.output_empty"));
        } else if (!isValidId(output.itemId)) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.item_invalid", output.itemId));
        }
        if (output.count < 1 || output.count > 99) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.count_range"));
        }
        if (!output.components.isBlank() && !looksLikeObject(output.components)) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.components_invalid"));
        }
        if (!draft.recipeId.isBlank() && !isValidId(draft.recipeId)) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.id_invalid"));
        }

        for (int i = 0; i < draft.inputs.size(); i++) {
            IngredientSpec input = draft.inputs.get(i);
            if (input.isMixed()) {
                problems.add(new Problem("gui.kubejs_recipe_gui.error.ingredient_mixed", i + 1));
            } else if (!input.isEmpty()) {
                String value = input.isTag() ? input.tagCore() : input.itemId;
                if (!isValidId(value)) {
                    problems.add(new Problem("gui.kubejs_recipe_gui.error.item_invalid", value));
                }
            } else if (draft.station.isSmithing()) {
                problems.add(new Problem("gui.kubejs_recipe_gui.error.smithing_three"));
                break;
            }
        }

        if (draft.station.isCrafting() && filledInputs(draft).isEmpty()) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.crafting_needs_input"));
        }
        if (draft.station.singleInput() && draft.input(0).isEmpty()) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.ingredient_empty", 1));
        }
        if (draft.station.isCooking() && (draft.cookingTime < 1 || draft.cookingTime > 72000)) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.time_range"));
        }
        if (recipeJson(draft).getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add(new Problem("gui.kubejs_recipe_gui.error.too_large"));
        }
        return problems;
    }

    /** 生成紧凑 JSON，作为自动 ID 的哈希输入。 */
    public static String recipeJson(RecipeDraft draft) {
        Workstation station = draft.station;
        if (station.isCrafting()) {
            return draft.shapeless ? shapelessJson(draft) : shapedJson(draft);
        }
        if (station.isCooking()) {
            String type = switch (station) {
                case BLAST_FURNACE -> "minecraft:blasting";
                case SMOKER -> "minecraft:smoking";
                case CAMPFIRE -> "minecraft:campfire_cooking";
                default -> "minecraft:smelting";
            };
            return "{\"type\":" + JsonText.quote(type)
                    + ",\"ingredient\":" + ingredientJson(draft.input(0))
                    + ",\"result\":" + resultJson(draft.output)
                    + ",\"experience\":" + number(draft.experience)
                    + ",\"cookingtime\":" + draft.cookingTime + "}";
        }
        if (station == Workstation.STONECUTTING) {
            return "{\"type\":\"minecraft:stonecutting\""
                    + ",\"ingredient\":" + ingredientJson(draft.input(0))
                    + ",\"result\":" + resultJson(draft.output) + "}";
        }
        return "{\"type\":\"minecraft:smithing_transform\""
                + ",\"template\":" + ingredientJson(draft.input(0))
                + ",\"base\":" + ingredientJson(draft.input(1))
                + ",\"addition\":" + ingredientJson(draft.input(2))
                + ",\"result\":" + resultJson(draft.output) + "}";
    }

    public static String prettyJson(RecipeDraft draft) {
        return JsonText.pretty(recipeJson(draft));
    }

    /** 显式填写了 ID 就用它，否则按配方内容哈希生成。 */
    public static String resolveId(RecipeDraft draft) {
        return draft.recipeId.isBlank() ? autoId(draft) : draft.recipeId;
    }

    public static String autoId(RecipeDraft draft) {
        return autoId(draft.station.key(), recipeJson(draft));
    }

    /** 服务端只拿到 JSON 时用它生成稳定的自动 ID。 */
    public static String autoId(String stationKey, String recipeJson) {
        return "kubejs:" + stationKey + "_" + hash8(recipeJson);
    }

    /** 带元数据注释的完整脚本片段，注释用于按条件删除与人工阅读。 */
    public static String entry(RecipeDraft draft, String id) {
        return entryFromJson(recipeJson(draft), id, draft.station.key(), outputSummary(draft), inputsSummary(draft));
    }

    /**
     * 由紧凑 JSON 与元数据拼出脚本片段。服务端重新拼装时复用这里，避免两边格式漂移。
     */
    public static String entryFromJson(String recipeJson, String id, String station,
                                       String output, String inputs) {
        return "// id: " + id + "\n"
                + "// station: " + station + "\n"
                + "// output: " + output + "\n"
                + "// inputs: " + inputs + "\n"
                + "event.custom(" + JsonText.pretty(recipeJson) + ").id(" + JsonText.quote(id) + ");\n";
    }

    public static String outputSummary(RecipeDraft draft) {
        return draft.output.display();
    }

    public static String inputsSummary(RecipeDraft draft) {
        List<String> parts = new ArrayList<>();
        for (IngredientSpec input : draft.inputs) {
            if (!input.isEmpty()) {
                parts.add(input.display());
            }
        }
        return String.join(", ", parts);
    }

    /** 删除条件是否已经填好；未填时调用方应阻止写入。 */
    public static boolean deleteTargetFilled(RecipeDraft draft) {
        return draft.deleteMode == DeleteMode.NONE
                || !draft.deleteMode.needsValue()
                || !draft.deleteValue.isBlank();
    }

    private static String shapedJson(RecipeDraft draft) {
        IngredientSpec[] cells = new IngredientSpec[9];
        for (int i = 0; i < 9; i++) {
            cells[i] = draft.input(i);
        }
        int minRow = 3;
        int maxRow = -1;
        int minCol = 3;
        int maxCol = -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (!cells[row * 3 + col].isEmpty()) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                }
            }
        }
        if (maxRow < 0) {
            return "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[],\"key\":{},\"result\":"
                    + resultJson(draft.output) + "}";
        }

        Map<String, Character> symbols = new LinkedHashMap<>();
        StringBuilder pattern = new StringBuilder("[");
        for (int row = minRow; row <= maxRow; row++) {
            if (row > minRow) {
                pattern.append(',');
            }
            StringBuilder line = new StringBuilder("\"");
            for (int col = minCol; col <= maxCol; col++) {
                IngredientSpec cell = cells[row * 3 + col];
                if (cell.isEmpty()) {
                    line.append(' ');
                } else {
                    Character symbol = symbols.get(cell.display());
                    if (symbol == null) {
                        symbol = SYMBOLS.charAt(symbols.size());
                        symbols.put(cell.display(), symbol);
                    }
                    line.append(symbol);
                }
            }
            line.append('"');
            pattern.append(line);
        }
        pattern.append(']');

        StringBuilder key = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Character> entry : symbols.entrySet()) {
            if (!first) {
                key.append(',');
            }
            first = false;
            IngredientSpec source = find(cells, entry.getKey());
            key.append(JsonText.quote(String.valueOf(entry.getValue())))
                    .append(':')
                    .append(ingredientJson(source));
        }
        key.append('}');

        return "{\"type\":\"minecraft:crafting_shaped\""
                + ",\"pattern\":" + pattern
                + ",\"key\":" + key
                + ",\"result\":" + resultJson(draft.output) + "}";
    }

    private static String shapelessJson(RecipeDraft draft) {
        StringBuilder ingredients = new StringBuilder("[");
        boolean first = true;
        for (IngredientSpec input : draft.inputs) {
            if (input.isEmpty()) {
                continue;
            }
            if (!first) {
                ingredients.append(',');
            }
            first = false;
            ingredients.append(ingredientJson(input));
        }
        ingredients.append(']');
        return "{\"type\":\"minecraft:crafting_shapeless\""
                + ",\"ingredients\":" + ingredients
                + ",\"result\":" + resultJson(draft.output) + "}";
    }

    private static IngredientSpec find(IngredientSpec[] cells, String display) {
        for (IngredientSpec cell : cells) {
            if (!cell.isEmpty() && cell.display().equals(display)) {
                return cell;
            }
        }
        return new IngredientSpec();
    }

    private static String ingredientJson(IngredientSpec input) {
        if (input.isTag()) {
            return "{\"tag\":" + JsonText.quote(input.tagCore()) + "}";
        }
        return "{\"item\":" + JsonText.quote(input.itemId) + "}";
    }

    private static String resultJson(OutputSpec output) {
        StringBuilder sb = new StringBuilder("{\"id\":").append(JsonText.quote(output.itemId));
        sb.append(",\"count\":").append(Math.max(1, output.count));
        if (!output.components.isBlank()) {
            sb.append(",\"components\":").append(output.components);
        }
        sb.append('}');
        return sb.toString();
    }

    private static List<IngredientSpec> filledInputs(RecipeDraft draft) {
        List<IngredientSpec> filled = new ArrayList<>();
        for (IngredientSpec input : draft.inputs) {
            if (!input.isEmpty()) {
                filled.add(input);
            }
        }
        return filled;
    }

    private static boolean looksLikeObject(String value) {
        String trimmed = value.trim();
        return trimmed.length() >= 2 && trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    public static boolean isValidId(String value) {
        return value != null && ID_PATTERN.matcher(value).matches();
    }

    private static String number(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "");
            if (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1);
            }
        }
        return text;
    }

    private static String hash8(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
