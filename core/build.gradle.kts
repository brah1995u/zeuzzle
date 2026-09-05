plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":engine"))
    api("com.badlogicgames.gdx:gdx:1.13.1")
    api("com.badlogicgames.gdx:gdx-box2d:1.13.1")
}
