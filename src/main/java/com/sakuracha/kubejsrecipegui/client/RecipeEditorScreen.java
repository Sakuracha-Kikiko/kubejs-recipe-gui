package com.sakuracha.kubejsrecipegui.client;

import com.sakuracha.kubejsrecipegui.codegen.RecipeCode;
import com.sakuracha.kubejsrecipegui.menu.RecipeEditorMenu;
import com.sakuracha.kubejsrecipegui.network.ReloadScriptsPayload;
import com.sakuracha.kubejsrecipegui.network.WriteRequest;
import com.sakuracha.kubejsrecipegui.network.WriteScriptPayload;
import com.sakuracha.kubejsrecipegui.server.ScriptStore;
import com.sakuracha.kubejsrecipegui.spec.DeleteMode;
import com.sakuracha.kubejsrecipegui.spec.IngredientSpec;
import com.sakuracha.kubejsrecipegui.spec.OutputSpec;
import com.sakuracha.kubejsrecipegui.spec.RecipeDraft;
import com.sakuracha.kubejsrecipegui.spec.Workstation;
import com.sakuracha.kubejsrecipegui.spec.WriteMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 配方编辑界面：左栏选工作站，中栏摆原料与产出，右栏三个标签页负责属性、生成与预览。
 *
 * <p>界面尺寸按原版 GUI 缩放设计：整块面板 470×250，在 GUI 缩放 3（640×360）下约占屏幕四分之三，
 * 缩放 4（480×270）也能完整显示；屏幕更小时按比例整体收缩。</p>
 */
public class RecipeEditorScreen extends AbstractContainerScreen<RecipeEditorMenu> {
    private static final int W = 470;
    private static final int H = 250;

    private static final int HEADER_H = 18;
    private static final int BODY_TOP = 22;
    private static final int BODY_BOTTOM = H - 6;

    private static final int LEFT_X = 5;
    private static final int LEFT_W = 104;
    private static final int MID_X = 114;
    private static final int MID_W = 196;
    private static final int RIGHT_X = 315;
    private static final int RIGHT_W = 150;

    private static final int SLOT = 28;
    private static final int FIELD_W = RIGHT_W - 12;

    private static final int COLOR_BG = 0xFF14161B;
    private static final int COLOR_PANEL = 0xFF1C1F26;
    private static final int COLOR_PANEL_ALT = 0xFF242832;
    private static final int COLOR_LINE = 0xFF373D4A;
    private static final int COLOR_LINE_SOFT = 0xFF2B3039;
    private static final int COLOR_TEXT = 0xFFE6E9EF;
    private static final int COLOR_MUTED = 0xFF98A1B0;
    private static final int COLOR_ACCENT = 0xFF5AA9FF;
    private static final int COLOR_GOOD = 0xFF74D68A;
    private static final int COLOR_BAD = 0xFFE5717B;
    private static final int COLOR_SLOT = 0xFF0D0F13;

    private static final int TAB_PROPERTIES = 0;
    private static final int TAB_GENERATE = 1;
    private static final int TAB_PREVIEW = 2;

    private static final int PICKER_W = 300;
    private static final int PICKER_H = 210;
    private static final int PICKER_COLUMNS = 10;
    private static final int PICKER_ROWS = 5;
    private static final int PICKER_CELL = 28;
    private static final int PICKER_PAGE = PICKER_COLUMNS * PICKER_ROWS;
    private static final int PICKER_LIMIT = 4096;

    private final RecipeDraft draft = RecipeDraft.of(Workstation.CRAFTING);
    private final List<EditBox> boxes = new ArrayList<>();

    private int tab = TAB_PROPERTIES;
    /** {@code -1} 表示产出格，{@code >= 0} 表示第几个原料格。 */
    private int selected = 0;
    private boolean syncing;
    private int previewScroll;

    private boolean pickerOpen;
    private int pickerTarget;
    private String pickerQuery = "";
    private int pickerPage;
    private List<String> pickerMatches = List.of();

    private EditBox countBox;
    private EditBox experienceBox;
    private EditBox timeBox;
    private EditBox itemBox;
    private EditBox tagBox;
    private EditBox componentsBox;
    private EditBox idBox;
    private EditBox deleteValueBox;
    private EditBox pickerSearch;

    private float zoom = 1.0F;
    private int originX;
    private int originY;

    private String statusKey;
    private Object[] statusArgs = new Object[0];
    private boolean statusGood;

