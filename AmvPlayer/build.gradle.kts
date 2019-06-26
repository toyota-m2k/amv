// Top-level build file where you can add configuration options common to all sub-projects/modules.
@file:Suppress("LocalVariableName")

buildscript {
    val kotlin_version by extra("1.3.40")
    val coroutine_version by extra("1.2.1")
    val support_lib_version by extra("28.0.0")
    val lifecycle_version by extra("1.1.1")     // com.android.support.constraint:constraint-layout
    val c_layout_version by extra("1.1.3")      // constraint-layout
    val exoplayer_version by extra("2.8.2")
    val okhttp_version by extra("3.12.0")

    val androidx_compat_version by extra("1.0.2")
    val androidx_media_version by extra("1.0.1")
    val androidx_test_runner_version by extra("1.2.0")
    val androidx_test_espresso_version by extra("3.2.0")
    val androidx_constraint_version by extra("1.1.3")

    val permission_dispatcher_version by extra("4.3.1")

    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
