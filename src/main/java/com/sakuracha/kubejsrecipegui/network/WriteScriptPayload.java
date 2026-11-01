package com.sakuracha.kubejsrecipegui.network;

import com.sakuracha.kubejsrecipegui.KubejsRecipeGui;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端把"要写入哪条配方"发给服务端。真正的脚本文本由服务端重新拼装。
 */
public record WriteScriptPayload(String data) implements CustomPacketPayload {
    /** 单条消息的体积上限，防止异常客户端把服务端撑爆。 */
    public static final int MAX_LENGTH = 32767;

    public static final CustomPacketPayload.Type<WriteScriptPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(KubejsRecipeGui.MOD_ID, "write_script"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WriteScriptPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.data, MAX_LENGTH),
            buffer -> new WriteScriptPayload(buffer.readUtf(MAX_LENGTH))
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
