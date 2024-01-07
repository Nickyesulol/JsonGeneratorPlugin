package dev.skidfucker.util;

import org.gradle.internal.impldep.jakarta.xml.bind.DatatypeConverter;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.Callable;

public class InformationRetriever implements Callable<BestArtifact> {


    private final BetterArtifact artifact;

    public InformationRetriever(final BetterArtifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public BestArtifact call() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(Files.readAllBytes(artifact.file().toPath()));
        byte[] digest = md.digest();
        String sha1 = DatatypeConverter.printHexBinary(digest);
        return new BestArtifact(artifact, sha1, Files.size(artifact.file().toPath()));
    }
}
