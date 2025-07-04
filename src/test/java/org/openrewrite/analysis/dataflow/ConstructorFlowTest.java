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
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class ConstructorFlowTest implements RewriteTest {

    @DocumentExample
    @Test
    void bigIntegerToRsaKeyGen() {
        rewriteRun(
          spec -> spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
              final MethodMatcher bigIntMatcher = new MethodMatcher("java.math.BigInteger <init>(java.lang.String)");
              final MethodMatcher rsaKeyGenSpecCtor = new MethodMatcher("java.security.spec.RSAKeyGenParameterSpec <init>(..)");

              @Override
              public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                  J.NewClass n = super.visitNewClass(newClass, executionContext);
                  if (bigIntMatcher.matches(n)) {
                      if (Dataflow.startingAt(getCursor()).findSinks(new DataFlowSpec() {
                          @Override
                          public boolean isSource(DataFlowNode srcNode) {
                              return true;
                          }

                          @Override
                          public boolean isSink(DataFlowNode sinkNode) {
                              J.NewClass nc = sinkNode.getCursor().firstEnclosing(J.NewClass.class);
                              if (nc != null) {
                                  return rsaKeyGenSpecCtor.matches(nc);
                              }
                              return false;
                          }
                      }).isSome()) {
                          return SearchResult.found(n);
                      }
                  }
                  return n;
              }
          })),
          //language=java
          java(
                """
              import java.math.BigInteger;
              import java.security.spec.RSAKeyGenParameterSpec;
              class C {
                  void params() {
                      BigInteger exponent = new BigInteger("65537");
                      RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(2048, exponent);
                  }
              }
              """,
            """
              import java.math.BigInteger;
              import java.security.spec.RSAKeyGenParameterSpec;
              class C {
                  void params() {
                      BigInteger exponent = /*~~>*/new BigInteger("65537");
                      RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(2048, exponent);
                  }
              }
              """
          )
        );
    }
}
