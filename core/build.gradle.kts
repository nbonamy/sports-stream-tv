plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
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
