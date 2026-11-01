package com.sakuracha.kubejsrecipegui.network;

import com.sakuracha.kubejsrecipegui.KubejsRecipeGui;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：请求重载 KubeJS 脚本。
 *
 * <p>写入脚本后不会自动重载数据包，玩家确认写入结果无误后点界面上的「重载」按钮发送此包。</p>
 */
public record ReloadScriptsPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ReloadScriptsPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(KubejsRecipeGui.MOD_ID, "reload_scripts"));

    public static final StreamCodec<ByteBuf, ReloadScriptsPayload> STREAM_CODEC =
            StreamCodec.unit(new ReloadScriptsPayload());

    @Override
    public CustomPacketPayload.Type<ReloadScriptsPayload> type() {
        return TYPE;
    }
}
