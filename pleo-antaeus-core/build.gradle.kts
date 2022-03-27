plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    implementation("org.apache.kafka:kafka-clients:3.1.0")
    api(project(":pleo-antaeus-models"))
}