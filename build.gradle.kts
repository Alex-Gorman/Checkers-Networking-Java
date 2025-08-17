plugins {
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } // 17 also fine
}

application {
    mainClass.set("checkers.SwingFrame")  // <-- your entry point class
}

tasks.jar {
    manifest { attributes["Main-Class"] = "checkers.SwingFrame" }
}
