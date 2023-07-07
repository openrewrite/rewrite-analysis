package org.openrewrite.analysis.dataflow.global;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class GlobalDataFlowTest implements RewriteTest {

    static final DataFlowSpec DATA_FLOW_SPEC = new DataFlowSpec() {
        private static final InvocationMatcher SYSTEM_OUT_PRINTLN = InvocationMatcher.fromMethodMatcher(
          new MethodMatcher("java.io.PrintStream println(..)")
        );

        @Override
        public boolean isSource(DataFlowNode srcNode) {
            return srcNode
              .asExpr(Literal.class)
              .bind(Literal::getValue)
              .map("42"::equals)
              .orSome(false);
        }

        @Override
        public boolean isSink(DataFlowNode sinkNode) {
            return SYSTEM_OUT_PRINTLN.advanced().isAnyArgument(sinkNode.getCursor());
        }
    };

    public static class GlobalDataFlowRecipe extends ScanningRecipe<GlobalDataFlow.Accumulator> {

        @Override
        public String getDisplayName() {
            return "blah";
        }

        @Override
        public String getDescription() {
            return "blah.";
        }

        @Override
        public GlobalDataFlow.Accumulator getInitialValue(ExecutionContext ctx) {
            return GlobalDataFlow.accumulator(DATA_FLOW_SPEC);
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getScanner(GlobalDataFlow.Accumulator acc) {
            return acc.scanner();
        }

        @Override
        public TreeVisitor<?, ExecutionContext> getVisitor(GlobalDataFlow.Accumulator acc) {
            return new RenderGlobalFlowPaths<>(acc);
        }
    }

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new GlobalDataFlowRecipe())
          .expectedCyclesThatMakeChanges(1)
          .cycles(1);
    }

    @Test
    void multiMethodDataFlowThroughIdentityFunction() {
        rewriteRun(
          java(
            """
                class Test {
                
                    String identity(String s) {
                        return s;
                    }
                    
                    void test() {
                        String s = "42";
                        String t = identity(s);
                        System.out.println(t);
                    }
                }
              """,
            """
                class Test {
                
                    String identity(String /*~~>*/s) {
                        return /*~~>*/s;
                    }
                    
                    void test() {
                        String s = /*~~(source)~~>*/"42";
                        String t = /*~~>*/identity(/*~~>*/s);
                        System.out.println(/*~~(sink)~~>*/t);
                    }
                }
              """
          )
        );
    }

    @Test
    void multiMethodDataFlowThroughNoOpFunction() {
        rewriteRun(
          java(
            """
                class Test {
                
                    String noOp(String s) {
                        return "something else";
                    }
                    
                    void test() {
                        String s = "42";
                        String t = noOp(s);
                        System.out.println(t);
                    }
                }
              """
          )
        );
    }
}
