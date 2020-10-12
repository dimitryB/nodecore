plugins {
    java
    idea
    `java-library`
    `maven-publish`
    id("com.jfrog.artifactory")
}

dependencies {

    compile(project(":nodecore-ucp"))
    compile(project(":nodecore-grpc"))

    compile("ch.qos.logback:logback-classic:1.2.3")
    compile("commons-cli:commons-cli:1.4")
    compile("org.apache.commons:commons-lang3:3.0")
    compile("com.google.protobuf:protobuf-gradle-plugin:0.8.6")
    compile("com.google.inject:guice:4.1.0")
    compile("com.google.inject.extensions:guice-multibindings:4.1.0")
    compile("com.google.inject.extensions:guice-assistedinject:4.1.0")
    compile("org.reflections:reflections:0.9.11")
    compile("com.diogonunes:JCDP:2.0.3.1")
    compile("org.quartz-scheduler:quartz:2.2.1")
    compile("org.quartz-scheduler:quartz-jobs:2.2.1")
    compile("com.j256.ormlite:ormlite-core:5.1")
    compile("com.j256.ormlite:ormlite-jdbc:5.1")
    compile("org.xerial:sqlite-jdbc:$sqliteVersion")
    compile("org.bitcoinj:bitcoinj-core:0.14.7")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    compile("com.google.code.gson:gson:2.8.2")
    compile("io.netty:netty-buffer:4.1.30.Final")
    compile("org.bouncycastle:bcprov-jdk15on:1.60")
    compile("io.vertx:vertx-core:3.6.2")

    testImplementation("junit:junit:4.12")
}

setupJar("VeriBlock Extensions", "org.veriblock.extensions")
val sourcesJar = setupSourcesJar()

publish(
    artifactName = "veriblock-extensions",
    sourcesJar = sourcesJar
)

setupJacoco()
