package com.sakuracha.kubejsrecipegui.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 写入请求的信封。只传结构化数据与生成好的紧凑 JSON，脚本文本由服务端重新拼装，客户端无法直接指定文件内容。
 */
public record WriteRequest(String recipeJson, String station, String output, String inputs,
                           String requestedId, String writeMode, String deleteMode, String deleteValue) {
    public static String encode(WriteRequest request) {
        JsonObject json = new JsonObject();
        json.addProperty("recipe", request.recipeJson());
        json.addProperty("station", request.station());
        json.addProperty("output", request.output());
        json.addProperty("inputs", request.inputs());
        json.addProperty("id", request.requestedId());
        json.addProperty("write", request.writeMode());
        json.addProperty("delete", request.deleteMode());
        json.addProperty("deleteValue", request.deleteValue());
        return json.toString();
    }

    /** 解析失败返回 {@code null}，调用方按"非法请求"处理。 */
    public static WriteRequest decode(String data) {
        if (data == null || data.length() > WriteScriptPayload.MAX_LENGTH) {
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            return new WriteRequest(
                    string(json, "recipe"),
                    string(json, "station"),
                    string(json, "output"),
                    string(json, "inputs"),
                    string(json, "id"),
                    string(json, "write"),
                    string(json, "delete"),
                    string(json, "deleteValue"));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }
}