    public RecipeEditorScreen(RecipeEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        this.boxes.clear();

        this.countBox = this.createBox(40, 2, this::onCountChanged,
                value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        this.experienceBox = this.createBox(40, 8, this::onExperienceChanged,
                value -> value.isEmpty() || value.chars().allMatch(c -> Character.isDigit(c) || c == '.'));
        this.timeBox = this.createBox(40, 5, this::onTimeChanged,
                value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        this.itemBox = this.createBox(FIELD_W, 128, this::onItemChanged, null);
        this.tagBox = this.createBox(FIELD_W, 128, this::onTagChanged, null);
        this.componentsBox = this.createBox(FIELD_W, 512, this::onComponentsChanged, null);
        this.idBox = this.createBox(FIELD_W, 128, value -> this.draft.recipeId = value, null);
        this.deleteValueBox = this.createBox(FIELD_W, 128, value -> this.draft.deleteValue = value, null);
        this.pickerSearch = this.createBox(PICKER_W - 12, 64, this::onPickerQueryChanged, null);

        this.layout();
    }

    /** 面板在小屏幕上整体收缩，避免 GUI 缩放偏大时看不全。 */
    private void updateGeometry() {
        float fitX = (this.width - 6) / (float) W;
        float fitY = (this.height - 6) / (float) H;
        float nextZoom = Math.min(1.0F, Math.min(fitX, fitY));
        int nextX = Math.round((this.width - W * nextZoom) / 2.0F);
        int nextY = Math.round((this.height - H * nextZoom) / 2.0F);
        boolean changed = nextZoom != this.zoom || nextX != this.originX || nextY != this.originY;
        this.zoom = nextZoom;
        this.originX = nextX;
        this.originY = nextY;
        if (changed && this.countBox != null) {
            this.layout();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.updateGeometry();
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int localX = this.toLocalX(mouseX);
        int localY = this.toLocalY(mouseY);

        graphics.pose().pushPose();
        graphics.pose().translate(this.originX, this.originY, 0.0F);
        graphics.pose().scale(this.zoom, this.zoom, 1.0F);
        this.drawChrome(graphics);
        this.drawStationColumn(graphics, localX, localY);
        this.drawRecipeArea(graphics, localX, localY);
        this.drawSidePanel(graphics, localX, localY);
        if (this.pickerOpen) {
            this.drawPicker(graphics, localX, localY);
        }
        graphics.pose().popPose();

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }

        if (!this.pickerOpen) {
            ItemStack hovered = this.hoveredStack(localX, localY);
            if (!hovered.isEmpty()) {
                graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 背景与面板全部在 render 中自绘。
    }

    // ------------------------------------------------------------------
    // 布局与输入控件
    // ------------------------------------------------------------------

    private EditBox createBox(int width, int maxLength, Consumer<String> responder, Predicate<String> filter) {
        EditBox box = new EditBox(this.font, 0, 0, width, 12, Component.empty());
        box.setMaxLength(maxLength);
        if (filter != null) {
            box.setFilter(filter);
        }
        box.setResponder(value -> {
            if (!this.syncing) {
                responder.accept(value);
            }
        });
        box.setTextColor(COLOR_TEXT);
        this.addRenderableWidget(box);
        this.boxes.add(box);
        return box;
    }

    /** 根据当前标签页、工作站与选中格，决定每个输入框的位置与可见性。 */
    private void layout() {
        if (this.countBox == null) {
            return;
        }
        this.updateGeometry();
        boolean properties = this.tab == TAB_PROPERTIES;
        boolean generate = this.tab == TAB_GENERATE;
        boolean outputSelected = this.selected < 0;
        boolean cooking = this.draft.station.isCooking();

        for (EditBox box : this.boxes) {
            box.setVisible(false);
            box.active = false;
        }

        if (this.pickerOpen) {
            this.place(this.pickerSearch, this.pickerX() + 6, this.pickerY() + 30, PICKER_W - 12);
            this.visible(this.pickerSearch, true);
            this.setFocused(this.pickerSearch);
            return;
        }

        int rx = RIGHT_X + 6;
        this.place(this.countBox, RIGHT_X + RIGHT_W - 6 - 44, 38, 44);
        this.place(this.experienceBox, RIGHT_X + RIGHT_W - 6 - 44, 56, 44);
        this.place(this.timeBox, RIGHT_X + RIGHT_W - 6 - 44, 74, 44);
        this.place(this.itemBox, rx, 108, FIELD_W);
        this.place(this.tagBox, rx, 142, FIELD_W);
        this.place(this.componentsBox, rx, 142, FIELD_W);
        this.place(this.idBox, rx, 48, FIELD_W);
        this.place(this.deleteValueBox, rx, 166, FIELD_W);

        if (properties) {
            this.visible(this.countBox, outputSelected);
            this.visible(this.experienceBox, cooking && outputSelected);
            this.visible(this.timeBox, cooking && outputSelected);
            this.visible(this.itemBox, true);
            this.visible(this.tagBox, !outputSelected);
            this.visible(this.componentsBox, outputSelected);
        } else if (generate) {
            this.visible(this.idBox, true);
            this.visible(this.deleteValueBox, this.draft.deleteMode.needsValue());
        }

        this.setValue(this.countBox, String.valueOf(this.draft.output.count));
        this.setValue(this.experienceBox, trimFloat(this.draft.experience));
        this.setValue(this.timeBox, String.valueOf(this.draft.cookingTime));
        this.setValue(this.itemBox, outputSelected ? this.draft.output.itemId : this.draft.input(this.selected).itemId);
        this.setValue(this.tagBox, outputSelected ? "" : this.draft.input(this.selected).tagId);
        this.setValue(this.componentsBox, this.draft.output.components);
        this.setValue(this.idBox, this.draft.recipeId);
        this.setValue(this.deleteValueBox, this.draft.deleteValue);
    }

    private void place(EditBox box, int logicalX, int logicalY, int logicalWidth) {
        box.setX(Math.round(this.originX + logicalX * this.zoom));
        box.setY(Math.round(this.originY + logicalY * this.zoom));
        box.setWidth(Math.max(20, Math.round(logicalWidth * this.zoom)));
        box.setHeight(Math.max(10, Math.round(12 * this.zoom)));
    }

    private void visible(EditBox box, boolean visible) {
        box.setVisible(visible);
        box.active = visible;
        box.setEditable(visible);
    }

    private void setValue(EditBox box, String value) {
        if (!box.visible || box.getValue().equals(value)) {
            return;
        }
        this.syncing = true;
        box.setValue(value);
        this.syncing = false;
    }

    private void onCountChanged(String value) {
        this.draft.output.count = parseInt(value, this.draft.output.count);
    }

    private void onExperienceChanged(String value) {
        this.draft.experience = parseFloat(value, this.draft.experience);
    }

    private void onTimeChanged(String value) {
        this.draft.cookingTime = parseInt(value, this.draft.cookingTime);
    }

    private void onItemChanged(String value) {
        if (this.selected < 0) {
            this.draft.output.itemId = value.trim();
            return;
        }
        IngredientSpec spec = this.draft.input(this.selected);
        spec.itemId = value.trim();
        if (!spec.itemId.isEmpty()) {
            spec.tagId = "";
        }
    }

    private void onTagChanged(String value) {
        if (this.selected < 0) {
            return;
        }
        IngredientSpec spec = this.draft.input(this.selected);
        spec.tagId = value.trim();
        if (!spec.tagId.isEmpty()) {
            spec.itemId = "";
        }
    }

    private void onComponentsChanged(String value) {
        this.draft.output.components = value.trim();
    }

    private void onPickerQueryChanged(String value) {
        this.pickerQuery = value;
        this.pickerPage = 0;
        this.updatePicker();
    }

    // ------------------------------------------------------------------
    // 鼠标与键盘
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.dispatchBoxClick(mouseX, mouseY, button)) {
            return true;
        }

        int localX = this.toLocalX(mouseX);
        int localY = this.toLocalY(mouseY);

        if (this.pickerOpen) {
            if (this.handlePickerClick(localX, localY, button)) {
                return true;
            }
            this.closePicker();
            return true;
        }

        this.setFocused((GuiEventListener) null);

        if (this.tabHit(localX, localY) >= 0) {
            this.tab = this.tabHit(localX, localY);
            this.layout();
            return true;
        }

        Workstation[] stations = Workstation.values();
        for (int index = 0; index < stations.length; index++) {
            if (inside(LEFT_X + 4, this.stationY(index), LEFT_W - 8, 20, localX, localY)) {
                this.selectStation(stations[index]);
                return true;
            }
        }

        if (this.draft.station.isCrafting() && inside(MID_X + 8, BODY_TOP + 2, 62, 14, localX, localY)) {
            this.draft.shapeless = false;
            return true;
        }
        if (this.draft.station.isCrafting() && inside(MID_X + 74, BODY_TOP + 2, 62, 14, localX, localY)) {
            this.draft.shapeless = true;
            return true;
        }

        for (int index = 0; index < this.draft.station.slotCount(); index++) {
            if (inside(this.slotX(index), this.slotY(index), SLOT, SLOT, localX, localY)) {
                this.clickSlot(index, button);
                return true;
            }
        }
        if (inside(this.outputX(), this.outputY(), SLOT, SLOT, localX, localY)) {
            this.clickSlot(-1, button);
            return true;
        }

        if (this.tab == TAB_PROPERTIES) {
            if (inside(RIGHT_X + 6, 186, FIELD_W, 16, localX, localY)) {
                this.openPicker(this.selected);
                return true;
            }
            if (inside(RIGHT_X + 6, 206, FIELD_W, 16, localX, localY)) {
                this.clearSelected();
                return true;
            }
        }

        if (this.tab == TAB_GENERATE) {
            if (inside(RIGHT_X + 6, 98, 66, 16, localX, localY)) {
                this.draft.writeMode = WriteMode.REPLACE;
                return true;
            }
            if (inside(RIGHT_X + 78, 98, 66, 16, localX, localY)) {
                this.draft.writeMode = WriteMode.SKIP;
                return true;
            }
            if (inside(RIGHT_X + 6, 132, FIELD_W, 16, localX, localY)) {
                this.draft.deleteMode = this.draft.deleteMode.next();
                this.layout();
                return true;
            }
            if (inside(RIGHT_X + 6, 204, 92, 18, localX, localY)) {
                this.writeScript();
                return true;
            }
            if (inside(RIGHT_X + 102, 204, 42, 18, localX, localY)) {
                this.reloadScripts();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.setDragging(false);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.pickerOpen) {
            this.scrollPicker(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        if (this.tab == TAB_PREVIEW) {
            this.previewScroll = Math.max(0, this.previewScroll - (int) Math.signum(scrollY) * 2);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.pickerOpen && keyCode == 256) {
            this.closePicker();
            return true;
        }

        // 正在输入时把按键全部留在输入框里：ESC 先退出输入，其余键（含 E）交给输入框，
        // 返回 false 让字符继续走 charTyped 插入，同时避免父类把 E 当成"关闭界面"。
        if (this.getFocused() instanceof EditBox box && box.isFocused() && box.visible) {
            if (keyCode == 256) {
                this.setFocused((GuiEventListener) null);
                return true;
            }
            if (box.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return false;
        }

        // 没有在输入时，吞掉背包键，避免误触关闭界面；退出统一用 ESC。
        if (keyCode == 256) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean dispatchBoxClick(double mouseX, double mouseY, int button) {
        for (EditBox box : this.boxes) {
            if (!box.visible) {
                continue;
            }
            if (box.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(box);
                if (button == 0) {
                    this.setDragging(true);
                }
                return true;
            }
        }
        return false;
    }

    private int tabHit(int localX, int localY) {
        for (int index = 0; index < 3; index++) {
            if (inside(RIGHT_X + index * 48, BODY_TOP, 46, 14, localX, localY)) {
                return index;
            }
        }
        return -1;
    }

    private void clickSlot(int index, int button) {
        if (button == 1) {
            if (index < 0) {
                this.draft.output.itemId = "";
                this.draft.output.components = "";
            } else {
                this.draft.input(index).clear();
            }
            this.selected = index;
            this.layout();
            return;
        }

        this.selected = index;
        ItemStack carried = this.menu.getCarried();
        if (carried.isEmpty()) {
            this.layout();
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (index < 0) {
            this.draft.output.itemId = id.toString();
            this.draft.output.count = Math.max(1, carried.getCount());
        } else {
            IngredientSpec spec = this.draft.input(index);
            spec.itemId = id.toString();
            spec.tagId = "";
        }
        this.layout();
    }

    private void clearSelected() {
        if (this.selected < 0) {
            this.draft.output = new OutputSpec("", 1, "");
        } else {
            this.draft.input(this.selected).clear();
        }
        this.layout();
    }

    private void selectStation(Workstation station) {
        this.draft.station(station);
        this.selected = Math.max(-1, Math.min(this.selected, station.slotCount() - 1));
        this.layout();
    }

    // ------------------------------------------------------------------
    // 物品选择浮层
    // ------------------------------------------------------------------

    private int pickerX() {
        return (W - PICKER_W) / 2;
    }

    private int pickerY() {
        return (H - PICKER_H) / 2;
    }

    private void openPicker(int target) {
        this.pickerTarget = target;
        this.pickerOpen = true;
        this.pickerQuery = "";
        this.pickerPage = 0;
        this.syncing = true;
        this.pickerSearch.setValue("");
        this.syncing = false;
        this.updatePicker();
        this.layout();
    }

    private void closePicker() {
        this.pickerOpen = false;
        this.layout();
    }

    private void updatePicker() {
        String query = this.pickerQuery.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            this.pickerMatches = ItemIndex.all();
        } else {
            List<String> matches = new ArrayList<>();
            for (String id : ItemIndex.all()) {
                if (id.contains(query)) {
                    matches.add(id);
                    if (matches.size() >= PICKER_LIMIT) {
                        break;
                    }
                }
            }
            this.pickerMatches = matches;
        }
        int pages = Math.max(1, (this.pickerMatches.size() + PICKER_PAGE - 1) / PICKER_PAGE);
        this.pickerPage = Math.max(0, Math.min(this.pickerPage, pages - 1));
    }

    private void scrollPicker(int delta) {
        int pages = Math.max(1, (this.pickerMatches.size() + PICKER_PAGE - 1) / PICKER_PAGE);
        this.pickerPage = Math.max(0, Math.min(this.pickerPage + delta, pages - 1));
    }

    private boolean handlePickerClick(int localX, int localY, int button) {
        if (button == 1) {
            this.closePicker();
            return true;
        }
        if (!inside(this.pickerX(), this.pickerY(), PICKER_W, PICKER_H, localX, localY)) {
            return false;
        }
        int gridX = this.pickerX() + 8;
        int gridY = this.pickerY() + 44;
        for (int row = 0; row < PICKER_ROWS; row++) {
            for (int column = 0; column < PICKER_COLUMNS; column++) {
                int index = this.pickerPage * PICKER_PAGE + row * PICKER_COLUMNS + column;
                if (index >= this.pickerMatches.size()) {
                    return true;
                }
                int cellX = gridX + column * PICKER_CELL;
                int cellY = gridY + row * PICKER_CELL;
                if (inside(cellX, cellY, PICKER_CELL - 2, PICKER_CELL - 2, localX, localY)) {
                    this.applyPicked(this.pickerMatches.get(index));
                    return true;
                }
            }
        }
        return true;
    }

    private void applyPicked(String itemId) {
        if (this.pickerTarget < 0) {
            this.draft.output.itemId = itemId;
            if (this.draft.output.count < 1) {
                this.draft.output.count = 1;
            }
        } else {
            IngredientSpec spec = this.draft.input(this.pickerTarget);
            spec.itemId = itemId;
            spec.tagId = "";
        }
        this.closePicker();
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    private void drawChrome(GuiGraphics graphics) {
        graphics.fill(0, 0, W, H, COLOR_BG);
        graphics.renderOutline(0, 0, W, H, COLOR_LINE);
        graphics.fill(0, 0, W, HEADER_H, COLOR_PANEL_ALT);
        graphics.fill(0, HEADER_H - 1, W, HEADER_H, COLOR_LINE);
        graphics.drawString(this.font, this.title, 6, 5, COLOR_TEXT, false);

        List<RecipeCode.Problem> problems = RecipeCode.validate(this.draft);
        String hint = problems.isEmpty()
                ? Component.translatable("gui.kubejs_recipe_gui.label.empty_slot").getString()
                : Component.translatable(problems.get(0).key(), problems.get(0).args()).getString();
        int color = problems.isEmpty() ? COLOR_MUTED : COLOR_BAD;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(hint, 240),
                W - 6 - this.font.width(this.font.plainSubstrByWidth(hint, 240)), 5, color, false);
    }

    private int stationY(int index) {
        return BODY_TOP + 12 + index * 24;
    }

    private void drawStationColumn(GuiGraphics graphics, int mouseX, int mouseY) {
        panel(graphics, LEFT_X, BODY_TOP, LEFT_W, BODY_BOTTOM - BODY_TOP);
        label(graphics, "gui.kubejs_recipe_gui.label.station", LEFT_X + 5, BODY_TOP + 4);

        Workstation[] stations = Workstation.values();
        for (int index = 0; index < stations.length; index++) {
            Workstation station = stations[index];
            int x = LEFT_X + 4;
            int y = this.stationY(index);
            boolean active = this.draft.station == station;
            boolean hovered = inside(x, y, LEFT_W - 8, 20, mouseX, mouseY);
            graphics.fill(x, y, x + LEFT_W - 8, y + 20, active ? COLOR_PANEL_ALT : COLOR_PANEL);
            graphics.renderOutline(x, y, LEFT_W - 8, 20, active ? COLOR_ACCENT : (hovered ? COLOR_LINE : COLOR_LINE_SOFT));
            graphics.drawString(this.font, Component.translatable(station.langKey()), x + 5, y + 6,
                    active ? COLOR_TEXT : COLOR_MUTED, false);
        }
    }

    private void drawRecipeArea(GuiGraphics graphics, int mouseX, int mouseY) {
        panel(graphics, MID_X, BODY_TOP, MID_W, BODY_BOTTOM - BODY_TOP);
        Workstation station = this.draft.station;

        if (station.isCrafting()) {
            modeButton(graphics, MID_X + 8, BODY_TOP + 2, 62, "gui.kubejs_recipe_gui.button.shaped",
                    !this.draft.shapeless, mouseX, mouseY);
            modeButton(graphics, MID_X + 74, BODY_TOP + 2, 62, "gui.kubejs_recipe_gui.button.shapeless",
                    this.draft.shapeless, mouseX, mouseY);
        } else if (station.isSmithing()) {
            label(graphics, "gui.kubejs_recipe_gui.label.template", this.slotX(0), this.slotY(0) - 10);
            label(graphics, "gui.kubejs_recipe_gui.label.base", this.slotX(1), this.slotY(1) - 10);
            label(graphics, "gui.kubejs_recipe_gui.label.addition", this.slotX(2), this.slotY(2) - 10);
        } else {
            label(graphics, "gui.kubejs_recipe_gui.label.ingredient", this.slotX(0), this.slotY(0) - 10);
        }

        for (int index = 0; index < station.slotCount(); index++) {
            IngredientSpec spec = this.draft.input(index);
            this.drawSlot(graphics, this.slotX(index), this.slotY(index), mouseX, mouseY, this.selected == index, spec);
        }

        graphics.drawString(this.font, ">", this.outputX() - 12, this.outputY() + 10, COLOR_MUTED, false);

        int outX = this.outputX();
        int outY = this.outputY();
        label(graphics, "gui.kubejs_recipe_gui.label.output", outX, outY - 10);
        graphics.fill(outX, outY, outX + SLOT, outY + SLOT, COLOR_SLOT);
        graphics.renderOutline(outX, outY, SLOT, SLOT,
                this.selected < 0 ? COLOR_ACCENT : (inside(outX, outY, SLOT, SLOT, mouseX, mouseY) ? COLOR_LINE : COLOR_LINE_SOFT));
        ItemStack output = ItemIndex.stack(this.draft.output.itemId);
        if (!output.isEmpty()) {
            ItemStack shown = output.copy();
            shown.setCount(Math.max(1, this.draft.output.count));
            graphics.renderItem(shown, outX + 6, outY + 6);
            graphics.renderItemDecorations(this.font, shown, outX + 6, outY + 6);
        }

        String slotHint = this.font.plainSubstrByWidth(
                Component.translatable("gui.kubejs_recipe_gui.hint.slot").getString(), MID_W - 10);
        graphics.drawString(this.font, slotHint, MID_X + 5, H - 15, COLOR_MUTED, false);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                          boolean selected, IngredientSpec spec) {
        graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_SLOT);
        boolean hovered = inside(x, y, SLOT, SLOT, mouseX, mouseY);
        graphics.renderOutline(x, y, SLOT, SLOT, selected ? COLOR_ACCENT : (hovered ? COLOR_LINE : COLOR_LINE_SOFT));

        if (spec.isTag()) {
            String tag = "#" + shortId(spec.tagId);
            graphics.drawString(this.font, tag, x + 1, y + 10, COLOR_ACCENT, false);
            return;
        }
        ItemStack stack = ItemIndex.stack(spec.itemId);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 6, y + 6);
        }
    }

    private void drawSidePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        panel(graphics, RIGHT_X, BODY_TOP, RIGHT_W, BODY_BOTTOM - BODY_TOP);

        String[] tabs = {
                "gui.kubejs_recipe_gui.tab.properties",
                "gui.kubejs_recipe_gui.tab.generate",
                "gui.kubejs_recipe_gui.tab.preview"
        };
        for (int index = 0; index < tabs.length; index++) {
            int x = RIGHT_X + index * 48;
            boolean active = this.tab == index;
            boolean hovered = inside(x, BODY_TOP, 46, 14, mouseX, mouseY);
            graphics.fill(x, BODY_TOP, x + 46, BODY_TOP + 14, active ? COLOR_PANEL_ALT : COLOR_PANEL);
            graphics.renderOutline(x, BODY_TOP, 46, 14, active ? COLOR_ACCENT : (hovered ? COLOR_LINE : COLOR_LINE_SOFT));
            String text = Component.translatable(tabs[index]).getString();
            graphics.drawString(this.font, text, x + (46 - this.font.width(text)) / 2, BODY_TOP + 3,
                    active ? COLOR_TEXT : COLOR_MUTED, false);
        }

        if (this.tab == TAB_PROPERTIES) {
            this.drawProperties(graphics, mouseX, mouseY);
        } else if (this.tab == TAB_GENERATE) {
            this.drawGenerate(graphics, mouseX, mouseY);
        } else {
            this.drawPreview(graphics);
        }
    }

    private void drawProperties(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean outputSelected = this.selected < 0;
        boolean cooking = this.draft.station.isCooking();

        label(graphics, "gui.kubejs_recipe_gui.label.count", RIGHT_X + 6, 41);
        label(graphics, "gui.kubejs_recipe_gui.label.experience", RIGHT_X + 6, 59);
        label(graphics, "gui.kubejs_recipe_gui.label.time", RIGHT_X + 6, 77);
        if (!cooking) {
            graphics.drawString(this.font, "-", RIGHT_X + RIGHT_W - 24, 59, COLOR_MUTED, false);
            graphics.drawString(this.font, "-", RIGHT_X + RIGHT_W - 24, 77, COLOR_MUTED, false);
        } else if (!outputSelected) {
            graphics.drawString(this.font, "-", RIGHT_X + RIGHT_W - 24, 41, COLOR_MUTED, false);
        }

        label(graphics, outputSelected ? "gui.kubejs_recipe_gui.label.item" : "gui.kubejs_recipe_gui.label.ingredient",
                RIGHT_X + 6, 97);
        if (!outputSelected) {
            label(graphics, "gui.kubejs_recipe_gui.label.tag", RIGHT_X + 6, 131);
        } else {
            label(graphics, "gui.kubejs_recipe_gui.label.components", RIGHT_X + 6, 131);
        }

        this.drawButton(graphics, RIGHT_X + 6, 186, FIELD_W, 16,
                Component.translatable("gui.kubejs_recipe_gui.button.select_item"), mouseX, mouseY);
        this.drawButton(graphics, RIGHT_X + 6, 206, FIELD_W, 16,
                Component.translatable("gui.kubejs_recipe_gui.button.clear"), mouseX, mouseY);
    }

    private void drawGenerate(GuiGraphics graphics, int mouseX, int mouseY) {
        label(graphics, "gui.kubejs_recipe_gui.label.recipe_id", RIGHT_X + 6, 37);
        graphics.drawString(this.font, Component.translatable("gui.kubejs_recipe_gui.hint.recipe_id").getString(),
                RIGHT_X + 6, 66, COLOR_MUTED, false);

        label(graphics, "gui.kubejs_recipe_gui.label.write_mode", RIGHT_X + 6, 85);
        this.drawButton(graphics, RIGHT_X + 6, 98, 66, 16,
                Component.translatable("gui.kubejs_recipe_gui.button.overwrite"), mouseX, mouseY,
                this.draft.writeMode == WriteMode.REPLACE);
        this.drawButton(graphics, RIGHT_X + 78, 98, 66, 16,
                Component.translatable("gui.kubejs_recipe_gui.button.skip"), mouseX, mouseY,
                this.draft.writeMode == WriteMode.SKIP);

        label(graphics, "gui.kubejs_recipe_gui.label.delete", RIGHT_X + 6, 121);
        this.drawButton(graphics, RIGHT_X + 6, 132, FIELD_W, 16,
                Component.translatable(this.draft.deleteMode.langKey()), mouseX, mouseY);

        if (this.draft.deleteMode.needsValue()) {
            label(graphics, "gui.kubejs_recipe_gui.label.delete_value", RIGHT_X + 6, 155);
        }

        if (this.statusKey != null) {
            String text = Component.translatable(this.statusKey, this.statusArgs).getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(text, RIGHT_W - 12),
                    RIGHT_X + 6, 188, this.statusGood ? COLOR_GOOD : COLOR_BAD, false);
        }

        this.drawButton(graphics, RIGHT_X + 6, 204, 92, 18,
                Component.translatable("gui.kubejs_recipe_gui.button.write"), mouseX, mouseY);
        this.drawButton(graphics, RIGHT_X + 102, 204, 42, 18,
                Component.translatable("gui.kubejs_recipe_gui.button.reload"), mouseX, mouseY);
    }

    private void drawPreview(GuiGraphics graphics) {
        int x = RIGHT_X + 6;
        int y = BODY_TOP + 20;
        int width = RIGHT_W - 12;
        int height = BODY_BOTTOM - y - 4;
        graphics.fill(x, y, x + width, y + height, COLOR_SLOT);
        graphics.renderOutline(x, y, width, height, COLOR_LINE_SOFT);

        String[] lines = this.previewText().split("\n", -1);
        int lineHeight = 9;
        int maxLines = (height - 4) / lineHeight;
        int start = Math.min(this.previewScroll, Math.max(0, lines.length - maxLines));

        graphics.enableScissor(
                Math.round(this.originX + (x + 1) * this.zoom),
                Math.round(this.originY + (y + 1) * this.zoom),
                Math.round(this.originX + (x + width - 1) * this.zoom),
                Math.round(this.originY + (y + height - 1) * this.zoom));
        for (int index = 0; index < maxLines && start + index < lines.length; index++) {
            String line = lines[start + index];
            String fitted = this.font.width(line) > width - 6
                    ? this.font.plainSubstrByWidth(line, width - 6)
                    : line;
            graphics.drawString(this.font, fitted, x + 3, y + 3 + index * lineHeight, COLOR_TEXT, false);
        }
        graphics.disableScissor();

        int scrollable = Math.max(0, lines.length - maxLines);
        this.previewScroll = Math.min(this.previewScroll, scrollable);
    }

    private void drawPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, W, H, 0xB0000000);
        int x = this.pickerX();
        int y = this.pickerY();
        graphics.fill(x, y, x + PICKER_W, y + PICKER_H, COLOR_PANEL);
        graphics.renderOutline(x, y, PICKER_W, PICKER_H, COLOR_ACCENT);
        graphics.drawString(this.font, Component.translatable("gui.kubejs_recipe_gui.picker.title"),
                x + 6, y + 10, COLOR_TEXT, false);

        int pages = Math.max(1, (this.pickerMatches.size() + PICKER_PAGE - 1) / PICKER_PAGE);
        String page = Component.translatable("gui.kubejs_recipe_gui.picker.page",
                String.valueOf(this.pickerPage + 1), String.valueOf(pages)).getString();
        graphics.drawString(this.font, page, x + PICKER_W - 8 - this.font.width(page), y + 10, COLOR_MUTED, false);

        if (this.pickerMatches.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.kubejs_recipe_gui.picker.empty"),
                    x + 6, y + PICKER_H / 2, COLOR_MUTED, false);
            return;
        }

