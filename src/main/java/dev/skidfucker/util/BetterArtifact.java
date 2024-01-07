package dev.skidfucker.util;

import java.io.File;
import java.net.URL;

public record BetterArtifact(Artifact artifact, URL url, File file) {
}
