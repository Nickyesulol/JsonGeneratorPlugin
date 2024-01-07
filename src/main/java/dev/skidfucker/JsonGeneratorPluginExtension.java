package dev.skidfucker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public class JsonGeneratorPluginExtension {

    private final ListProperty<String> excludedDependencies;
    private final Property<String> fileName;


    public JsonGeneratorPluginExtension(ObjectFactory objectFactory) {
        this.excludedDependencies = objectFactory.listProperty(String.class);
        this.fileName = objectFactory.property(String.class);
    }


    public ListProperty<String> getExcludedDependencies() {
        return excludedDependencies;
    }

    public Property<String> getFileName() {
        return fileName;
    }
}
