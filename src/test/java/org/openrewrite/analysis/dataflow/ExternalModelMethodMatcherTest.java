package org.openrewrite.analysis.dataflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.test.RewriteTest;

import java.util.function.Supplier;

import static org.openrewrite.test.RewriteTest.toRecipe;
import static org.openrewrite.java.Assertions.java;

public class ExternalModelMethodMatcherTest implements RewriteTest {
    static Supplier<TreeVisitor<?, ExecutionContext>> fromModel(
      String namespace,
      String type,
      boolean subtypes,
      String name,
      String signature
    ) {
        return () -> new UsesMethod<>(
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

    @Test
    void methodMatcherOnFileCopy() {
        rewriteRun(
          spec -> spec
            .recipe(toRecipe(fromModel(
              "org.apache.commons.io",
              "IOUtils",
              true,
              "copy",
              "(InputStream,OutputStream)")))
            .parser(JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "commons-io-2.13.0")),
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
    void methodMatcherOnToString() {
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
                      System.out.println(source.toString());
                  }
              }
              """,
            """
              /*~~>*/class Test {
                  Integer source() { return 0; }
                  void test() {
                      Integer source = source();
                      System.out.println(source.toString());
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
}
