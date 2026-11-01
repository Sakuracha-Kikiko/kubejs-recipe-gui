package com.sakuracha.kubejsrecipegui.server;

import com.sakuracha.kubejsrecipegui.codegen.RecipeCode;
import com.sakuracha.kubejsrecipegui.network.WriteRequest;
import com.sakuracha.kubejsrecipegui.network.WriteScriptPayload;
import com.sakuracha.kubejsrecipegui.spec.DeleteMode;
import com.sakuracha.kubejsrecipegui.spec.Workstation;
import com.sakuracha.kubejsrecipegui.spec.WriteMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 服务端写入流程：校验权限 → 重新拼装脚本文本 → 合并进目标文件 → 触发 {@code /reload}。
 *
 * <p>客户端只提供结构化数据，服务端不信任客户端给出的任何文本，避免把任意内容写进脚本目录。</p>
 */
public final class RecipeWriteService {
    private static final Logger LOGGER = LoggerFactory.getLogger("kubejs-recipe-gui");

    private RecipeWriteService() {
    }

    public static void handle(WriteScriptPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> write(payload.data(), player));
    }

    private static void write(String data, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!canWrite(server, player)) {
            player.sendSystemMessage(Component.translatable("gui.kubejs_recipe_gui.msg.no_permission")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        WriteRequest request = WriteRequest.decode(data);
        if (request == null) {
            player.sendSystemMessage(Component.translatable("gui.kubejs_recipe_gui.msg.invalid", "-")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        Workstation station = Workstation.byKey(request.station());
        if (station == null || request.recipeJson().isBlank()
                || request.recipeJson().length() > RecipeCode.MAX_BYTES) {
            player.sendSystemMessage(Component.translatable("gui.kubejs_recipe_gui.msg.invalid", request.station())
                    .withStyle(ChatFormatting.RED));
            return;
        }

        String id = request.requestedId().isBlank()
                ? RecipeCode.autoId(station.key(), request.recipeJson())
                : request.requestedId();
        if (!RecipeCode.isValidId(id)) {
            player.sendSystemMessage(Component.translatable("gui.kubejs_recipe_gui.msg.invalid", id)
                    .withStyle(ChatFormatting.RED));
            return;
        }

        String output = sanitize(request.output());
        String inputs = sanitize(request.inputs());
        String text = RecipeCode.entryFromJson(request.recipeJson(), id, station.key(), output, inputs);
        ScriptStore.Entry entry = new ScriptStore.Entry(id, station.key(), output, inputs, text);

        Path file = FMLPaths.GAMEDIR.get().resolve(ScriptStore.RELATIVE_PATH);
        try {
            String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            ScriptStore.MergeResult result = ScriptStore.merge(existing, entry,
                    WriteMode.byKey(request.writeMode()), DeleteMode.byKey(request.deleteMode()),
                    request.deleteValue());

            if (result.outcome() == ScriptStore.Outcome.SKIPPED) {
                player.sendSystemMessage(Component.translatable("gui.kubejs_recipe_gui.msg.skipped", id)
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }

            ScriptStore.write(file, result.content());
            String key = result.outcome() == ScriptStore.Outcome.CREATED
                    ? "gui.kubejs_recipe_gui.msg.written"
                    : "gui.kubejs_recipe_gui.msg.replaced";
            player.sendSystemMessage(Component.translatable(key, id, ScriptStore.RELATIVE_PATH)
                    .withStyle(ChatFormatting.GREEN));
            reload(server);
        } catch (IOException exception) {
            LOGGER.error("写入脚本 {} 失败", file, exception);
            player.sendSystemMessage(Component.translatable("gui.kubejs_recipe_gui.msg.failed", exception.getMessage())
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** 只有单人房主与服务端管理员可以落盘。 */
    private static boolean canWrite(MinecraftServer server, ServerPlayer player) {
        return server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2);
    }

    /** 元数据注释里不允许出现换行，否则会把脚本结构拆坏。 */
    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    /** KubeJS 在资源重载时重新执行脚本，因此写完立刻 /reload 就能生效。 */
    private static void reload(MinecraftServer server) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
    }
}
