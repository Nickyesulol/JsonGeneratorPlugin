package dev.skidfucker.util;

import dev.skidfucker.JsonGeneratorPlugin;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Callable;

public class URLRetriever implements Callable<URL> {

    private final Artifact artifact;
    private final MavenArtifactRepository repo;

    public URLRetriever(final Artifact artifact, final MavenArtifactRepository repo) {
        this.artifact = artifact;
        this.repo = repo;
    }


    @Override
    public URL call() throws Exception {
        String potentialUrl = determineURL();
        try {
            URL url = new URI(potentialUrl).toURL();
            url.openStream().close();
            if (JsonGeneratorPlugin.DEBUG) System.out.println("URL SUCCESSFULLY FOUND: " + url);
            return url;
        } catch (IOException | URISyntaxException e) {
            if (JsonGeneratorPlugin.DEBUG) {
                System.out.println("URL failed: " + potentialUrl);
            }
            return null;
        }
    }

    @NotNull
    private String determineURL() {
        String potentialUrl;
        if (repo.getUrl().toString().endsWith("/")) {
            potentialUrl = repo.getUrl() + artifact.group().replace('.', '/') + "/" + artifact.name() + "/" + artifact.version() + "/" + artifact.name() + "-" + artifact.version() + "." + artifact.extension();
        } else {
            potentialUrl = repo.getUrl() + "/" + artifact.group().replace('.', '/') + "/" + artifact.name() + "/" + artifact.version() + "/" + artifact.name() + "-" + artifact.version() + "." + artifact.extension();
        }
        return potentialUrl;
    }
}
