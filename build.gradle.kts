plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    kotlinScriptAdditionalPaths {
        include(
            fileTree("buildSrc") {
                include("*.kts", "src/**/*.kts")
            },
        )
    }
}
