plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.laneby.wallpaperpicker"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.laneby.wallpaperpicker"
        minSdk = 31
        targetSdk = 36
        versionCode = 14
        versionName = "2.342.1.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    val activity_version = "1.11.0"

    //noinspection UseTomlInstead
    implementation("androidx.activity:activity:$activity_version")
    implementation ("com.geyifeng.immersionbar:immersionbar:3.2.2")
    implementation("com.geyifeng.immersionbar:immersionbar-ktx:3.2.2")

    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}