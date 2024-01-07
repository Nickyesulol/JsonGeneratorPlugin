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

    public static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    public final String fileName;
    private static final ExecutorService service = Executors.newVirtualThreadPerTaskExecutor();
    private final String version;
    private final String typeOfRelease;
    private final boolean wipeLwjgl;
    private final boolean wipeLibraries;
    private final boolean prettyPrint;

    public JsonGenerator(String fileName, String version, String typeOfRelease, boolean wipeLwjgl, boolean wipeLibraries, boolean prettyPrint) {
        this.version = version;
        this.typeOfRelease = typeOfRelease;
        this.wipeLwjgl = wipeLwjgl;
        this.wipeLibraries = wipeLibraries;
        this.prettyPrint = prettyPrint;
        this.fileName = fileName;
    }

    public void run(CopyOnWriteArrayList<BetterArtifact> betterArtifacts) {
        final JsonObject manifastJson = HttpUtil.downloadJson(VERSION_MANIFEST_URL);
        final JsonArray versions = manifastJson.getAsJsonArray("versions");

        String pistonUrl = this.selectVersion(versions, typeOfRelease, version);

        if (JsonGeneratorPlugin.DEBUG) System.out.println("PistonURL: " + pistonUrl);

        JsonObject selectedJson = HttpUtil.downloadJson(pistonUrl);

        selectedJson.addProperty("id", fileName);

        this.deleteDownloads(selectedJson, true);

        File clientJson = JsonUtil.writeJsonToFile(selectedJson.toString(), Paths.get(fileName + ".json"));

        JsonArray libraries = selectedJson.getAsJsonArray("libraries");

        if (this.wipeLwjgl && !this.wipeLibraries) {
            libraries = this.wipeLwjgl2(libraries);
        }

        if (this.wipeLibraries) {
            libraries = new JsonArray();
        }

        final CopyOnWriteArrayList<BestArtifact> bestArtifacts = new CopyOnWriteArrayList<>();

        final CopyOnWriteArrayList<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();
        for (final BetterArtifact betterArtifact : betterArtifacts) {
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return new InformationRetriever(betterArtifact).call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, service).thenAccept(bestArtifacts::add);

            futures.add(future);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        // Block and wait for all futures to complete
        allFutures.join();

        if (JsonGeneratorPlugin.DEBUG) {
            for (final BestArtifact artifact : bestArtifacts) {
                System.out.println("Artifact: " + artifact.betterArtifact().file().getName() + " Sha1: "+ artifact.sha1() + " Size: " + artifact.size());
            }
        }

        for (final BestArtifact bestArtifact : bestArtifacts) {
            JsonObject customDependency = new JsonObject();
            JsonObject downloads = new JsonObject();
            JsonObject artifact = new JsonObject();

            Artifact artifact1 = bestArtifact.betterArtifact().artifact();

            final String name = artifact1.group() + ":" + artifact1.name() + ":" + artifact1.version();
            if (this.isDuplicate(libraries, name)) continue;

            customDependency.addProperty("name", name);

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

        //implement property for prettyPrinting
        if (JsonGeneratorPlugin.DEBUG || this.prettyPrint) {
            String lmao = JsonUtil.prettyFormatPrint(selectedJson);
            clientJson = JsonUtil.writeJsonToFile(lmao, clientJson.toPath());
        }
    }

    private boolean isDuplicate(final JsonArray libraries, String name) {
        for (final JsonElement element : libraries) {
            if (!checkIfNameEquals(element, name)) continue;
            return true;
        }
        return false;
    }

    private boolean checkIfNameEquals(final JsonElement element, final String name) {
        if (!element.isJsonObject()) return false;
        final JsonObject object = element.getAsJsonObject();
        if (!object.has("name")) return false;
        String tempName = object.get("name").getAsString();
        return tempName.equals(name);
    }

    private JsonArray wipeLwjgl2(JsonArray libraries) {
        final JsonArray temp = libraries.deepCopy();
        for (JsonElement element : libraries) {
            if (!element.isJsonObject()) continue;
            final JsonObject object = element.getAsJsonObject();
            if (!object.has("name")) continue;
            if (!object.get("name").getAsString().startsWith("org.lwjgl")) continue;
            temp.remove(element);
        }
        return temp;
    }

    public void debugJsonArrayPrinter(JsonArray array) {
        if (!JsonGeneratorPlugin.DEBUG) return;
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

    private String retrievePistonLink(JsonArray versions, String typeOfRelease, String version) {
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
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                String message = (i + 1) + ". " + id;
                if (i == 0) {
                    message += " latest";
                }
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
