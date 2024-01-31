/*
 * Copyright 2023 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.search.UsesInvocation;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import java.util.function.Supplier;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

@SuppressWarnings({"ResultOfMethodCallIgnored", "UnnecessaryCallToStringValueOf", "StringOperationCanBeSimplified"})
public class ExternalModelMethodMatcherTest implements RewriteTest {
    static Supplier<TreeVisitor<?, ExecutionContext>> fromModel(
      String namespace,
      String type,
      boolean subtypes,
      String name,
      String signature
    ) {
        return () -> new UsesInvocation<>(
          new ExternalFlowModels.FlowModel(
            namespace,
            type,
            subtypes,
            name,
            signature,
            null,
            null,
            null,
            null,
            null
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "(InputStream,OutputStream)",
      "(java.io.InputStream,java.io.OutputStream)"
    })
    void methodMatcherOnFileCopy(String signature) {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "org.apache.commons.io",
              "IOUtils",
              true,
              "copy",
              signature)))
            .parser(JavaParser.fromJavaVersion()
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
              /*~~>*/import org.apache.commons.io.IOUtils;
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
              """
          )
        );
    }

    @Test
    void methodMatcherOnBuffer() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "org.apache.commons.io",
              "IOUtils",
              true,
              "buffer",
              "(InputStream,int)")))
            .parser(JavaParser.fromJavaVersion()
              .classpath("commons-io")),
          java(
            """
              import org.apache.commons.io.IOUtils;
              import java.io.InputStream;
              import java.io.BufferedInputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = source();
                      BufferedInputStream output = IOUtils.buffer(source, 64);
                  }
              }
              """,
            """
              /*~~>*/import org.apache.commons.io.IOUtils;
              import java.io.InputStream;
              import java.io.BufferedInputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = source();
                      BufferedInputStream output = IOUtils.buffer(source, 64);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherOnToString() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "java.lang",
              "String",
              false,
              "toString",
              ""))),
          java(
            """
              class Test {
                  String source() { return null; }
                  void test() {
                      String source = source();
                      System.out.println(source.toString());
                  }
              }
              """,
            """
              /*~~>*/class Test {
                  String source() { return null; }
                  void test() {
                      String source = source();
                      System.out.println(source.toString());
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherOnFill() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "java.util",
              "Arrays",
              false,
              "fill",
              "(double[],double)"))),
          java(
            """
              import java.util.Arrays;
              class Test {
                  double[] source() { return null; }
                  void test() {
                      double[] source = source();
                      Arrays.fill(source, 1.0);
                  }
              }
              """,
            """
              /*~~>*/import java.util.Arrays;
              class Test {
                  double[] source() { return null; }
                  void test() {
                      double[] source = source();
                      Arrays.fill(source, 1.0);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherOnFirstElement() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "java.util",
              "Vector",
              true,
              "firstElement",
              "()"))),
          java(
            """
              import java.util.Vector;
              class Test {
                  Vector<String> source() { return null; }
                  void test() {
                      Vector<String> source = source();
                      String firstElem = source.firstElement();
                      System.out.println(firstElem);
                  }
              }
              """,
            """
              /*~~>*/import java.util.Vector;
              class Test {
                  Vector<String> source() { return null; }
                  void test() {
                      Vector<String> source = source();
                      String firstElem = source.firstElement();
                      System.out.println(firstElem);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherOnToStringWhenNoToString() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "java.lang",
              "String",
              true,
              "toString",
              ""))),
          java(
            """
              class Test {
                  Integer source() { return 0; }
                  void test() {
                      Integer source = source();
                      System.out.println(source);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherThroughReadNBytesOnDifferentSignature() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "java.io",
              "InputStream",
              true,
              "readNBytes",
              "(byte[],int,int)"))),
          java(
            """
              import java.io.InputStream;
              class Test {
                  InputStream source() { return null; }
                  void test() {
                      InputStream source = source();
                      source.readNBytes(8);
                      source.close();
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherThroughFillOnDifferentSignature() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "java.util",
              "Arrays",
              false,
              "fill",
              "(Object[],int,int,Object)"))),
          java(
            """
              import java.util.Arrays;
              class Test {
                  int[] source() { return null; }
                  void test() {
                      int[] source = source();
                      Arrays.fill(source, 5);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherThroughValueOfOnDifferentSignature() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "java.lang",
              "String",
              false,
              "valueOf",
              "(char[])"))),
          java(
            """
              class Test {
                  char source() { return null; }
                  void test() {
                      char source = source();
                      System.out.println(String.valueOf(source));
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherDoesNotMatchCommonEndsWith() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "",
              "AClass",
              false,
              "aMethod",
              "(File)"))),
          java(
            """
              import java.io.File;
              class AClass {
                  void aMethod(File file) {
                      // no-op
                  }
                  
                  void aMethod(PotatoFile potatoFile) {
                      // no-op
                  }
              }
              class PotatoFile {
              }
              """
          ),
          java(
            """
              import java.io.File;
              class Test1 {
                  void test(AClass a, File file) {
                      a.aMethod(file);
                  }
              }
              """,
            """
              /*~~>*/import java.io.File;
              class Test1 {
                  void test(AClass a, File file) {
                      a.aMethod(file);
                  }
              }
              """
          ),
          java(
            """
              import java.io.File;
              class Test2 {
                  void test(AClass a, PotatoFile potatoFile) {
                      a.aMethod(potatoFile);
                  }
              }
              """
          )
        );
    }

    @Test
    void methodMatcherOnSubtypesFalse() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "java.util",
              "Collection",
              false,
              "size",
              ""))),
          java(
            """
              import java.util.Collection;
              class Test1 {
                  void test(Collection<Object> collection) {
                      collection.size();
                  }
              }
              """,
            """
              /*~~>*/import java.util.Collection;
              class Test1 {
                  void test(Collection<Object> collection) {
                      collection.size();
                  }
              }
              """
          ),
          java(
            """
              import java.util.Set;
              class Test2 {
                  void test(Set<Object> set) {
                      set.size();
                  }
              }
              """
          )
        );
    }
}
