import org.gradle.internal.os.OperatingSystem

plugins {
    application
    kotlin("jvm") version "1.4.32"
    id("org.beryx.jlink") version "2.23.7"
}

group = "com.icuxika"
version = "1.0.0"

val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
val compileJava: JavaCompile by tasks
compileJava.destinationDir = compileKotlin.destinationDir

application {
    applicationName = "JavaFXSample"
    mainModule.set("sample")
    mainClass.set("com.icuxika.MainAppKt")
    applicationDefaultJvmArgs = listOf(
        // Java16的ZGC似乎有大幅度优化
        "-XX:+UseZGC",
        // 当遇到空指针异常时显示更详细的信息
        "-XX:+ShowCodeDetailsInExceptionMessages",
        "-Dsun.java2d.opengl=true",
        // 不添加此参数，打包成exe后，https协议的网络图片资源无法加载
        "-Dhttps.protocols=TLSv1.1,TLSv1.2",
        "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=com.jfoenix"
    )
}

val platform = when {
    OperatingSystem.current().isWindows -> {
        "win"
    }
    OperatingSystem.current().isMacOsX -> {
        "mac"
    }
    else -> {
        "linux"
    }
}

jlink {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))

    launcher {
        name = application.applicationName
        imageName.set(application.applicationName)
    }

    imageZip.set(project.file("${project.buildDir}/image-zip/JavaFXSample.zip"))

    jpackage {
        outputDir = "build-package"
        imageName = application.applicationName
        skipInstaller = false
        installerName = application.applicationName
        appVersion = version.toString()

        application.applicationDefaultJvmArgs.forEach {
            jvmArgs.add(it)
        }

        when {
            OperatingSystem.current().isWindows -> {
                icon = "src/main/resources/application.ico"
                installerOptions =
                    listOf("--win-dir-chooser", "--win-menu", "--win-shortcut", "--install-dir", "Shimmer")
            }
            OperatingSystem.current().isMacOsX -> {
                icon = "src/main/resources/application.icns"
            }
            else -> {
                icon = "src/main/resources/application.png"
                installerOptions = listOf(
                    "--linux-deb-maintainer",
                    "icuxika@outlook.com",
                    "--linux-menu-group",
                    application.applicationName,
                    "--linux-shortcut"
                )
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    implementation("org.openjfx:javafx-base:16:${platform}")
    implementation("org.openjfx:javafx-controls:16:${platform}")
    implementation("org.openjfx:javafx-graphics:16:${platform}")
    implementation("org.openjfx:javafx-fxml:16:${platform}")
    implementation("org.openjfx:javafx-swing:16:${platform}")
    implementation("org.openjfx:javafx-media:16:${platform}")
    implementation("org.openjfx:javafx-web:16:${platform}")
    implementation("com.jfoenix:jfoenix:9.0.10")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.register<Copy>("copyDependencies") {
    from(configurations.getByName("runtimeClasspath"))
    into("$buildDir/modules")
}

tasks.register<Exec>("package2Image") {
    dependsOn("build", "copyDependencies")

    val iconPath = when {
        OperatingSystem.current().isWindows -> {
            "$projectDir/src/main/resources/application.ico"
        }
        OperatingSystem.current().isMacOsX -> {
            "$projectDir/src/main/resources/application.icns"
        }
        else -> {
            "$projectDir/src/main/resources/application.png"
        }
    }

    commandLine("jpackage")
    args(
        "-n",
        application.applicationName,
        "-t",
        "app-image",
        "--java-options",
        application.applicationDefaultJvmArgs.joinToString(separator = " "),
        "-p",
        "$buildDir/modules" + File.pathSeparator + "$buildDir/libs",
        "-d",
        "$buildDir/package/image",
        "-m",
        "${application.mainModule.get()}/${application.mainClass.get()}",
        "--icon",
        iconPath,
        "--app-version",
        "$version"
    )
}

tasks.register<Exec>("package2Installer") {
    dependsOn("build", "copyDependencies")

    val installerType = when {
        OperatingSystem.current().isWindows -> {
            "msi"
        }
        OperatingSystem.current().isMacOsX -> {
            "dmg"
        }
        else -> {
            "deb"
        }
    }

    val iconPath = when {
        OperatingSystem.current().isWindows -> {
            "$projectDir/src/main/resources/application.ico"
        }
        OperatingSystem.current().isMacOsX -> {
            "$projectDir/src/main/resources/application.icns"
        }
        else -> {
            "$projectDir/src/main/resources/application.png"
        }
    }

    val argsList = arrayListOf(
        "-n",
        application.applicationName,
        "-t",
        installerType,
        "--java-options",
        application.applicationDefaultJvmArgs.joinToString(separator = " "),
        "-p",
        "$buildDir/modules" + File.pathSeparator + "$buildDir/libs",
        "-d",
        "$buildDir/package/installer",
        "-m",
        "${application.mainModule.get()}/${application.mainClass.get()}",
        "--icon",
        iconPath,
        "--app-version",
        "$version"
    )

    val winInstallerOptionList = listOf(
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut",
        "--win-menu-group",
        application.applicationName
    )

    val linuxInstallerOptionList = listOf(
        "--linux-deb-maintainer",
        "icuxika@outlook.com",
        "--linux-menu-group",
        application.applicationName,
        "--linux-shortcut"
    )

    if (OperatingSystem.current().isWindows) {
        argsList.addAll(winInstallerOptionList)
    }

    if (OperatingSystem.current().isLinux) {
        argsList.addAll(linuxInstallerOptionList)
    }

    commandLine("jpackage")
    args(argsList)
}