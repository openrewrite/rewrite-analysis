/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.DocumentExample;
import org.openrewrite.analysis.trait.expr.MethodAccess;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

/**
 * Higher-order ("lambda call") sinks: a sink whose access path is {@code Argument[i].ReturnValue}
 * marks the value <em>returned by the lambda passed as argument {@code i}</em> as the sink. The
 * canonical example is Couchbase's {@code PasswordAuthenticator.Builder.password(Supplier)}, whose
 * supplier's return value is a {@code credentials-password} sink.
 */
@SuppressWarnings("FunctionName")
class HigherOrderSinkTest implements RewriteTest {

    /**
     * A minimal stub of the Couchbase type carrying the real {@code Argument[0].ReturnValue} sink
     * models, so the test exercises the shipped {@code sinks.csv} data without a Couchbase dependency.
     */
    //language=java
    private static final String PASSWORD_AUTHENTICATOR = """
      package com.couchbase.client.core.env;

      import java.util.function.Supplier;

      public class PasswordAuthenticator {
          public static Builder builder() {
              return new Builder();
          }

          public static class Builder {
              public Builder username(Supplier<String> username) {
                  return this;
              }

              public Builder password(Supplier<String> password) {
                  return this;
              }
          }
      }
      """;

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
                return ExternalSinkModels.instance().isSinkNode(sinkNode, "credentials-password");
            }
        }))).expectedCyclesThatMakeChanges(1).cycles(1);
    }

    @DocumentExample
    @Test
    void sinkAtExpressionBodyLambdaReturn() {
        rewriteRun(
          java(PASSWORD_AUTHENTICATOR),
          java(
            """
              import com.couchbase.client.core.env.PasswordAuthenticator;
              class Test {
                  String source() { return null; }
                  void test() {
                      String secret = source();
                      PasswordAuthenticator.builder().password(() -> secret);
                  }
              }
              """,
            """
              import com.couchbase.client.core.env.PasswordAuthenticator;
              class Test {
                  String source() { return null; }
                  void test() {
                      String secret = /*~~>*/source();
                      PasswordAuthenticator.builder().password(() -> /*~~>*/secret);
                  }
              }
              """
          )
        );
    }

    @Test
    void sinkAtBlockBodyLambdaReturn() {
        rewriteRun(
          java(PASSWORD_AUTHENTICATOR),
          java(
            """
              import com.couchbase.client.core.env.PasswordAuthenticator;
              class Test {
                  String source() { return null; }
                  void test() {
                      String secret = source();
                      PasswordAuthenticator.builder().password(() -> {
                          return secret;
                      });
                  }
              }
              """,
            """
              import com.couchbase.client.core.env.PasswordAuthenticator;
              class Test {
                  String source() { return null; }
                  void test() {
                      String secret = /*~~>*/source();
                      PasswordAuthenticator.builder().password(() -> {
                          return /*~~>*/secret;
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void noSinkWhenValueIsNotReturnedFromLambda() {
        // The tainted value is used inside the lambda but is not its return value, so it is not a
        // `Argument[0].ReturnValue` sink. No source -> sink path exists, so nothing is marked.
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          java(PASSWORD_AUTHENTICATOR),
          java(
            """
              import com.couchbase.client.core.env.PasswordAuthenticator;
              class Test {
                  String source() { return null; }
                  void test() {
                      String secret = source();
                      PasswordAuthenticator.builder().password(() -> {
                          System.out.println(secret);
                          return "constant";
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void noSinkWhenLambdaPassedToNonMatchingMethod() {
        // The same lambda shape passed to a method with no callback-return sink model is not a sink.
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          java(
            """
              import java.util.function.Supplier;
              class Test {
                  String source() { return null; }
                  void consume(Supplier<String> s) {}
                  void test() {
                      String secret = source();
                      consume(() -> secret);
                  }
              }
              """
          )
        );
    }
}
