import org.gradle.api.file.DuplicatesStrategy

plugins { id("com.android.application"); kotlin("android") }

val arm64Natives by configurations.creating
val armv7Natives by configurations.creating

android {
    namespace = "studio.cortex.zeustemplestack"
    compileSdk = 36
    defaultConfig {
        applicationId = "studio.cortex.zeustemplestack"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.13.1")
    implementation("com.badlogicgames.gdx:gdx-box2d:1.13.1")
    armv7Natives("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-armeabi-v7a")
    armv7Natives("com.badlogicgames.gdx:gdx-box2d-platform:1.13.1:natives-armeabi-v7a")
    arm64Natives("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-arm64-v8a")
    arm64Natives("com.badlogicgames.gdx:gdx-box2d-platform:1.13.1:natives-arm64-v8a")
}

tasks.register<Copy>("copyAndroidNatives") {
    from(armv7Natives.map { zipTree(it) }) { include("*.so"); into("armeabi-v7a") }
    from(arm64Natives.map { zipTree(it) }) { include("*.so"); into("arm64-v8a") }
    into(layout.buildDirectory.dir("generated/jniLibs"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
android.sourceSets.getByName("main").jniLibs.srcDirs(layout.buildDirectory.dir("generated/jniLibs"))
android.sourceSets.getByName("main").assets.srcDirs(rootProject.file("assets"))
tasks.named("preBuild").configure { dependsOn("copyAndroidNatives") }