        int gridX = x + 8;
        int gridY = y + 44;
        for (int row = 0; row < PICKER_ROWS; row++) {
            for (int column = 0; column < PICKER_COLUMNS; column++) {
                int index = this.pickerPage * PICKER_PAGE + row * PICKER_COLUMNS + column;
                if (index >= this.pickerMatches.size()) {
                    return;
                }
                int cellX = gridX + column * PICKER_CELL;
                int cellY = gridY + row * PICKER_CELL;
                boolean hovered = inside(cellX, cellY, PICKER_CELL - 2, PICKER_CELL - 2, mouseX, mouseY);
                graphics.fill(cellX, cellY, cellX + PICKER_CELL - 2, cellY + PICKER_CELL - 2, COLOR_SLOT);
                graphics.renderOutline(cellX, cellY, PICKER_CELL - 2, PICKER_CELL - 2,
                        hovered ? COLOR_ACCENT : COLOR_LINE_SOFT);
                ItemStack stack = ItemIndex.stack(this.pickerMatches.get(index));
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, cellX + 5, cellY + 5);
                }
            }
        }
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, int height, Component text,
                            int mouseX, int mouseY) {
        this.drawButton(graphics, x, y, width, height, text, mouseX, mouseY, false);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int width, int height, Component text,
                            int mouseX, int mouseY, boolean active) {
        boolean hovered = inside(x, y, width, height, mouseX, mouseY);
        graphics.fill(x, y, x + width, y + height, hovered ? COLOR_PANEL_ALT : COLOR_PANEL);
        graphics.renderOutline(x, y, width, height, active ? COLOR_ACCENT : (hovered ? COLOR_LINE : COLOR_LINE_SOFT));
        String label = text.getString();
        graphics.drawString(this.font, label, x + (width - this.font.width(label)) / 2, y + (height - 8) / 2,
                active ? COLOR_TEXT : COLOR_MUTED, false);
    }

    private void modeButton(GuiGraphics graphics, int x, int y, int width, String key, boolean active,
                            int mouseX, int mouseY) {
        this.drawButton(graphics, x, y, width, 14, Component.translatable(key), mouseX, mouseY, active);
    }

    private void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
        graphics.renderOutline(x, y, width, height, COLOR_LINE_SOFT);
    }

    private void label(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(this.font, Component.translatable(key), x, y, COLOR_MUTED, false);
    }

    // ------------------------------------------------------------------
    // 几何与取值
    // ------------------------------------------------------------------

    private int slotX(int index) {
        Workstation station = this.draft.station;
        if (station.isCrafting()) {
            return MID_X + 14 + (index % 3) * 32;
        }
        if (station.isSmithing()) {
            return MID_X + 12 + index * 40;
        }
        return MID_X + 40;
    }

    private int slotY(int index) {
        Workstation station = this.draft.station;
        if (station.isCrafting()) {
            return BODY_TOP + 26 + (index / 3) * 32;
        }
        if (station.isSmithing()) {
            return BODY_TOP + 44;
        }
        return BODY_TOP + 36;
    }

    private int outputX() {
        if (this.draft.station.isCrafting()) {
            return MID_X + 132;
        }
        if (this.draft.station.isSmithing()) {
            return MID_X + 148;
        }
        return MID_X + 132;
    }

    private int outputY() {
        if (this.draft.station.isCrafting()) {
            return BODY_TOP + 58;
        }
        if (this.draft.station.isSmithing()) {
            return BODY_TOP + 44;
        }
        return BODY_TOP + 36;
    }

    private ItemStack hoveredStack(int mouseX, int mouseY) {
        for (int index = 0; index < this.draft.station.slotCount(); index++) {
            if (inside(this.slotX(index), this.slotY(index), SLOT, SLOT, mouseX, mouseY)) {
                IngredientSpec spec = this.draft.input(index);
                return spec.isTag() ? ItemStack.EMPTY : ItemIndex.stack(spec.itemId);
            }
        }
        if (inside(this.outputX(), this.outputY(), SLOT, SLOT, mouseX, mouseY)) {
            return ItemIndex.stack(this.draft.output.itemId);
        }
        return ItemStack.EMPTY;
    }

    private String previewText() {
        List<RecipeCode.Problem> problems = RecipeCode.validate(this.draft);
        StringBuilder builder = new StringBuilder();
        builder.append("// -> ").append(ScriptStore.RELATIVE_PATH).append('\n');
        if (!problems.isEmpty()) {
            for (RecipeCode.Problem problem : problems) {
                builder.append("// ").append(Component.translatable(problem.key(), problem.args()).getString())
                        .append('\n');
            }
            return builder.toString();
        }
        String id = this.draft.recipeId.isBlank() ? RecipeCode.autoId(this.draft) : this.draft.recipeId;
        builder.append(RecipeCode.entry(this.draft, id));
        return builder.toString();
    }

    private void writeScript() {
        List<RecipeCode.Problem> problems = RecipeCode.validate(this.draft);
        if (!problems.isEmpty()) {
            RecipeCode.Problem problem = problems.get(0);
            this.setStatus(false, problem.key(), problem.args());
            return;
        }

        String recipeJson = RecipeCode.recipeJson(this.draft);
        WriteRequest request = new WriteRequest(
                recipeJson,
                this.draft.station.key(),
                RecipeCode.outputSummary(this.draft),
                RecipeCode.inputsSummary(this.draft),
                this.draft.recipeId,
                this.draft.writeMode.key(),
                this.draft.deleteMode.key(),
                this.draft.deleteValue);
        PacketDistributor.sendToServer(new WriteScriptPayload(WriteRequest.encode(request)));

        String id = this.draft.recipeId.isBlank() ? RecipeCode.autoId(this.draft) : this.draft.recipeId;
        this.setStatus(true, "gui.kubejs_recipe_gui.msg.sent", id);
    }

    private void reloadScripts() {
        PacketDistributor.sendToServer(new ReloadScriptsPayload());
        this.setStatus(true, "gui.kubejs_recipe_gui.msg.reloading");
    }

    private void setStatus(boolean good, String key, Object... args) {
        this.statusGood = good;
        this.statusKey = key;
        this.statusArgs = args;
    }

    private int toLocalX(double mouseX) {
        return (int) Math.floor((mouseX - this.originX) / this.zoom);
    }

    private int toLocalY(double mouseY) {
        return (int) Math.floor((mouseY - this.originY) / this.zoom);
    }

    private static boolean inside(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value.isEmpty() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return value.isEmpty() ? fallback : Float.parseFloat(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String trimFloat(float value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String shortId(String value) {
        int colon = value.indexOf(':');
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        return path.length() > 12 ? path.substring(0, 12) : path;
    }
}
