import java.io.ByteArrayOutputStream
import java.util.*

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    java
    idea
    id ("com.google.cloud.tools.jib") version "3.4.1"
}

idea {
    module {
        isDownloadJavadoc = true
    }
}




group = "cz.inovatika"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}


repositories {
    mavenLocal()
    mavenCentral()
}

val slf4jVersion = "2.0.12"
val krameriusVersion = "7.0.31"

dependencies {
    implementation("io.javalin:javalin:6.1.3")

    implementation("org.apache.lucene:lucene-core:9.10.0")

    implementation("cz.incad.kramerius:common:$krameriusVersion") {
        exclude( group="internal", module="djvuframe-0.8.09")
        exclude( group="internal", module="javadjvu-0.8.09")
        exclude( group="internal", module="jai_imageio-1.1")
        exclude( group="nl.siegmann.epublib", module="epublib-core")
    }

    implementation("org.slf4j:log4j-over-slf4j:$slf4jVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.slf4j:jcl-over-slf4j:$slf4jVersion")
    implementation("org.slf4j:jul-to-slf4j:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:1.3.14")
    implementation("ch.qos.logback:logback-core:1.3.14")

    testImplementation("junit:junit:4.13.2")
}

fun getDate(): String {
    return Date().toString()
}

val buildDate by extra(getDate())
val userName by extra(System.getProperty("user.name"))

tasks {
    val gitcall by registering(Exec::class) {
        commandLine("git", "rev-parse", "HEAD")
        standardOutput = ByteArrayOutputStream()
        doLast {
            val result = standardOutput.toString()
            val hash by extra(result)
        }
    }





}

jib {
    from {
        image = "eclipse-temurin:21-jre-alpine"
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }

    to {
        image = "ceskaexpedice/kramerius-k3handle:${version}"
    }

    container {
        mainClass = "cz.inovatika.k3handle.Main"
        ports = listOf("8080")
        environment = mapOf("JAVA_OPTS" to "-Xms512m -Xmx1024m")
        user = "0"
    }
}




