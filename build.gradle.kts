plugins {
    id("java")
    id("dev.skidfucker.JsonGenerator") version "1.0"
    `java-gradle-plugin`
    `maven-publish`

}



buildscript {
}


group = "dev.skidfucker"
version = "1.0"
repositories {
    maven { url = uri("https://jitpack.io")  }
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1");
    implementation("org.projectlombok:lombok:1.18.30")
    implementation(gradleApi())
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