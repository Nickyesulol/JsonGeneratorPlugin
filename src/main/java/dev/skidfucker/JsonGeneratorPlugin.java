package dev.skidfucker;

import dev.skidfucker.util.Artifact;
import dev.skidfucker.util.BetterArtifact;
import dev.skidfucker.util.URLRetriever;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public final class JsonGeneratorPlugin implements Plugin<Project> {

    private static final ExecutorService service = Executors.newVirtualThreadPerTaskExecutor();
    private static final CopyOnWriteArrayList<BetterArtifact> dependencies = new CopyOnWriteArrayList<>();
    public static boolean DEBUG = true;
    private final HashMap<Artifact, File> dependenciesAndFiles = new HashMap<>();

    @Override
    public void apply(Project project) {
        /*
         * TODO:
         *  implement hashing file and getting Size in bytes
         *  implement comments explaining features
         *  implement writing to json file
         */
        project.task("generateJson", task -> {
            task.setGroup("client");
            task.doLast(t -> {
                boolean exists = checkIfPropertiesFileExists(project);
                final String[] properties = determineProperties(project, exists);
                final String typeOfRelease = properties[0];
                final String version = properties[1];

                try {
                    DEBUG = Boolean.parseBoolean(properties[2]);
                } catch (Exception e) {
                    System.err.println("Invalid argument option for json.generator.Debug, falling back to default!");
                    DEBUG = false;
                }

                project.getConfigurations().all(this::execute);

                resolveRepositories(project);


                JsonGenerator generator = new JsonGenerator(version, typeOfRelease);
                generator.run(dependencies);
            });
        });
    }

    private boolean checkIfPropertiesFileExists(Project project) {
        final File propertiesFile = project.file("gradle.properties");

        if (propertiesFile.exists()) {
            return true;
        } else {
            System.err.println("No gradle.properties file found! Defaulting to typeOfRelease: release and latest versions!");
            return false;
        }
    }

    private String[] determineProperties(Project project, boolean exists) {
        final String[] temp = new String[3];
        if (exists) {

            if (project.hasProperty("json.generator.TypeOfRelease")) {
                temp[0] = (String.valueOf(project.property("json.generator.TypeOfRelease")));
            } else {
                System.err.println("No typeOfRelease-property found, defaulting to release!");
                temp[0] = ("release");
            }
            if (project.hasProperty("json.generator.Version")) {
                temp[1] = (String.valueOf(project.property("json.generator.Version")));
            } else {
                System.err.println("No version-property found, defaulting to latest!");
                temp[1] = ("latest");
            }

            if (project.hasProperty("json.generator.Debug")) {
                System.err.println("Debug mode was enabled!");
                temp[2] = (String.valueOf(project.property("json.generator.Debug")));
            } else {
                temp[2] = ("false");
            }
        } else {
            System.err.println("No gradle.properties file found! Falling back to default settings!");
            temp[0] = "release";
            temp[1] = "latest";
            temp[2] = "false";
        }
        return temp;
    }

    private void resolveRepositories(Project project) {
        final ConcurrentHashMap<Artifact, URL> validUrls = new ConcurrentHashMap<>();
        project.getRepositories().forEach(temp -> {
            if (temp instanceof MavenArtifactRepository repo) {
                if (repo.getUrl().toString().startsWith("file")) return;
                if (DEBUG) System.out.println(repo.getUrl());


                final ConcurrentHashMap<Artifact, CompletableFuture<URL>> futures = new ConcurrentHashMap<>();

                for (final Artifact artifact : dependenciesAndFiles.keySet()) {
                    CompletableFuture<URL> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            return new URLRetriever(artifact, repo).call();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, service);
                    futures.put(artifact, future);
                }

                // Wait for all futures to complete
                CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

                // Add the URLs to validUrls
                for (Map.Entry<Artifact, CompletableFuture<URL>> entry : futures.entrySet()) {
                    try {
                        URL url = entry.getValue().get();
                        if (url != null) {
                            validUrls.put(entry.getKey(), url);
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }
//                service.shutdown();
//                try {
//                    if (!(service.awaitTermination(3, TimeUnit.MINUTES))) {
//                        System.err.println("Tasks haven't been finished in time, aborting!");
//                        return;
//                    }
//                } catch (InterruptedException e) {
//                    System.err.println("Failed executing all tasks, aborting!");
//                    return;
//                }
            }
        });

        for (Artifact artifact : dependenciesAndFiles.keySet()) {
            if (!validUrls.containsKey(artifact)) {
                dependenciesAndFiles.remove(artifact);
            } else {
                dependencies.add(new BetterArtifact(artifact, validUrls.get(artifact), dependenciesAndFiles.get(artifact)));
            }
        }
    }

    private void execute(Configuration configuration) {
        if (!configuration.getName().equals("runtimeClasspath")) return;

        if (DEBUG) {
            System.out.println("Configuration: " + configuration.getName());
        }


        // Get the resolved configuration
        ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();

        // Iterate over the resolved dependencies
        for (ResolvedDependency resolvedDependency : resolvedConfiguration.getFirstLevelModuleDependencies()) {


            if (DEBUG) {
                System.out.println("Resolved Dependency: " + resolvedDependency.getModuleGroup() + ":" + resolvedDependency.getModuleName() + ":" + resolvedDependency.getModuleVersion());
            }


            for (ResolvedArtifact artifact : resolvedDependency.getAllModuleArtifacts()) {
                if (DEBUG) System.out.println("Artifact: " + artifact.getExtension());
                if (!artifact.getExtension().equals("jar")) continue;
                Artifact dependency = new Artifact(resolvedDependency.getModuleGroup(), resolvedDependency.getModuleName(), resolvedDependency.getModuleVersion(), "jar");
                dependenciesAndFiles.putIfAbsent(dependency, artifact.getFile());
            }

        }
    }

}
