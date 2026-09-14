import java.net.URI

plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
val providerConfig = rootProject.file("../config/provider.json")
val generatedProvider = layout.buildDirectory.dir("generated/provider")
val generateProviderConfig by tasks.registering {
    inputs.file(providerConfig)
    outputs.dir(generatedProvider)
    doLast {
        val config = groovy.json.JsonSlurper().parse(providerConfig) as Map<*, *>
        val url = config["baseUrl"] as String
        val uri = URI(url)
        require(uri.scheme == "https" && uri.host != null && url.endsWith('/') && !url.contains('"') && !url.contains('\\'))
        val output = generatedProvider.get().file("fr/bonamy/sports/core/ProviderConfig.kt").asFile
        output.parentFile.mkdirs()
        output.writeText("package fr.bonamy.sports.core\n\ninternal object ProviderConfig { const val BASE = \"$url\" }\n")
    }
}
kotlin.sourceSets["main"].kotlin.srcDir(generatedProvider)
tasks.named("compileKotlin") { dependsOn(generateProviderConfig) }
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
tasks.register<JavaExec>("probe") {
    description = "Read live listings and validate a stream playlist (no URL tokens printed)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("fr.bonamy.sports.core.LiveProbeKt")
}
