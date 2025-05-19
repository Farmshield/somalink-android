package library

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import config.AppConfig
import extensions.addComposeDependencies
import extensions.addCoreAndroidDependencies
import extensions.addHiltDependencies
import extensions.addRetrofitDependencies
import extensions.addTestDependencies
import extensions.addWorkManagerDependencies
import extensions.implementCoreModules
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import plugins.Plugins
import java.io.File
import java.util.Properties

class ApplicationModulePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            applyPlugins()
            configureAndroid()
            configureDependencies()
        }
    }

    private fun Project.applyPlugins() {
        plugins.apply(Plugins.androidApplication)
        plugins.apply(Plugins.kotlinAndroid)
        plugins.apply(Plugins.kotlinParcelize)
        plugins.apply(Plugins.kotlinxSerialization)
        plugins.apply(Plugins.ksp)
        plugins.apply(Plugins.hiltAndroid)
        plugins.apply(Plugins.compose)
    }

    private fun Project.configureAndroid() {
        val property = loadProperties()
        val prodBackendUrl = getPropertyOrThrow(property, "PROD_BACKEND_URL")
        val devBackendUrl = getPropertyOrThrow(property, "DEV_BACKEND_URL")
        val stagingBackendUrl = getPropertyOrThrow(property, "STAGING_BACKEND_URL")

        extensions.configure<BaseAppModuleExtension> {
            compileSdk = AppConfig.compileSdk


            defaultConfig {
                applicationId = "com.newton.somalink"
                minSdk = AppConfig.minSdk
                targetSdk = AppConfig.targetSdk
                versionCode = AppConfig.versionCode
                versionName = AppConfig.versionName
                multiDexEnabled = true
                testInstrumentationRunner = AppConfig.testInstrumentationRunner
            }

            configureSigningConfigs(property)
            configureBuildTypes(prodBackendUrl, devBackendUrl, stagingBackendUrl)
            configureProductFlavors()

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }

            buildFeatures {
                compose = true
                buildConfig = true
            }
        }
    }

    private fun Project.loadProperties(): Properties {
        val property = Properties()
        val store = rootProject.file("keys.properties")

        if (store.exists()) {
            property.load(store.inputStream())
            return property
        } else {
            throw GradleException("keys.properties file not found")
        }
    }

    private fun getPropertyOrThrow(property: Properties, key: String): String {
        return property.getProperty(key) ?: throw GradleException("$key not found in keys.properties")
    }

    private fun AppExtension.configureSigningConfigs(property: Properties) {
        signingConfigs.create("release") {
            val keystoreFile = getPropertyOrThrow(property, "RELEASE_STORE_FILE")
            val keystorePassword = getPropertyOrThrow(property, "RELEASE_STORE_PASSWORD")
            val keyalias = getPropertyOrThrow(property, "RELEASE_KEY_ALIAS")
            val keyaliasPassword = getPropertyOrThrow(property, "RELEASE_KEY_PASSWORD")

            storeFile = File(keystoreFile)
            storePassword = keystorePassword
            keyAlias = keyalias
            keyPassword = keyaliasPassword
        }
    }

    private fun AppExtension.configureBuildTypes(
        prodBackendUrl: String,
        devBackendUrl: String,
        stagingBackendUrl: String
    ) {
        buildTypes {
            getByName("debug") {
                isMinifyEnabled = false
                isShrinkResources = false
                buildConfigField("String", "BACKEND_URL", "\"$devBackendUrl\"")
            }
            create("stagging") {
                isMinifyEnabled = true
                isShrinkResources = false
                buildConfigField("String", "BACKEND_URL", "\"$stagingBackendUrl\"")
            }
            getByName("release") {
                signingConfig = signingConfigs.getByName("release")
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
                buildConfigField("String", "BACKEND_URL", "\"$prodBackendUrl\"")
            }
        }
    }

    private fun AppExtension.configureProductFlavors() {
        flavorDimensions("appStatus")
        productFlavors {
            create("production") {
                applicationIdSuffix = ""
                dimension = "appStatus"
                manifestPlaceholders["appName"] = "somalink"
            }

            create("dev") {
                applicationIdSuffix = ".dev"
                dimension = "appStatus"
                manifestPlaceholders["appName"] = "[DEV] somalink"
            }

            create("staging") {
                applicationIdSuffix = ".stg"
                dimension = "appStatus"
                manifestPlaceholders["appName"] = "[STG] somalink"
            }
        }
    }

    private fun Project.configureDependencies() {
        dependencies {
            addCoreAndroidDependencies()
            addComposeDependencies()
            addTestDependencies()
            addHiltDependencies()
            addRetrofitDependencies()
            addWorkManagerDependencies()
            implementCoreModules()
        }
    }

}