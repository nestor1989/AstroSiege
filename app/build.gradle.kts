plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nestordev.astrosiege"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nestordev.astrosiege"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Distinct application id suffix so debug + release can be installed side by side,
            // and so AdMob's own "test device" mode is easy to reason about while developing.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // AdMob (Google Mobile Ads SDK) — banner, interstitial and rewarded ads.
    // Real App ID / ad unit IDs are set in AndroidManifest.xml and AdManager.kt.
    implementation("com.google.android.gms:play-services-ads:23.5.0")

    // Google Play Billing — used for the one-time "remove ads" purchase.
    // v8.3.0: Play Console now requires Billing Library >= 8.0.0 (policy deadline
    // Aug 31, 2026). The KTX calls in BillingManager.kt already used the Params-based
    // signatures (PendingPurchasesParams, QueryProductDetailsParams, QueryPurchasesParams)
    // that v8 requires, so this was a pure version bump — no source changes needed.
    implementation("com.android.billingclient:billing-ktx:8.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
