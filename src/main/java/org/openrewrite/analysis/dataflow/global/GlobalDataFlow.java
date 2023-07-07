package org.openrewrite.analysis.dataflow.global;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.controlflow.Guard;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.Dataflow;
import org.openrewrite.analysis.dataflow.analysis.SinkFlowSummary;
import org.openrewrite.analysis.trait.expr.Call;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan:
 * <p>
 * <ol>
 *     <li>
 *         Build an exhaustive flow graph that includes all methods, from all arguments, to all arguments, and to all return values.
 *         Don't discriminate at all. This is a superset graph of data flow. It will include all possible paths.
 *     </li>
 *     <li>
 *         Connect this graph together after finding all of the valid source nodes.
 *     </li>
 *     <li>
 *         Walk the graph from all sources, pruning all paths were {@link DataFlowSpec#isAdditionalFlowStep}
 *         returns false, and where {@link DataFlowSpec#isSink(DataFlowNode)} returns false.
 *     </li>
 * </ol>
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class GlobalDataFlow {

    public static Accumulator accumulator(DataFlowSpec spec) {
        return new Accumulator(spec);
    }

    @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static class Accumulator {
        private final DataFlowSpec spec;
        private final List<SinkFlowSummary> summaries = new ArrayList<>();

        public TreeVisitor<?, ExecutionContext> scanner() {
            GlobalDataFlowSpec globalDataFlowSpec = new GlobalDataFlowSpec(spec);
            return new JavaVisitor<ExecutionContext>() {
                @Override
                public J visitExpression(Expression expression, ExecutionContext e) {
                    Dataflow.startingAt(getCursor()).findSinks(globalDataFlowSpec).forEach(summaries::add);
                    return expression;
                }

                @Override
                public J visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext executionContext) {
                    Dataflow.startingAt(getCursor()).findSinks(globalDataFlowSpec).forEach(summaries::add);
                    return super.visitVariable(variable, executionContext);
                }
            };
        }

        public boolean isSource(Cursor cursor) {
            return DataFlowNode.of(cursor).map(spec::isSource).orSome(false) &&
                   summaries.stream().anyMatch(s -> s.getSource().equals(cursor.getValue()));
        }

        public boolean isSink(Cursor cursor) {
            return DataFlowNode.of(cursor).map(spec::isSink).orSome(false) &&
                   summaries.stream().anyMatch(s -> s.getSinks().contains(cursor.<J>getValue()));
        }

        public boolean isFlowParticipant(Cursor cursor) {
            return summaries.stream().anyMatch(s -> s.getFlowParticipants().contains(cursor.<Expression>getValue()));
        }
    }

    @AllArgsConstructor
    private static class GlobalDataFlowSpec extends DataFlowSpec {
        private static final InvocationMatcher MATCHES_ALL = e -> true;
        DataFlowSpec decorated;

        @Override
        public boolean isSource(DataFlowNode srcNode) {
            return decorated.isSource(srcNode) || srcNode.asParameter().isSome();
        }

        @Override
        public boolean isSink(DataFlowNode sinkNode) {
            return decorated.isSink(sinkNode) || sinkNode.asExpr().map(__ -> {
                J.Return returnn = sinkNode.getCursor().firstEnclosing(J.Return.class);
                if (returnn != null) {
                    return Expression.unwrap(returnn.getExpression()) == sinkNode.getCursor().getValue();
                }
                return false;
            }).orSome(false);
        }

        @Override
        public boolean isAdditionalFlowStep(DataFlowNode srcNode, DataFlowNode sinkNode) {
            return decorated.isAdditionalFlowStep(srcNode, sinkNode) ||
                   MATCHES_ALL.advanced().isAnyArgument(srcNode.getCursor()) &&
                   sinkNode.asExprParent(Call.class).map(call -> call.matches(MATCHES_ALL)).orSome(false);
        }

        @Override
        public boolean isBarrier(DataFlowNode node) {
            return decorated.isBarrier(node);
        }

        @Override
        public boolean isBarrierGuard(Guard guard, boolean branch) {
            return decorated.isBarrierGuard(guard, branch);
        }
    }
}
