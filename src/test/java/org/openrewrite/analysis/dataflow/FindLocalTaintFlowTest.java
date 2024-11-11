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
package org.openrewrite.analysis.dataflow;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.analysis.trait.expr.BinaryExpr;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.analysis.trait.expr.MethodAccess;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

@SuppressWarnings("FunctionName")
class FindLocalTaintFlowTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new FindLocalFlowPaths<>(new TaintFlowSpec() {
            @Override
            public boolean isSource(DataFlowNode srcNode) {
                return srcNode
                  .asExpr(MethodAccess.class)
                  .map(MethodAccess::getSimpleName)
                  .map("source"::equals)
                  .orSome(false);
            }

            @Override
            public boolean isSink(DataFlowNode sinkNode) {
                return true;
            }

            @Override
            public boolean isSanitizer(DataFlowNode node) {
                return node
                  .asExpr(BinaryExpr.class)
                  .map(binary -> binary.getOperator() == J.Binary.Type.Addition &&
                    binary.getRight() instanceof Literal &&
                    ((Literal) binary.getRight()).getValue().map("sanitizer"::equals).orSome(false))
                  .orSome(false);
            }
        }))).expectedCyclesThatMakeChanges(1).cycles(1);
    }

    @DocumentExample
    @Test
    void taintTrackingThroughStringManipulations() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = source();
                      String o = n.substring(0, 3);
                      String p = o.toUpperCase();
                      System.out.println(p);
                  }
              }
              """,
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = /*~~>*/source();
                      String o = /*~~>*//*~~>*/n.substring(0, 3);
                      String p = /*~~>*//*~~>*/o.toUpperCase();
                      System.out.println(/*~~>*/p);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughFileManipulations() {
        rewriteRun(
          java(
            """
              import java.io.File;
              import java.nio.file.Path;
              class Test {
                  File source() { return null; }
                  void test() {
                      {
                          File n = source();
                          String o = n.getAbsolutePath();
                          String p = o.toUpperCase();
                          System.out.println(p);
                      }
                      // To Path Type
                      {
                          File n = source();
                          Path o = n.toPath();
                          File p = o.toFile();
                          System.out.println(p);
                      }
                  }
              }
              """,
            """
              import java.io.File;
              import java.nio.file.Path;
              class Test {
                  File source() { return null; }
                  void test() {
                      {
                          File n = /*~~>*/source();
                          String o = /*~~>*//*~~>*/n.getAbsolutePath();
                          String p = /*~~>*//*~~>*/o.toUpperCase();
                          System.out.println(/*~~>*/p);
                      }
                      // To Path Type
                      {
                          File n = /*~~>*/source();
                          Path o = /*~~>*//*~~>*/n.toPath();
                          File p = /*~~>*//*~~>*/o.toFile();
                          System.out.println(/*~~>*/p);
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughFileConstructor() {
        rewriteRun(
          java(
            """
              import java.io.File;
              import java.net.URI;
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = source();
                      File o = new File(n);
                      URI p = o.toURI();
                      System.out.println(p);
                  }
              }
              """,
            """
              import java.io.File;
              import java.net.URI;
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = /*~~>*/source();
                      File o = /*~~>*/new File(/*~~>*/n);
                      URI p = /*~~>*//*~~>*/o.toURI();
                      System.out.println(/*~~>*/p);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughStringJoin() {
        rewriteRun(
          java(
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = source();
                      String o = String.join(", ", n);
                      String p = String.join(o, ", ");
                      String q = String.join(" ", "hello", p);
                      System.out.println(q);
                  }
              }
              """,
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = /*~~>*/source();
                      String o = /*~~>*/String.join(", ", /*~~>*/n);
                      String p = /*~~>*/String.join(/*~~>*/o, ", ");
                      String q = /*~~>*/String.join(" ", "hello", /*~~>*/p);
                      System.out.println(/*~~>*/q);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughStringAppending() {
        rewriteRun(
          java(
            """
              import java.io.File;
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = source();
                      String o = "hello " + n ;
                      String p = o + " world";
                      String q = p + File.separatorChar;
                      String r = q + true;
                      System.out.println(r);
                  }
              }
              """,
            """
              import java.io.File;
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = /*~~>*/source();
                      String o = /*~~>*/"hello " + /*~~>*/n ;
                      String p = /*~~>*//*~~>*/o + " world";
                      String q = /*~~>*//*~~>*/p + File.separatorChar;
                      String r = /*~~>*//*~~>*/q + true;
                      System.out.println(/*~~>*/r);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintStopsAtSanitizer() {
        rewriteRun(
          java(
            """
              import java.io.File;
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = source();
                      String o = "hello " + n ;
                      String p = o + " world";
                      String q = p + "sanitizer";
                      String r = q + true;
                      System.out.println(r);
                  }
              }
              """,
            """
              import java.io.File;
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = /*~~>*/source();
                      String o = /*~~>*/"hello " + /*~~>*/n ;
                      String p = /*~~>*//*~~>*/o + " world";
                      String q = /*~~>*/p + "sanitizer";
                      String r = q + true;
                      System.out.println(r);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughTryWithResources() {
        rewriteRun(
          java(
            """
              import java.io.InputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      try (InputStream source = source()) {
                          System.out.println(source.read());
                      }
                  }
              }
              """,
            """
              import java.io.InputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      try (InputStream source = /*~~>*/source()) {
                          System.out.println(/*~~>*/source.read());
                      }
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    @Test
    void taintTrackingThroughTry() {
        rewriteRun(
          java(
            """
              import java.io.InputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = source();
                      try {
                          System.out.println(source.read());
                      } finally {
                          source.close();
                      }
                  }
              }
              """,
            """
              import java.io.InputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = /*~~>*/source();
                      try {
                          System.out.println(/*~~>*/source.read());
                      } finally {
                          /*~~>*/source.close();
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughStringBuilder() {
        rewriteRun(
          java(
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = source();
                      StringBuilder o = new StringBuilder("hello ");
                      o.append(n);
                      o.append(" world");
                      System.out.println(o);
                  }
              }
              """,
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = /*~~>*/source();
                      StringBuilder o = new StringBuilder("hello ");
                      /*~~>*//*~~>*/o.append(/*~~>*/n);
                      /*~~>*//*~~>*/o.append(" world");
                      System.out.println(/*~~>*/o);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughStringBuilderAssignment() {
        rewriteRun(
          java(
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = source();
                      StringBuilder o = new StringBuilder("hello ");
                      StringBuilder p = o.append(n);
                      StringBuilder q = p.append(" world");
                      System.out.println(q);
                  }
              }
              """,
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String n = /*~~>*/source();
                      StringBuilder o = new StringBuilder("hello ");
                      StringBuilder p = /*~~>*//*~~>*/o.append(/*~~>*/n);
                      StringBuilder q = /*~~>*//*~~>*/p.append(" world");
                      System.out.println(/*~~>*/q);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingIntoStringBuilderAppend() {
        rewriteRun(
          java(
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      StringBuilder o = new StringBuilder("hello ");
                      o.append(source());
                      o.append(" world");
                      System.out.println(o);
                  }
              }
              """,
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      StringBuilder o = new StringBuilder("hello ");
                      /*~~>*//*~~>*/o.append(/*~~>*/source());
                      /*~~>*//*~~>*/o.append(" world");
                      System.out.println(/*~~>*/o);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughArrayCopy() {
        rewriteRun(
          java(
            """
              class Test {
                  String[] source() { return null; }
                  void test() {
                      String[] n = source();
                      String[] m = { "a", "b", "c" };
                      System.arraycopy(n, 0, m, 1, 1);
                      System.out.println(java.util.Arrays.toString(m));
                  }
              }
              """,
            """
              class Test {
                  String[] source() { return null; }
                  void test() {
                      String[] n = /*~~>*/source();
                      String[] m = { "a", "b", "c" };
                      System.arraycopy(/*~~>*/n, 0, /*~~>*/m, 1, 1);
                      System.out.println(java.util.Arrays.toString(/*~~>*/m));
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughMultipleArrayCopies() {
        rewriteRun(
          java(
            """
              class Test {
                  String[] source() { return null; }
                  void test() {
                      String[] n = source();
                      String[] m = { "a", "b", "c" };
                      String[] o = { "1", "2", "3", "4" };
                      System.arraycopy(n, 0, m, 1, 1);
                      System.arraycopy(m, 1, o, 2, 2);
                      System.out.println(java.util.Arrays.toString(o));
                  }
              }
              """,
            """
              class Test {
                  String[] source() { return null; }
                  void test() {
                      String[] n = /*~~>*/source();
                      String[] m = { "a", "b", "c" };
                      String[] o = { "1", "2", "3", "4" };
                      System.arraycopy(/*~~>*/n, 0, /*~~>*/m, 1, 1);
                      System.arraycopy(/*~~>*/m, 1, /*~~>*/o, 2, 2);
                      System.out.println(java.util.Arrays.toString(/*~~>*/o));
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughFileCopy() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpath("commons-io")),
          java(
            """
              import org.apache.commons.io.IOUtils;
              import java.io.InputStream;
              import java.io.OutputStream;
              import java.io.FileOutputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = source();
                      OutputStream dest = new FileOutputStream("dest.txt");
                      IOUtils.copy(source, dest);
                  }
              }
              """,
            """
              import org.apache.commons.io.IOUtils;
              import java.io.InputStream;
              import java.io.OutputStream;
              import java.io.FileOutputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = /*~~>*/source();
                      OutputStream dest = new FileOutputStream("dest.txt");
                      IOUtils.copy(/*~~>*/source, /*~~>*/dest);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughFileCopyLargeShortSignature() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpath("commons-io")),
          java(
            """
              import org.apache.commons.io.IOUtils;
              import java.io.InputStream;
              import java.io.OutputStream;
              import java.io.FileOutputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = source();
                      OutputStream dest = new FileOutputStream("dest.txt");
                      byte[] buffer = new byte[16];
                      long bytesCopied = IOUtils.copyLarge(source, dest, buffer);
                  }
              }
              """,
            """
              import org.apache.commons.io.IOUtils;
              import java.io.InputStream;
              import java.io.OutputStream;
              import java.io.FileOutputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = /*~~>*/source();
                      OutputStream dest = new FileOutputStream("dest.txt");
                      byte[] buffer = new byte[16];
                      long bytesCopied = IOUtils.copyLarge(/*~~>*/source, /*~~>*/dest, buffer);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughWritingLines() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpath("commons-io")),
          java(
            """
              import org.apache.commons.io.IOUtils;
              import java.io.OutputStream;
              import java.io.FileOutputStream;
              import java.util.List;
              class Test {
                  String source() { return null; }
                  void test() {
                      List<String> lines = List.of("a", "b", "c");
                      String source = source();
                      OutputStream output = new FileOutputStream("dest.txt");
                      IOUtils.writeLines(lines, source, output);
                  }
              }
              """,
            """
              import org.apache.commons.io.IOUtils;
              import java.io.OutputStream;
              import java.io.FileOutputStream;
              import java.util.List;
              class Test {
                  String source() { return null; }
                  void test() {
                      List<String> lines = List.of("a", "b", "c");
                      String source = /*~~>*/source();
                      OutputStream output = new FileOutputStream("dest.txt");
                      IOUtils.writeLines(lines, /*~~>*/source, /*~~>*/output);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughJoiner() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpath("guava")),
          java(
            """
              import com.google.common.base.Joiner;
              class Test {
                  String source() { return null; }
                  void test() {
                      StringBuilder result = new StringBuilder();
                      String source = source();
                      Joiner.appendTo(result, source, "hello", null);
                      System.out.println(result);
                  }
              }
              """,
            """
              import com.google.common.base.Joiner;
              class Test {
                  String source() { return null; }
                  void test() {
                      StringBuilder result = new StringBuilder();
                      String source = /*~~>*/source();
                      /*~~>*/Joiner.appendTo(/*~~>*/result, /*~~>*/source, "hello", null);
                      System.out.println(/*~~>*/result);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughTransferTo() {
        rewriteRun(
          java(
            """
              import java.io.InputStream;
              import java.io.OutputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream input = source();
                      OutputStream output = null;
                      input.transferTo(output);
                      System.out.println(output.toString());
                  }
              }
              """,
            """
              import java.io.InputStream;
              import java.io.OutputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream input = /*~~>*/source();
                      OutputStream output = null;
                      /*~~>*/input.transferTo(/*~~>*/output);
                      System.out.println(/*~~>*/output.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughGetChars() {
        rewriteRun(
          java(
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String str = source();
                      char[] dest = new char[32];
                      str.getChars(0, 5, dest, 0);
                      System.out.println(dest);
                  }
              }
              """,
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String str = /*~~>*/source();
                      char[] dest = new char[32];
                      /*~~>*/str.getChars(0, 5, /*~~>*/dest, 0);
                      System.out.println(/*~~>*/dest);
                  }
              }
              """
          )
        );
    }

    @Test
    void taintTrackingThroughMethodCallArgument() {
        rewriteRun(
          java(
            """
              import java.util.Objects;

              class Test {
                  String source() { return null; }
                  void test() {
                      String str = Objects.requireNonNull(source());
                      System.out.println(str);
                  }
              }
              """,
            """
              import java.util.Objects;

              class Test {
                  String source() { return null; }
                  void test() {
                      String str = /*~~>*/Objects.requireNonNull(/*~~>*/source());
                      System.out.println(/*~~>*/str);
                  }
              }
              """
          )
        );
    }

    static Stream<TestCase> fileProvider() {
        try (ScanResult scanResult = new ClassGraph().acceptPaths("/find-local-taint-flow-tests").scan()) {
            return scanResult
              .getResourcesWithExtension(".java")
              .stream()
              .filter(resource -> resource.getPath().endsWith(".Initial.java"))
              .map(resource -> new TestCase(
                  resource.getPath(),
                  resource.getPath().replace(".Initial", ".Result")
                )
              );
        }
    }

    record TestCase(String source, String after) {
    }

    @ParameterizedTest
    @MethodSource("fileProvider")
    void taintTrackingThroughFiles(TestCase testCase) {
        rewriteRun(
          java(
            StringUtils.readFully(
              requireNonNull(FindLocalTaintFlowTest.class.getResourceAsStream("/" + testCase.source()))
            ),
            StringUtils.readFully(
              requireNonNull(FindLocalTaintFlowTest.class.getResourceAsStream("/" + testCase.after()))
            )
          )
        );
    }
}
