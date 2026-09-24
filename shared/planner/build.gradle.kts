plugins {
    id("ivy.module")
    id("ivy.room")
}

android {
    namespace = "com.ivy.planner"
}

dependencies {
    implementation(projects.shared.base)
}
