plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-android-extensions")
    id("kotlin-kapt")
    id("maven")
}

android {
    compileSdkVersion(28)



    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(28)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
//        getByName("main").kotlin.srcDirs("src/main/java")
        getByName("main").java.srcDirs("src/main/java")
        getByName("main").java.srcDirs("../m4m/android/src/main/java")
        getByName("main").java.srcDirs("../m4m/domain/src/main/java")
        getByName("main").java.srcDirs("../m4m/effects/src/main/java")
    }

    dataBinding {
        isEnabled = true
    }
}
dependencies {
    val androidx_compat_version:String by project
    val androidx_constraint_version:String by project
    val androidx_test_runner_version:String by project
    val androidx_test_espresso_version:String by project
    val kotlin_version:String by project
    val coroutine_version:String by project
    val androidx_media_version:String by project
    val exoplayer_version:String by project
    val okhttp_version:String by project

    implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))

    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.appcompat:appcompat:$androidx_compat_version")
    implementation("androidx.constraintlayout:constraintlayout:$androidx_constraint_version")
    implementation("androidx.media:media:$androidx_media_version")
    implementation("androidx.recyclerview:recyclerview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.0.0")

    // Unit Test
    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test:runner:$androidx_test_runner_version")
    androidTestImplementation("androidx.test.espresso:espresso-core:$androidx_test_espresso_version")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutine_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutine_version")

    // Parceler
    // https://github.com/johncarl81/parceler
//    implementation("org.parceler:parceler-api:1.1.12")
//    kapt "org.parceler:parceler:1.1.12"

    // ExoPlayer
    implementation("com.google.android.exoplayer:exoplayer-core:$exoplayer_version")
    implementation("com.google.android.exoplayer:exoplayer-ui:$exoplayer_version")
//      implementation("com.google.android.exoplayer:exoplayer-dash:$exoplayer_version")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:$okhttp_version")

    implementation(project("path" to ":utils"))

    // Blurry
    // implementation("jp.wasabeef:blurry:2.1.1")

    // ExoPlayer
    implementation("com.google.android.exoplayer:exoplayer-core:$exoplayer_version")
    implementation("com.google.android.exoplayer:exoplayer-ui:$exoplayer_version")
//      implementation("com.google.android.exoplayer:exoplayer-dash:$exoplayer_version")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:3.12.0")
}


repositories {
    mavenCentral()
}
