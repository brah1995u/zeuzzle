plugins { application; kotlin("jvm") }

kotlin { jvmToolchain(17) }

application { mainClass.set("studio.cortex.thunderbound.lwjgl3.Lwjgl3LauncherKt") }

tasks.named<JavaExec>("run") {
    workingDir(rootProject.file("assets"))
    if (project.hasProperty("capture")) {
        systemProperty("capturePath", rootProject.layout.buildDirectory.file("qa/olympus-main.png").get().asFile.absolutePath)
    }
    if (project.hasProperty("captureMenu")) {
        systemProperty("captureView", "menu")
        systemProperty("capturePath", rootProject.layout.buildDirectory.file("qa/olympus-menu.png").get().asFile.absolutePath)
    }
    if (project.hasProperty("captureMap")) {
        systemProperty("captureView", "map")
        systemProperty("capturePath", rootProject.layout.buildDirectory.file("qa/olympus-map.png").get().asFile.absolutePath)
    }
    if (project.hasProperty("captureGame")) {
        systemProperty("captureView", "game")
        systemProperty("capturePath", rootProject.layout.buildDirectory.file("qa/olympus-gameplay.png").get().asFile.absolutePath)
    }
    if (project.hasProperty("captureScreen")) {
        val screen = project.property("captureScreen").toString().lowercase()
        systemProperty("captureView", screen)
        systemProperty("capturePath", rootProject.layout.buildDirectory.file("qa/olympus-$screen.png").get().asFile.absolutePath)
    }
    if (project.hasProperty("captureBonus")) {
        systemProperty("captureView", "bonus")
        systemProperty("capturePath", rootProject.layout.buildDirectory.file("qa/crystal-storm.png").get().asFile.absolutePath)
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.13.1")
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:1.13.1:natives-desktop")
    runtimeOnly("com.badlogicgames.gdx:gdx-box2d-platform:1.13.1:natives-desktop")
}
