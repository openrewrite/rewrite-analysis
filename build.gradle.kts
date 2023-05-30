@Suppress("RedundantSuppression", "GradlePackageUpdate")

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.rewrite") version "latest.release"
}

group = "org.openrewrite.meta"
description = "Static code analysis APIs leveraging data flow, control flow, and other AST-based search"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()

dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-maven")
    implementation("org.openrewrite:rewrite-yaml")
    implementation("org.openrewrite:rewrite-xml")

    implementation("io.github.classgraph:classgraph:latest.release")
    implementation("org.functionaljava:functionaljava:latest.release")

    testImplementation("org.openrewrite:rewrite-test:${rewriteVersion}")
    testImplementation("org.openrewrite:rewrite-java-tck:${rewriteVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
    testImplementation("com.tngtech.archunit:archunit-junit5:latest.release")

    testImplementation("org.assertj:assertj-core:latest.release")

    testRuntimeOnly("org.openrewrite:rewrite-java-17:${rewriteVersion}")
}

tasks.withType<Javadoc> {
    // generated ANTLR sources violate doclint
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")

    // Items besides JavaParser due to lombok error which looks similar to this:
    //     openrewrite/rewrite/rewrite-java/src/main/java/org/openrewrite/java/OrderImports.java:42: error: cannot find symbol
    // @AllArgsConstructor(onConstructor_=@JsonCreator)
    //                     ^
    //   symbol:   method onConstructor_()
    //   location: @interface AllArgsConstructor
    // 1 error
    exclude("**/trait/**")
}

rewrite {
    failOnDryRunResults = true
    activeRecipe("org.openrewrite.self.Rewrite")
}

