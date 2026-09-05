// Root build — versions pinned for the Temple Stack application modules.
plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("android") version "2.1.20" apply false
    id("com.android.application") version "8.9.2" apply false
}

allprojects {
    group = "studio.cortex.zeustemplestack"
    version = "1.0.0"
}
