import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sciview.implementation
import sciview.joglNatives
import java.net.URL
import sciview.*

plugins {
    val ktVersion = "1.5.10"
    val dokkaVersion = "1.4.32"

    java
    kotlin("jvm") version ktVersion
    kotlin("kapt") version ktVersion
    sciview.publish
    sciview.sign
    id("org.jetbrains.dokka") version dokkaVersion
    jacoco
    id("sciJava.platform") version "30.0.0+15"
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://maven.scijava.org/content/groups/public")
    maven("https://raw.githubusercontent.com/kotlin-graphics/mary/master")
    maven("https://jitpack.io")
}

dependencies {

    // Graphics dependencies

    annotationProcessor(sciJava.common)
    kapt(sciJava.common)

    val sceneryVersion = "e1e0b7e"
    api("graphics.scenery:scenery:$sceneryVersion")
    // check if build is triggered on https://jitpack.io/#scenerygraphics/sciview `build` tab
    // if not, uncomment this only to trigger it
//    api("com.github.scenerygraphics:scenery:$scenery")

    // This seams to be still necessary
    implementation(platform("org.lwjgl:lwjgl-bom:3.2.3"))

    implementation(misc.cleargl)
    implementation(misc.coreMem)
    implementation(jogamp.jogl, joglNatives)

    implementation("com.formdev:flatlaf:0.38")

    // SciJava dependencies

    implementation(sciJava.common)
    implementation(sciJava.uiBehaviour)
    implementation(sciJava.scriptEditor)
    implementation(sciJava.uiSwing)
    implementation(sciJava.uiAwt)
    implementation(sciJava.search)
    implementation(sciJava.scriptingJython)
    implementation(migLayout.swing)

    // ImageJ dependencies

    implementation(imagej.core)
    //    sciJava("net.imagej") {
    //        exclude("org.scijava", "scripting-renjin")
    //        exclude("org.scijava", "scripting-jruby")
    //    }
    implementation(imagej.common)
    implementation(imagej.mesh) { version { strictly("0.8.1") } } // FIXME
    implementation(imagej.meshIo)
    implementation(imagej.ops)
    implementation(imagej.launcher)
    implementation(imagej.uiSwing)
    implementation(imagej.legacy)
    implementation(scifio.core)
    implementation(scifio.bfCompat)

    // ImgLib2 dependencies
    implementation(imgLib2.core)
    implementation(imgLib2.roi)

    // Math dependencies
    implementation(commons.math3)
    implementation(misc.joml)

    // Kotlin dependencies

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")

    // Test scope

    testImplementation(misc.junit4)
    implementation(imagej.ij)
    implementation(imgLib2.ij)

    implementation(n5.core)
    implementation(n5.hdf5)
    implementation(n5.imglib2)
    implementation(bigDataViewer.spimData)

    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(kotlin("test-junit"))

    implementation(bigDataViewer.core)
    implementation(bigDataViewer.visTools)
}

kapt {
    useBuildCache = false // safe
    arguments {
        arg("-Werror")
        arg("-Xopt-in", "kotlin.RequiresOptIn")
    }
}

tasks {
    withType<KotlinCompile>().all {
        val version = System.getProperty("java.version").substringBefore('.').toInt()
        val default = if (version == 1) "1.8" else "$version"
        kotlinOptions {
            jvmTarget = project.properties["jvmTarget"]?.toString() ?: default
            freeCompilerArgs += listOf("-Xinline-classes", "-Xopt-in=kotlin.RequiresOptIn")
        }
        sourceCompatibility = project.properties["sourceCompatibility"]?.toString() ?: default
    }
    test {
        finalizedBy(jacocoTestReport) // report is always generated after tests run
    }
    jar {
        archiveVersion.set(rootProject.version.toString())
    }

    dokkaHtml {
        enabled = false
        dokkaSourceSets.configureEach {
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("https://github.com/scenerygraphics/sciview/tree/master/src/main/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }

    dokkaJavadoc {
        enabled = false
    }

    jacocoTestReport {
        reports {
            xml.isEnabled = true
            html.apply {
                isEnabled = false
                //destination = file("$buildDir/jacocoHtml")
            }
        }
        dependsOn(test) // tests are required to run before generating the report
    }

    register("runMain", JavaExec::class.java) {
        classpath = sourceSets.main.get().runtimeClasspath

        main = "sc.iview.Main"

        val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

        val additionalArgs = System.getenv("SCENERY_JVM_ARGS")
        allJvmArgs = if (additionalArgs != null) {
            allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") } + additionalArgs
        } else {
            allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
        }
    }

    register("runImageJMain", JavaExec::class.java) {
        classpath = sourceSets.main.get().runtimeClasspath

        main = "sc.iview.ImageJMain"

        val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

        val additionalArgs = System.getenv("SCENERY_JVM_ARGS")
        allJvmArgs = if (additionalArgs != null) {
            allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") } + additionalArgs
        } else {
            allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
        }
    }

    sourceSets.main.get().allSource.files
        .filter { it.path.contains("demo") && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
        .map {
            val p = it.path
            if(p.endsWith(".kt")) {
                p.substringAfter("kotlin${File.separatorChar}").replace(File.separatorChar, '.').substringBefore(".kt")
            } else {
                p.substringAfter("java${File.separatorChar}").replace(File.separatorChar, '.').substringBefore(".java")
            }
        }
        .forEach { className ->
            println("Working on $className")
            val exampleName = className.substringAfterLast(".")
            val exampleType = className.substringBeforeLast(".").substringAfterLast(".")

            println("Registering $exampleName of $exampleType")
            register<JavaExec>(name = className.substringAfterLast(".")) {
                classpath = sourceSets.test.get().runtimeClasspath
                main = className
                group = "demos.$exampleType"

                val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

                val additionalArgs = System.getenv("SCENERY_JVM_ARGS")
                allJvmArgs = if (additionalArgs != null) {
                    allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") } + additionalArgs
                } else {
                    allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
                }
            }
        }

    register<JavaExec>(name = "run") {
        classpath = sourceSets.main.get().runtimeClasspath
        if (project.hasProperty("target")) {
            project.property("target")?.let { target ->
                classpath = sourceSets.test.get().runtimeClasspath

                println("Target is $target")
//                if(target.endsWith(".kt")) {
//                    main = target.substringAfter("kotlin${File.separatorChar}").replace(File.separatorChar, '.').substringBefore(".kt")
//                } else {
//                    main = target.substringAfter("java${File.separatorChar}").replace(File.separatorChar, '.').substringBefore(".java")
//                }

                main = "$target"
                val props = System.getProperties().filter { (k, _) -> k.toString().startsWith("scenery.") }

                val additionalArgs = System.getenv("SCENERY_JVM_ARGS")
                allJvmArgs = if (additionalArgs != null) {
                    allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") } + additionalArgs
                } else {
                    allJvmArgs + props.flatMap { (k, v) -> listOf("-D$k=$v") }
                }

                println("Will run target $target with classpath $classpath, main=$main")
                println("JVM arguments passed to target: $allJvmArgs")
          }
        }
    }
}

val dokkaJavadocJar by tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.get().outputDirectory.get())
    archiveClassifier.set("javadoc")
}

val dokkaHtmlJar by tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.get().outputDirectory.get())
    archiveClassifier.set("html-doc")
}

jacoco {
    toolVersion = "0.8.7"
}

artifacts {
    archives(dokkaJavadocJar)
    archives(dokkaHtmlJar)
}

java.withSourcesJar()

