@file:Suppress("LocalVariableName")

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-android-extensions")
    id("kotlin-kapt")
}

android {
    compileSdkVersion(28)

    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(28)
        versionCode =1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release"){
            isMinifyEnabled = false
            proguardFiles( getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    val androidx_compat_version:String by project
    val androidx_constraint_version:String by project
    val androidx_test_runner_version:String by project
    val androidx_test_espresso_version:String by project
    val kotlin_version:String by project

    implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))

    implementation("androidx.appcompat:appcompat:$androidx_compat_version")
    implementation("androidx.constraintlayout:constraintlayout:$androidx_constraint_version")

    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test:runner:$androidx_test_runner_version")
    androidTestImplementation("androidx.test.espresso:espresso-core:$androidx_test_espresso_version")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")

    // Parceler
    // https://github.com/johncarl81/parceler
//    implementation("org.parceler:parceler-api:1.1.12")
//    kapt("org.parceler:parceler:1.1.12")
}
repositories {
    mavenCentral()
}
