package dev.skidfucker.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class JsonUtil {

    public static String prettyFormatPrint(JsonObject json) {
        return prettyFormatPrint(json.toString());
    }

    public static String prettyFormatPrint(String json) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonElement jsonElement = com.google.gson.JsonParser.parseString(json);
        return gson.toJson(jsonElement);
    }

    public static File writeJsonToFile(String json, Path path) {
        try {
            File file = path.toFile();
            if (!file.exists()) file.createNewFile();
            FileWriter fw = new FileWriter(file);
            fw.write(json);
            fw.flush();
            fw.close();
            return file;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
