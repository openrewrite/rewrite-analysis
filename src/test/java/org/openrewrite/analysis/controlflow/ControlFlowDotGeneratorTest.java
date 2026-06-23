/*
 * Copyright 2022 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.analysis.controlflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.openrewrite.java.Assertions.java;

/**
 * Generates Graphviz DOT files for control flow graphs.
 *
 * <p>Skipped by default; runs only when the system property {@code cfg.dot.output.dir} is set.
 *
 * <p>Usage:
 * <pre>
 *   # Generate all DOT files:
 *   ./gradlew test --tests "*.ControlFlowDotGeneratorTest" -Dcfg.dot.output.dir=build/cfg-dot
 *
 *   # Then convert to PNG:
 *   for f in build/cfg-dot/*.dot; do dot -Tpng -o "${f%.dot}.png" "$f"; done
 * </pre>
 *
 * <p>Add new test methods here (following the {@code generateDot(name, source)} pattern) to produce
 * DOT files for any Java snippet without touching the main test suite.
 */
class ControlFlowDotGeneratorTest implements RewriteTest {

    @BeforeEach
    void assumeDotOutputEnabled() {
        assumeTrue(
          System.getProperty("cfg.dot.output.dir") != null,
          "Skipped: set -Dcfg.dot.output.dir=<path> to generate DOT files"
        );
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ControlFlowVisualization(true, false))
          .expectedCyclesThatMakeChanges(1).cycles(1);
    }

    @Test
    void simpleTryCatch() {
        generateDot("simple-try-catch",
          //language=java
          """
            class Test {
                void test(boolean flag) {
                    int n = 1;
                    try {
                        System.out.println(n);
                    } catch (RuntimeException e) {
                        System.out.println(e.getMessage());
                    }
                    System.out.println("done");
                }
            }
            """);
    }

    @Test
    void tryCatchFinally() {
        generateDot("try-catch-finally",
          //language=java
          """
            import java.io.InputStream;
            class Test {
                InputStream source() { return null; }
                int test() {
                    InputStream source = source();
                    try {
                        return source.read();
                    } catch (RuntimeException e) {
                        System.out.println(e.getMessage());
                        return -1;
                    } finally {
                        source.close();
                    }
                }
            }
            """);
    }

    @Test
    void tryCatchMultipleCatchClauses() {
        generateDot("try-multi-catch",
          //language=java
          """
            import java.io.*;
            class Test {
                void test() {
                    try {
                        InputStream in = new FileInputStream("file.txt");
                        System.out.println(in.read());
                    } catch (FileNotFoundException e) {
                        System.err.println("not found: " + e.getMessage());
                    } catch (IOException e) {
                        System.err.println("io error: " + e.getMessage());
                    } finally {
                        System.out.println("done");
                    }
                }
            }
            """);
    }

    @Test
    void tryWithSingleResource() {
        generateDot("try-with-single-resource",
          //language=java
          """
            import java.io.*;
            class Test {
                void test() {
                    try (InputStream in = new FileInputStream("file.txt")) {
                        System.out.println(in.read());
                    } catch (IOException e) {
                        System.err.println("error: " + e.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void tryWithMultipleResources() {
        generateDot("try-with-multiple-resources",
          //language=java
          """
            import java.io.*;
            class Test {
                void test() {
                    try (InputStream in = new FileInputStream("file.txt");
                         OutputStream out = new FileOutputStream("out.txt")) {
                        out.write(in.read());
                    } catch (IOException e) {
                        System.err.println("error: " + e.getMessage());
                    }
                }
            }
            """);
    }

    // --- Coverage: branches inside the no-catch-has-finally path (lines 914 & 918) ---

    @Test
    void breakInTryBodyNoCatchWithFinally() {
        generateDot("break-in-try-no-catch-finally",
          //language=java
          """
            class Test {
                void test() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            if (i == 5) break;
                            System.out.println(i);
                        } finally {
                            System.out.println("finally");
                        }
                    }
                }
            }
            """);
    }

    @Test
    void continueInTryBodyNoCatchWithFinally() {
        generateDot("continue-in-try-no-catch-finally",
          //language=java
          """
            class Test {
                void test() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            if (i == 5) continue;
                            System.out.println(i);
                        } finally {
                            System.out.println("finally");
                        }
                    }
                }
            }
            """);
    }

    // --- Coverage: allCurrents empty (line 1013 else) and allExitFlow (line 1019) ---

    @Test
    void allPathsReturnInTryCatchFinally() {
        generateDot("all-paths-return-try-catch-finally",
          //language=java
          """
            class Test {
                int test() {
                    try {
                        return 1;
                    } catch (RuntimeException e) {
                        return -1;
                    } finally {
                        System.out.println("cleanup");
                    }
                }
            }
            """);
    }

    @Test
    void returnInCatchBodyWithFinally() {
        generateDot("return-in-catch-with-finally",
          //language=java
          """
            class Test {
                int test() {
                    try {
                        System.out.println("try");
                    } catch (RuntimeException e) {
                        return -1;
                    } finally {
                        System.out.println("cleanup");
                    }
                    return 0;
                }
            }
            """);
    }

    @Test
    void catchContinue() {
        generateDot("catch-continue",
          //language=java
          """
            class Test {
                void test() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            System.out.println(i);
                        } catch (RuntimeException e) {
                            continue;
                        }
                        System.out.println("after try");
                    }
                }
            }
            """);
    }

    @Test
    void catchBreak() {
        generateDot("catch-break",
          //language=java
          """
            class Test {
                void test() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            System.out.println(i);
                        } catch (RuntimeException e) {
                            break;
                        }
                        System.out.println("after try");
                    }
                }
            }
            """);
    }

    @Test
    void finallyContinue() {
        generateDot("finally-continue",
          //language=java
          """
            class Test {
                void test() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            System.out.println(i);
                        } catch (RuntimeException e) {
                            System.out.println("caught: " + e);
                        } finally {
                            continue;
                        }
                    }
                }
            }
            """);
    }

    @Test
    void finallyBreak() {
        generateDot("finally-break",
          //language=java
          """
            class Test {
                void test() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            System.out.println(i);
                        } catch (RuntimeException e) {
                            System.out.println("caught: " + e);
                        } finally {
                            break;
                        }
                    }
                }
            }
            """);
    }

    @Test
    void catchBreakAndContinue() {
        generateDot("catch-break-and-continue",
          //language=java
          """
            class Test {
                void test(boolean flag) {
                    for (int i = 0; i < 10; i++) {
                        try {
                            System.out.println(i);
                        } catch (RuntimeException e) {
                            if (flag) {
                                continue;
                            } else {
                                break;
                            }
                        }
                        System.out.println("after try");
                    }
                }
            }
            """);
    }

    @Test
    void finallyBreakAndContinue() {
        generateDot("finally-break-and-continue",
          //language=java
          """
            class Test {
                void test() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            System.out.println(i);
                        } catch (RuntimeException e) {
                            System.out.println("caught: " + e);
                        } finally {
                            if (i % 2 == 0) {
                                continue;
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
            """);
    }

    @Test
    void tryWithResourcesInsanity() {
        generateDot("try-with-resources-insanity",
          //language=java
          """
            import java.io.*;
            class Test {
                void test() {
                    for (int i = 0; i < 10; i++) {
                        try (InputStream in = new FileInputStream("f.txt")) {
                            if (in.read() == 0) {
                                continue;
                            }
                            System.out.println(in.read());
                        } catch (FileNotFoundException e) {
                            break;
                        } catch (IOException e) {
                            continue;
                        } finally {
                            if (i % 2 == 0) {
                                continue;
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
            """);
    }

    // -------------------------------------------------------------------------
    // Infrastructure
    // -------------------------------------------------------------------------

    private void generateDot(String name, String javaSource) {
        AtomicReference<String> dotContent = new AtomicReference<>();
        rewriteRun(
          java(javaSource, spec -> spec.after(s -> s).afterRecipe(cu -> {
              cu.getClasses().forEach(cls ->
                cls.getBody().getStatements().forEach(stmt -> {
                    if (stmt instanceof org.openrewrite.java.tree.J.MethodDeclaration) {
                        org.openrewrite.java.tree.J.MethodDeclaration method =
                                (org.openrewrite.java.tree.J.MethodDeclaration) stmt;
                        method.getMarkers().getMarkers().stream()
                                .filter(SearchResult.class::isInstance)
                                .map(SearchResult.class::cast)
                                .map(SearchResult::getDescription)
                                .filter(d -> d != null && d.startsWith("digraph"))
                                .findFirst()
                                .ifPresent(dotContent::set);
                    }
                }));
          }))
        );
        String dot = dotContent.get();
        if (dot == null) return;
        String dotDir = System.getProperty("cfg.dot.output.dir", "build/cfg-dot");
        try {
            Path outDir = Paths.get(dotDir);
            Files.createDirectories(outDir);
            Files.writeString(outDir.resolve(name + ".dot"), dot);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
