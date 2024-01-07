package dev.skidfucker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.skidfucker.util.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class JsonGenerator {

    private static final boolean DEBUG_MODE = JsonGeneratorPlugin.DEBUG;
    private static final ExecutorService service = Executors.newVirtualThreadPerTaskExecutor();
    public static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    public static final String FILE_NAME = "./test.json";

    private final String version;
    private final String typeOfRelease;

    public JsonGenerator(String version, String typeOfRelease) {
        this.version = version;
        this.typeOfRelease = typeOfRelease;
    }

    public void run(CopyOnWriteArrayList<BetterArtifact> betterArtifacts) {
        final JsonObject manifastJson = HttpUtil.downloadJson(VERSION_MANIFEST_URL);
        final JsonArray versions = manifastJson.getAsJsonArray("versions");


//        this.debugJsonArrayPrinter(versions);
        
        String pistonUrl = this.selectVersion(versions, typeOfRelease, version);

        if (DEBUG_MODE) System.out.println(pistonUrl);

        JsonObject selectedJson = HttpUtil.downloadJson(pistonUrl);

        this.deleteDownloads(selectedJson, true);

        File clientJson = JsonUtil.writeJsonToFile(selectedJson.toString(), Paths.get(FILE_NAME));

        if (DEBUG_MODE) {
            String lmao = JsonUtil.prettyFormatPrint(selectedJson);
            clientJson = JsonUtil.writeJsonToFile(lmao, clientJson.toPath());
        }

        JsonArray libraries = selectedJson.getAsJsonArray("libraries");

//        this.debugJsonArrayPrinter(libraries);

        final CopyOnWriteArrayList<BestArtifact> bestArtifacts = new CopyOnWriteArrayList<>();

        for (final BetterArtifact betterArtifact : betterArtifacts) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return new InformationRetriever(betterArtifact).call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, service).thenAccept(bestArtifacts::add);
        }


        for (final BestArtifact artifact : bestArtifacts) {
            System.out.println(artifact.sha1());
        }

/*
        service.shutdown();

        try {
            if (!(service.awaitTermination(5, TimeUnit.MINUTES))) {
                System.err.println("Tasks haven't been finished in time, aborting!");
                return;
            }
        } catch (InterruptedException e) {
            System.err.println("Failed executing all tasks, aborting!");
            return;
        }*/


        for (final BestArtifact bestArtifact : bestArtifacts) {
            JsonObject customDependency = new JsonObject();
            JsonObject downloads = new JsonObject();
            JsonObject artifact = new JsonObject();

            customDependency.addProperty("name", bestArtifact.betterArtifact().artifact().name());

            Artifact artifact1 = bestArtifact.betterArtifact().artifact();

            artifact.addProperty("sha1", bestArtifact.sha1());
            artifact.addProperty("size", bestArtifact.size());
            artifact.addProperty("url", bestArtifact.betterArtifact().url().toString());
            artifact.addProperty("path", artifact1.group().replace('.', '/') + "/" + artifact1.name() + "/" + artifact1.version() + "/" + artifact1.name() + "-" + artifact1.version() + "." + artifact1.extension());
            downloads.add("artifact", artifact);

            customDependency.add("downloads", downloads);
            libraries.add(customDependency);
        }

        selectedJson.add("libraries", libraries);

        clientJson = JsonUtil.writeJsonToFile(selectedJson.toString(), clientJson.toPath());

        if (DEBUG_MODE) {
            String lmao = JsonUtil.prettyFormatPrint(selectedJson);
            clientJson = JsonUtil.writeJsonToFile(lmao, clientJson.toPath());
        }
        //TODO: implement logic
       /* for (final String dependency : namesOrgVer) {
            JsonObject customDependency = new JsonObject();
            JsonObject downloads = new JsonObject();
            JsonObject artifact = new JsonObject();

            customDependency.addProperty("name", dependency);

            artifact.addProperty("sha1", "TODO");
            artifact.addProperty("size", 12345);
            artifact.addProperty("url", "TODO");

            downloads.add("artifact", artifact);

            customDependency.add("downloads", downloads);
            libraries.add(customDependency);
        }

        selectedJson.add("libraries", libraries);

        clientJson = JsonUtil.writeJsonToFile(selectedJson.toString(), clientJson.toPath());

        if (DEBUG_MODE) {
            String lmao = JsonUtil.prettyFormatPrint(selectedJson);
            clientJson = JsonUtil.writeJsonToFile(lmao, clientJson.toPath());
        }*/
    }


    public void debugJsonArrayPrinter(JsonArray array) {
        if (!DEBUG_MODE) return;
        System.out.println(array);
        for (final JsonElement element : array) {
            System.out.println(element.getAsJsonObject());
        }

    }


    public void deleteDownloads(JsonObject json, boolean wipe) {
        if (wipe) {
            if (json.has("downloads")) json.remove("downloads");
        } else {
            JsonObject downloads = json.get("downloads").getAsJsonObject();
            if (downloads.has("client")) downloads.remove("client");
            if (downloads.has("server")) downloads.remove("server");
            if (downloads.has("windows_server")) downloads.remove("windows_server");
        }
    }


    public String selectVersion(JsonArray versions, String type, String version) {
        return retrievePistonLink(versions, type, version);
    }

    private String retrievePistonLink(JsonArray versions,String typeOfRelease, String version) {
        JsonArray typeArray = new JsonArray();

        for (final JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            if (!element.getAsJsonObject().get("type").getAsString().equals(typeOfRelease)) continue;
            typeArray.add(element);
        }


        final List<String> ids = new ArrayList<>();


        for (final JsonElement element : typeArray) {
            String id = element.getAsJsonObject().get("id").getAsString();
            ids.add(id);
        }

        if (JsonGeneratorPlugin.DEBUG) {
            for (int i = 1; i < ids.size(); i++) {
                String id = ids.get(i);
                String message = i + ". " + id;
                System.out.println(message);
            }
        }


        String wantedVersion = version;

        if (wantedVersion.equalsIgnoreCase("latest")) {
            wantedVersion = ids.getFirst();
        } else {
            for (final String id : ids) {
                if (!wantedVersion.trim().equals(id)) continue;
                wantedVersion = id;
                break;
            }
        }

        String url = null;
        for (final JsonElement element : typeArray) {
            if (!element.getAsJsonObject().get("id").getAsString().equals(wantedVersion)) continue;
            url = element.getAsJsonObject().get("url").getAsString();
        }

        return url;
    }


}
