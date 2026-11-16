import java.security.SecureRandom
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Versi aplikasi. Server menolak versi di bawah "Versi minimum" di halaman
// Admin > Akses Siswa, jadi naikkan versi ini setiap rilis.
val appVersionName = "5.0.0"
val appVersionCode = appVersionName.split(".").map(String::toInt)
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// URL server ujian. Bisa diganti tanpa mengubah kode:
// CBT_EXAM_URL=https://... ./gradlew assembleRelease
val examUrl = (System.getenv("CBT_EXAM_URL")?.takeIf { it.isNotBlank() } ?: "https://cbtteladan.my.id")
    .trimEnd('/')

// Kunci tanda tangan request (sama dengan CBT_APP_SECRET di .env server).
// Hanya dimasukkan ke build rilis, disamarkan dengan XOR agar tidak terbaca
// langsung dengan `strings`. Tidak pernah ditulis ke repo.
val appSecret = System.getenv("CBT_APP_SECRET")?.trim().orEmpty()
if (System.getenv("CBT_REQUIRE_SECRET") == "1" && appSecret.isEmpty()) {
    throw GradleException("CBT_APP_SECRET wajib diisi untuk build rilis.")
}
val secretMask = ByteArray(32).also { SecureRandom().nextBytes(it) }
val secretMasked = appSecret.toByteArray(Charsets.UTF_8)
    .mapIndexed { i, b -> (b.toInt() xor secretMask[i % secretMask.size].toInt()).toByte() }
    .toByteArray()
fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

val keystoreFile = System.getenv("CBT_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "id.my.cbtteladan"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.my.cbtteladan"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField("String", "EXAM_URL", "\"$examUrl\"")
        buildConfigField("String", "SECRET_DATA", "\"\"")
        buildConfigField("String", "SECRET_MASK", "\"\"")
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("CBT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CBT_KEY_ALIAS")
                keyPassword = System.getenv("CBT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "SECRET_DATA", "\"${b64(secretMasked)}\"")
            buildConfigField("String", "SECRET_MASK", "\"${b64(secretMask)}\"")
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
}
