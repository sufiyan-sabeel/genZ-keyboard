import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------- version
// One source of truth: version.properties at the repository root, overridable with
// -PversionName / -PversionCode so CI can pass the tag it is building. The release workflow
// writes the same numbers into the GitHub release body, which is what the in-app update check
// reads, so the three can no longer drift apart. scripts/check-release-version.sh enforces it.
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// -P wins over the file, so CI can build an exact tag; the file wins over nothing, so a plain
// ./gradlew build always produces the same version a maintainer sees in the repository.
val appVersionName: String =
    (project.findProperty("versionName") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: versionProps.getProperty("versionName")?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("no versionName in version.properties and no -PversionName given")

val rawVersionCode: String =
    (project.findProperty("versionCode") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: versionProps.getProperty("versionCode")?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("no versionCode in version.properties and no -PversionCode given")

val appVersionCode: Int = rawVersionCode.toIntOrNull()
    ?: throw GradleException("versionCode must be an integer, got '$rawVersionCode'")
if (appVersionCode <= 0) throw GradleException("versionCode must be greater than zero")

android {
    namespace = "com.genz.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.genz.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables {
            useSupportLibrary = true
        }
        // only the resource languages the app actually ships stay in the APK
        resourceConfigurations += listOf("ru", "en")

        // ключи GIF-сервисов — из local.properties (он не попадает в git): KLIPY_API_KEY=… и/или GIPHY_API_KEY=…
        val local = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "KLIPY_API_KEY", "\"${local.getProperty("KLIPY_API_KEY", "").trim()}\"")
        // Community backend. Only the PUBLISHABLE anon key is shipped; row-level security in
        // supabase/schema.sql is what makes that safe. Absent values leave the feature disabled.
        buildConfigField("String", "SUPABASE_URL", "\"${local.getProperty("SUPABASE_URL", "").trim()}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${local.getProperty("SUPABASE_ANON_KEY", "").trim()}\"")
        buildConfigField("String", "GIPHY_API_KEY", "\"${local.getProperty("GIPHY_API_KEY", "").trim()}\"")
        // instrumentation tests need a device; they are kept apart from the JVM unit tests
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // своя подпись релиза — если в local.properties (он не попадает в git) указан keystore:
    // RELEASE_STORE_FILE=…, RELEASE_STORE_PASSWORD=…, RELEASE_KEY_ALIAS=…, RELEASE_KEY_PASSWORD=…
    // Без него релиз подписывается отладочным ключом, как раньше (иначе уже установленные копии не обновятся).
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val releaseStore = localProps.getProperty("RELEASE_STORE_FILE")?.let { rootProject.file(it) }?.takeIf { it.exists() }
    signingConfigs {
        getByName("debug") {
            enableV2Signing = true
            enableV3Signing = true
        }
        if (releaseStore != null) create("release") {
            storeFile = releaseStore
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            // ревизия git и путь к проекту не попадают в APK
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // No silent debug fallback. Without the production keystore this variant produces an
            // UNSIGNED apk, which cannot be installed by anyone - the signed-release workflow
            // refuses to continue in that state. Falling back to the debug key here would produce
            // an APK that looks installable but breaks every future update.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // API Material 3 Expressive (MaterialShapes, MotionScheme.expressive, LoadingIndicator) пока помечены экспериментальными
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
    }

    // Two distributions from one codebase.
    //   standard - GitHub Releases: everything, including the anime artwork.
    //   fdroid    - the same keyboard without assets whose redistribution rights are unresolved.
    //               The ten themes still ship; with no bundled picture they fall back to the
    //               palette that was sampled from that artwork, so nothing looks broken.
    flavorDimensions += "distribution"
    productFlavors {
        create("standard") { dimension = "distribution" }
        create("fdroid") { dimension = "distribution" }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // проверки качества исправлений и свайпа гоняются на компьютере, без телефона
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // lint-проверки из material3 1.5 (Expressive) собраны под более новый AGP и роняют lintVital — сборку не прерываем
        checkReleaseBuilds = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.version"
            // kotlin/*.kotlin_builtins не вырезаем: без них не работает Jackson в SDK Anthropic
            excludes += "DebugProbesKt.bin"
            // служебные файлы лицензий из библиотек SDK Anthropic (Jackson, Apache HttpClient)
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/{LICENSE,LICENSE.txt,LICENSE.md,NOTICE,NOTICE.txt,NOTICE.md}"
            excludes += "META-INF/FastDoubleParser-*"
        }
    }
}

composeCompiler {
    // меньше лишних перекомпозиций: лямбды и нестабильные параметры тоже пропускаются, если не изменились
    enableStrongSkippingMode = true
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha13")
    implementation("androidx.compose.material:material-icons-extended")

    // пароли и логины из Google Паролей (и других служб автозаполнения) в строке подсказок, как в Gboard
    implementation("androidx.autofill:autofill:1.1.0")

    // исправление текста с помощью Claude (ключ API пользователя)
    implementation("com.anthropic:anthropic-java:2.34.0")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    // JavaScript для модов (плагинов со скриптами): чистая Java, работает в режиме интерпретатора
    implementation("org.mozilla:rhino:1.7.15")

    // ставит baseline-профили Compose при установке: быстрее первый показ клавиатуры и прокрутка
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    testImplementation("junit:junit:4.13.2")
    // The platform's org.json is a stub in JVM unit tests (methods return defaults), so the real
    // implementation is needed on the test classpath for any test that parses JSON.
    testImplementation("org.json:json:20240303")

    // instrumentation only: never on the app's runtime classpath, so the F-Droid dependency gate
    // and the shipped APK are unaffected
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
