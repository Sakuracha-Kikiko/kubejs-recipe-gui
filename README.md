# KubeJS Recipe GUI

A NeoForge mod that adds an intuitive GUI to easily edit KubeJS recipes.

在游戏里放一个「配方编辑器」方块，像工作台一样摆原料、填产出，点一下就把配方写进
`kubejs/server_scripts/generated_recipes.js`；确认无误后点界面上的「重载」按钮即可生效。
不需要手写 JS，也不需要退出游戏。

- Minecraft 1.21.1 / NeoForge 21.1.216+ / Java 21
- 前置：KubeJS 2101.7+（JEI 可选，用于以后的配方导入）
- 行为约定见 [docs/SPEC.md](docs/SPEC.md)

## 当前进度

M1 已完成：方块与界面骨架、原版 7 类配方（有序/无序合成、熔炉、高炉、烟熏炉、营火、切石、锻造）、
代码生成与预览、写入脚本与权限控制。

M1 已验证：

- `gradle build` 通过，产物 `build/libs/kubejs_recipe_gui-0.1.0.jar`；
- `RecipeCode` / `ScriptStore` 的 22 个单元测试全部通过；
- 用生成的脚本启动开发服务器，KubeJS 日志为
  `Loaded script server_scripts:generated_recipes.js ... with 0 errors` 与
  `Added 4 recipes, removed 0 recipes ... with 0 failed recipes`。

2026-11-01 界面调整（根据实机反馈）：

- 面板从 600×340 缩小到 470×250，GUI 缩放 3 下不再占满屏幕，缩放 4 也能完整显示；
  更小的屏幕会整体等比收缩；
- 输入框获得焦点时按 E 只输入字符，不再关闭界面（退出统一用 ESC）；
- 写入脚本后不再自动 `/reload`，改由「重载」按钮手动触发，避免写完立刻重载造成的卡顿与误操作。

## 构建

```bash
./gradlew build -x test      # 打包（测试见下）
./gradlew runClient          # 开发客户端
```

测试用的是纯逻辑代码，不依赖游戏，可以直接用 JUnit 控制台跑（不需要 Gradle 解析测试依赖）：

```bash
javac -encoding UTF-8 -d build/testclasses -cp "build/classes/java/main;<junit-console.jar>" $(find src/test/java -name "*.java")
java -jar <junit-console.jar> execute --class-path "build/classes/java/main;build/testclasses" --scan-class-path
```

离线环境下如果 `runServer` 解析不了 KubeJS 的传递依赖，可以把 KubeJS、Rhino 等 jar 放进 `run/mods/`，
FML 会扫描该目录。

## 结构

```
spec/      配方草稿、原料/产出、写入与删除策略（纯数据）
codegen/   配方 JSON、脚本条目、ID 与校验（纯逻辑）
server/    标记块合并、原子写入脚本文件
network/   客户端 → 服务端的写入请求
client/    配方编辑器界面（原版控件 + 自绘）
```

游戏侧只有 `KubejsRecipeGui`、`ModContent`、`RecipeEditorBlock`、`RecipeEditorMenu`、
`RecipeWriteService` 与界面几个类；其余都是可以直接单测的纯 Java。

## 生成结果长这样

```js
ServerEvents.recipes(event => {
// <kubejs-recipe-gui:begin>
// id: kubejs:crafting_b16949c4
// station: crafting
// output: minecraft:crafting_table
// inputs: minecraft:oak_planks, minecraft:oak_planks, minecraft:oak_planks, minecraft:oak_planks
event.custom({
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "AA",
    "AA"
  ],
  "key": {
    "A": {
      "item": "minecraft:oak_planks"
    }
  },
  "result": {
    "id": "minecraft:crafting_table",
    "count": 1
  }
}).id("kubejs:crafting_b16949c4");
// <kubejs-recipe-gui:end>
})
```

标记块之外的内容不会被改动；同 ID 覆盖、按类型/产物/标签批量删除都在标记块内完成。

## 许可

MIT。
