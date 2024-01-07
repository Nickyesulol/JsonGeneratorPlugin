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
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
         *  setup file name of project and custom project
         *  implement comments explaining features
         */
        final String rootProjectName = project.getRootProject().getName();
        final JsonGeneratorPluginExtension extension = project.getExtensions()
                .create("jsonGenerator", JsonGeneratorPluginExtension.class);

        extension.getExcludedDependencies().convention(List.of());
        extension.getFileName().convention(rootProjectName);

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
                boolean wipeLjwgl;
                try {
                    wipeLjwgl = Boolean.parseBoolean(properties[3]);
                } catch (Exception e) {
                    System.err.println("Invalid argument option for json.generator.WipeLwjgl, falling back to default!");
                    wipeLjwgl = false;
                }
                boolean wipeLibraries;
                try {
                    wipeLibraries = Boolean.parseBoolean(properties[4]);
                } catch (Exception e) {
                    System.err.println("Invalid argument option for json.generator.WipeLibraries, falling back to default!");
                    wipeLibraries = false;
                }
                boolean prettyPrint;
                try {
                    prettyPrint = Boolean.parseBoolean(properties[5]);
                } catch (Exception e) {
                    System.err.println("Invalid argument option for json.generator.PrettyPrint, falling back to default!");
                    prettyPrint = false;
                }

                project.getConfigurations().all(configuration -> this.execute(configuration, extension.getExcludedDependencies().get()));

                resolveRepositories(project);
                final String projectVersion = project.getVersion().toString();
                final String fileName = extension.getFileName().get() + "-" + projectVersion;

                final JsonGenerator generator = new JsonGenerator(fileName, version, typeOfRelease, wipeLjwgl, wipeLibraries, prettyPrint);
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
        final String[] temp = new String[6];
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
                temp[2] = (String.valueOf(project.property("json.generator.Debug")));
                if (temp[2].equalsIgnoreCase("true")) System.err.println("Debug mode was enabled!");
            } else {
                temp[2] = "false";
            }
            if (project.hasProperty("json.generator.WipeLwjgl")) {
                temp[3] = (String.valueOf(project.property("json.generator.WipeLwjgl")));
            } else {
                temp[3] = "false";
            }
            if (project.hasProperty("json.generator.WipeLibraries")) {
                temp[4] = (String.valueOf(project.property("json.generator.WipeLibraries")));
            } else {
                temp[4] = "false";
            }
            if (project.hasProperty("json.generator.PrettyPrint")) {
                temp[5] = (String.valueOf(project.property("json.generator.PrettyPrint")));
            } else {
                temp[5] = "false";
            }

        } else {
            System.err.println("No gradle.properties file found! Falling back to default settings!");
            temp[0] = "release";
            temp[1] = "latest";
            temp[2] = "false";
            temp[3] = "false";
            temp[4] = "false";
            temp[5] = "false";
        }
        return temp;
    }

    private void resolveRepositories(Project project) {
        final ConcurrentHashMap<Artifact, URL> validUrls = new ConcurrentHashMap<>();
        project.getRepositories().forEach(temp -> {
            if (temp instanceof MavenArtifactRepository repo) {
                if (repo.getUrl().toString().startsWith("file")) return;
                if (DEBUG) System.out.println("Found repository: " + repo.getUrl());


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

    private void execute(Configuration configuration, List<String> excluded) {
        if (!configuration.getName().equals("runtimeClasspath")) return;

        if (DEBUG) {
            System.out.println("Configuration: " + configuration.getName());
        }


        // Get the resolved configuration
        ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();

        if (DEBUG) {
            System.out.println("Exluded dependencies: ");
            excluded.forEach(System.out::println);
            System.out.println("-------------------");
        }

        // Iterate over the resolved dependencies
        for (ResolvedDependency resolvedDependency : resolvedConfiguration.getFirstLevelModuleDependencies()) {
            final String resolvedString1 = resolvedDependency.getModuleGroup() + ":" + resolvedDependency.getModuleName() + ":" + resolvedDependency.getModuleVersion();
            final String resolvedString2 = resolvedDependency.getModuleGroup() + ":" + resolvedDependency.getModuleName();
            if (excluded.contains(resolvedString1)) {
                if (DEBUG) System.out.println("excluded1");
                continue;
            } else if (excluded.contains(resolvedString2)) {
                if (DEBUG) System.out.println("excluded2");
                continue;
            }

            if (DEBUG) {
                System.out.println("Resolved Dependency: " + resolvedString1);
            }

            for (ResolvedArtifact artifact : resolvedDependency.getAllModuleArtifacts()) {
                if (DEBUG) System.out.println("Extension: " + artifact.getExtension());
                if (!Objects.equals(artifact.getExtension(), "jar")) continue;
                Artifact dependency = new Artifact(resolvedDependency.getModuleGroup(), resolvedDependency.getModuleName(), resolvedDependency.getModuleVersion(), "jar");
                dependenciesAndFiles.putIfAbsent(dependency, artifact.getFile());
            }

        }
    }

}
