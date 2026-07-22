plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.allopen") version "2.4.0"
    id("io.quarkus") version "3.37.3"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jmailen.kotlinter") version "5.6.0"
}

repositories {
    mavenCentral()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-hibernate-orm-panache-kotlin")
    implementation("io.quarkus:quarkus-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // 必要に応じて追加:
    // implementation("io.quarkus:quarkus-grpc")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}

group = "com.akaitigo"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        javaParameters.set(true)
    }
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

detekt {
    config.setFrom("$projectDir/detekt.yml")
    parallel = true
    buildUponDefaultConfig = true
}

tasks.register<JavaExec>("generateSampleData") {
    group = "application"
    description = "分析用サンプルCSVを生成する。args: [出力パス] [トランザクション数] [シード]"
    mainClass.set("com.akaitigo.posanalytics.sample.SampleDataGeneratorKt")
    classpath = sourceSets["main"].runtimeClasspath
}
