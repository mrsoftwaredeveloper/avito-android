plugins {
    kotlin("jvm")
    `maven-publish`
    id("com.avito.bintray")
}

publishing {
    publications {
        val publication = register<MavenPublication>("maven") {
            from(components["java"])
            afterEvaluate {
                artifactId = project.getOptionalExtra("artifact-id") ?: project.name
            }
        }

        bintray {
            setPublications(publication.name)
        }
    }
}

fun Project.getOptionalExtra(key: String): String? {
    return if (extra.has(key)) {
        (extra[key] as? String)?.let { if (it.isBlank()) null else it }
    } else {
        null
    }
}
