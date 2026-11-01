package com.sakuracha.kubejsrecipegui.network;

import com.sakuracha.kubejsrecipegui.KubejsRecipeGui;
import com.sakuracha.kubejsrecipegui.server.RecipeWriteService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 注册客户端到服务端的写入与重载请求通道。
 */
@EventBusSubscriber(modid = KubejsRecipeGui.MOD_ID)
public final class ModNetwork {
    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(WriteScriptPayload.TYPE, WriteScriptPayload.STREAM_CODEC, RecipeWriteService::handle);
        registrar.playToServer(ReloadScriptsPayload.TYPE, ReloadScriptsPayload.STREAM_CODEC,
                RecipeWriteService::handleReload);
    }
}
