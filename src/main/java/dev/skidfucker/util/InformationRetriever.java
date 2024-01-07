package dev.skidfucker.util;

import java.nio.file.Files;
import java.security.MessageDigest;
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            sb.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
        }
        return new BestArtifact(artifact, sb.toString(), Files.size(artifact.file().toPath()));
    }
}
