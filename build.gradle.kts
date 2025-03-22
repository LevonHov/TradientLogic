plugins {
    id("java")
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")  // JSON parser
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")  // YAML parser
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20210307")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("org.example.BnbFeeTest")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runBnbTest") {
    group = "application"
    description = "Runs the BNB Fee Test"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.example.BnbFeeTest")
}

tasks.register<JavaExec>("runRiskDemo") {
    group = "application"
    description = "Runs the improved Risk Calculator Demo"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.example.demo.RiskCalculatorDemo")
}