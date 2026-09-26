plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.planner.ui"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.planner)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    // automatic backups run in the background
    implementation(libs.androidx.work)
}
