plugins {
    kotlin("jvm") version "1.9.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://gitlab.com/api/v4/groups/6569239/-/packages/maven") {
        gitLabCredentials()
    }
    maven {
        url = uri("https://entur2.jfrog.io/artifactory/partner-release")
        authentication {
            create<BasicAuthentication>("basic")
        }
        credentials {
            val usernameProp = "ENTUR_ARTIFACTORY_USER"
            val passwordProp = "ENTUR_ARTIFACTORY_PASSWORD"
            username = findProperty(usernameProp)?.toString() ?: System.getenv(usernameProp)
            password = findProperty(passwordProp)?.toString() ?: System.getenv(passwordProp)
        }
    }

}

val grpcVersion = "1.60.0"

dependencies {
    implementation("no.ruter.sb.idb:sb-idb-common:1.16.109")

    implementation("io.grpc:grpc-okhttp:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")

    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")



    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
fun MavenArtifactRepository.gitLabCredentials() {
    credentials(HttpHeaderCredentials::class.java) {
        System.getenv("CI_JOB_TOKEN")?.let {
            name = "Job-Token"
            value = it
            return@credentials
        }
        name = "Private-Token"
        value = project.properties["gitLabPrivateToken"] as String? ?: error(
            "Couldn't find CI_JOB_TOKEN env variable. If locally, set 'gitLabPrivateToken=<gitlab-personal-access-token>' in ~/.gradle/gradle.properties!"
        )
    }
    authentication {
        create<HttpHeaderAuthentication>("header")
    }
}
