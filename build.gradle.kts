plugins {
    id("java")
    id("dev.skidfucker.JsonGenerator") version "1.0"
    `java-gradle-plugin`
    `maven-publish`

}

jsonGenerator {
    excludedDependencies = listOf("com.google.code.gson:gson:2.10.1")
}

group = "dev.skidfucker"
version = "1.0"
repositories {
    maven { url = uri("https://jitpack.io")  }
    mavenLocal()
    mavenCentral()
}

dependencies {
    api("com.google.code.gson:gson:2.10.1");
    api(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

gradlePlugin {
    plugins {
        create("jsonGenerator") {
            id = "dev.skidfucker.JsonGenerator"
            implementationClass = "dev.skidfucker.JsonGeneratorPlugin"
        }
    }
}


tasks.test {
    useJUnitPlatform()
}