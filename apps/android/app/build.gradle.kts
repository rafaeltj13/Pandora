import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val debugApiUrl = providers.gradleProperty("pandora.apiUrl")
    .orElse("http://localhost:8080").get().trimEnd('/')
val releaseApiUrl = providers.gradleProperty("pandora.releaseApiUrl")
    .orElse("").get().trimEnd('/')

fun quotedUrl(value: String): String {
    if (value.isNotEmpty()) {
        val uri = URI(value)
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrEmpty()) {
            "Pandora API URL must be an absolute HTTP(S) URL"
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Pandora API URL cannot include credentials, a query, or a fragment"
        }
    }
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

require(releaseApiUrl.isEmpty() || releaseApiUrl.startsWith("https://")) {
    "The release API URL must use HTTPS"
}

val githubClientId = providers.gradleProperty("pandora.githubClientId").orElse("").get().trim()
val githubAppSlug = providers.gradleProperty("pandora.githubAppSlug").orElse("").get().trim()
val githubLogin = providers.gradleProperty("pandora.githubLogin").orElse("").get().trim()
for (value in listOf(githubClientId, githubAppSlug, githubLogin)) {
    require(value.isEmpty() || value.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid GitHub configuration value" }
}

android {
    namespace = "dev.rafaelm.pandora"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.rafaelm.pandora"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        buildConfigField("String", "GITHUB_APP_SLUG", "\"$githubAppSlug\"")
        buildConfigField("String", "GITHUB_LOGIN", "\"$githubLogin\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", quotedUrl(debugApiUrl))
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            buildConfigField("String", "API_BASE_URL", quotedUrl(releaseApiUrl))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("test").resources.srcDir("../../../packages/contracts")
}

// This starter has no deployed API. Require an explicit URL for release builds.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enable = releaseApiUrl.isNotEmpty()
    }
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
