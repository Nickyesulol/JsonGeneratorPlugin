package dev.skidfucker.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skidfucker.JsonGenerator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class HttpUtil {

    public static JsonObject downloadJson(String string) {
        try {
            return downloadJson(new URI(string).toURL());
        } catch (MalformedURLException | URISyntaxException e) {
           e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("code", -1);
            error.addProperty("message", "idfk what u did wrong ¯\\_(ツ)_/¯, but you fucked up!");
            return error;
        }
    }

    public static JsonObject downloadJson(URL url) {
        try (InputStream input = url.openConnection().getInputStream()) {
            InputStreamReader isr = new InputStreamReader(input);
            BufferedReader reader = new BufferedReader(isr);
            StringBuilder json = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                json.append((char) c);
            }
            return JsonParser.parseString(json.toString()).getAsJsonObject();
        } catch (IOException e) {
            e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("code", -1);
            error.addProperty("message", "idfk what u did wrong ¯\\_(ツ)_/¯, but you fucked up!");
            return error;
        }
    }

    public static String downloadJsonString(URL url) {
        try (InputStream input = url.openStream()) {
            InputStreamReader isr = new InputStreamReader(input);
            BufferedReader reader = new BufferedReader(isr);
            StringBuilder json = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                json.append((char) c);
            }
            return json.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        JsonObject error = new JsonObject();
        error.addProperty("code", 500);
        error.addProperty("message", "idfk what u did wrong ¯\\_(ツ)_/¯, but you fucked up!");
        return error.toString();
    }

}
