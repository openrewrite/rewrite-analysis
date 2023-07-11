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
package org.openrewrite.analysis.dataflow.global;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.controlflow.Guard;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.analysis.FlowGraph;
import org.openrewrite.analysis.dataflow.analysis.ForwardFlow;
import org.openrewrite.analysis.trait.expr.Call;
import org.openrewrite.analysis.trait.member.Callable;
import org.openrewrite.analysis.util.CursorUtil;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private static final InvocationMatcher MATCHES_ALL = e -> true;

    public static Accumulator accumulator(DataFlowSpec spec) {
        return new Accumulator(spec);
    }

    @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static class Accumulator {
        private final DataFlowSpec spec;
        private final Set<FlowGraph> sourceFlowGraphs = new HashSet<>();

        private final Map<JavaType.Method, List<FlowGraph>> methodCallFlowGraphs = new HashMap<>();
        private final Map<JavaType.Method, List<FlowGraph>> parameterFlowGraphs = new HashMap<>();
        private final Map<JavaType.Method, List<FlowGraph>> argumentFlowGraphs = new HashMap<>();
        private final Map<JavaType.Method, List<FlowGraph>> methodReturnFlowGraphs = new HashMap<>();

        public TreeVisitor<?, ExecutionContext> scanner() {
            GlobalDataFlowSpec globalDataFlowSpec = new GlobalDataFlowSpec(spec);
            return new JavaVisitor<ExecutionContext>() {
                @Override
                public J visitExpression(Expression expression, ExecutionContext e) {
                    DataFlowNode.of(getCursor()).forEach(n -> {
                        if (!spec.isSource(n)) {
                            return;
                        }
                        FlowGraph source = ForwardFlow.findSinks(n, globalDataFlowSpec);
                        sourceFlowGraphs.add(source);
                        walkFlowGraphConnecting(source);
                    });
                    return expression;
                }

                @Override
                public J visitVariable(J.VariableDeclarations.NamedVariable variable, ExecutionContext executionContext) {
                    DataFlowNode
                            .of(getCursor())
                            .forEach(n -> n.asParameter().forEach(p -> {
                                p.getCallable().getMethodType().forEach(m -> {
                                    FlowGraph g = ForwardFlow.findSinks(n, globalDataFlowSpec);
                                    parameterFlowGraphs.computeIfAbsent(m, __ -> nulledFlowGraphList(p.getCallable().getParameters().size()))
                                            .set(p.getPosition(), g);
                                    if (argumentFlowGraphs.containsKey(m)) {
                                        argumentFlowGraphs.get(m).get(p.getPosition()).addEdge(g);
                                    }
                                    sourceFlowGraphs.add(g);
                                    walkFlowGraphConnecting(g);
                                });
                            }));
                    return super.visitVariable(variable, executionContext);
                }

                private void walkFlowGraphConnecting(FlowGraph flowGraph) {
                    DataFlowNode n = flowGraph.getNode();
                    n.asExprParent(Call.class).bind(Call::getMethodType).forEach(methodType -> {
                        methodCallFlowGraphs
                                .computeIfAbsent(methodType, __ -> new ArrayList<>())
                                .add(flowGraph);
                        if (methodReturnFlowGraphs.containsKey(methodType)) {
                            methodReturnFlowGraphs.get(methodType).forEach(returnGraph -> returnGraph.addEdge(flowGraph));
                        }
                    });
                    if (isAnyMethodArgument(n)) {
                        MethodCall methodCall = n.getCursor().firstEnclosing(MethodCall.class);
                        assert methodCall != null;
                        JavaType.Method methodType = methodCall.getMethodType();
                        if (methodType != null) {
                            int argumentIndex = methodCall.getArguments().indexOf(n.getCursor().<Expression>getValue());
                            argumentFlowGraphs
                                    .computeIfAbsent(methodType, __ -> nulledFlowGraphList(methodCall.getArguments().size()))
                                    .set(argumentIndex, flowGraph);
                            if (parameterFlowGraphs.containsKey(methodType)) {
                                flowGraph.addEdge(parameterFlowGraphs.get(methodType).get(argumentIndex));
                            }
                        }
                    }
                    J.Return aReturn = n.getCursor().firstEnclosing(J.Return.class);
                    if (aReturn != null && Expression.unwrap(aReturn.getExpression()) == flowGraph.getNode().getCursor().getValue()) {
                        // Get the JavaType.Method for the enclosing body
                        J.MethodDeclaration methodDeclaration = n.getCursor().firstEnclosing(J.MethodDeclaration.class);
                        assert methodDeclaration != null;
                        JavaType.Method methodType = methodDeclaration.getMethodType();
                        if (methodType != null) {
                            methodReturnFlowGraphs
                                    .computeIfAbsent(methodType, __ -> new ArrayList<>())
                                    .add(flowGraph);
                            if (methodCallFlowGraphs.containsKey(methodType)) {
                                methodCallFlowGraphs.get(methodType).forEach(flowGraph::addEdge);
                            }
                        }
                    }
                    flowGraph.getEdges().forEach(this::walkFlowGraphConnecting);
                }
            };
        }

        private Set<Cursor> pruneFlowGraphs() {
            return sourceFlowGraphs
                    .stream()
                    .flatMap(flowGraph -> pruneFlowGraph(flowGraph).stream())
                    .collect(Collectors.toSet());
        }

        private Set<Cursor> pruneFlowGraph(FlowGraph flowGraph) {
            Set<Cursor> prunedParticipatingNodes = new HashSet<>();
            // If this is a method argument, and it is connected to a parameter, then prune the edge that connects,
            // as long as it was not an additional flow step already
            if (isAnyMethodArgument(flowGraph.getNode())) {
                // Find the edge that connects from this argument to the method call, and prune that edge
                for (FlowGraph edge : flowGraph.getEdges()) {
                    if (edge
                            .getNode()
                            .asExprParent(Call.class)
                            .map(c -> spec.isFlowStep(flowGraph.getNode(), edge.getNode()))
                            .orSome(edge.getNode().isParameter())) {
                        continue;
                    }
                    flowGraph.removeEdge(edge);
                }
            }
            boolean isSink = spec.isSink(flowGraph.getNode());
            if (flowGraph.getEdges().isEmpty() && !isSink) {
                return Collections.emptySet();
            }
            for (FlowGraph edge : flowGraph.getEdges()) {
                prunedParticipatingNodes.addAll(pruneFlowGraph(edge));
            }
            if (prunedParticipatingNodes.isEmpty() && !isSink) {
                return Collections.emptySet();
            }
            prunedParticipatingNodes.add(flowGraph.getNode().getCursor());
            return prunedParticipatingNodes;
        }

        public Summary summary(Cursor cursor) {
            Set<Cursor> prunedParticipatingNodes = pruneFlowGraphs();
            if (prunedParticipatingNodes.isEmpty()) {
                return AlwaysFalseSummary.INSTANCE;
            }
            return DataFlowNode
                    .of(cursor)
                    .map(n -> (Summary) new ResultSummary(n, prunedParticipatingNodes))
                    .orSome(AlwaysFalseSummary.INSTANCE);
        }

        @AllArgsConstructor
        private class ResultSummary implements Summary {
            private final DataFlowNode node;
            private final Set<Cursor> prunedParticipatingCursors;

            @Getter(lazy = true)
            private final Set<J> prunedParticipating =
                    prunedParticipatingCursors
                            .stream()
                            .map(Cursor::<J>getValue)
                            .collect(Collectors.toSet());

            @Override
            public boolean isSource() {
                return spec.isSource(node);
            }

            @Override
            public boolean isSink() {
                return spec.isSink(node) && isFlowParticipant();
            }

            @Override
            public boolean isFlowParticipant() {
                return getPrunedParticipating().contains(node.getCursor().<J>getValue());
            }
        }


        public boolean isSource(Cursor cursor) {
            return summary(cursor).isSource();
        }

        public boolean isSink(Cursor cursor) {
            return summary(cursor).isSink();
        }

        public boolean isFlowParticipant(Cursor cursor) {
            return summary(cursor).isFlowParticipant();
        }
    }

    public interface Summary {
        boolean isSource();

        boolean isSink();

        boolean isFlowParticipant();
    }

    private enum AlwaysFalseSummary implements Summary {
        INSTANCE;

        @Override
        public boolean isSource() {
            return false;
        }

        @Override
        public boolean isSink() {
            return false;
        }

        @Override
        public boolean isFlowParticipant() {
            return false;
        }
    }

    @AllArgsConstructor
    private static class GlobalDataFlowSpec extends DataFlowSpec {

        private final DataFlowSpec decorated;

        @Override
        public boolean isSource(DataFlowNode srcNode) {
            return decorated.isSource(srcNode) || srcNode.asParameter().isSome();
        }

        @Override
        public boolean isSink(DataFlowNode sinkNode) {
            return decorated.isSink(sinkNode) || sinkNode.asExpr().map(__ -> {
                J.Return returnn = sinkNode.getCursor().firstEnclosing(J.Return.class);
                if (returnn != null && Expression.unwrap(returnn.getExpression()) == sinkNode.getCursor().getValue()) {
                    return true;
                }
                return MATCHES_ALL.advanced().isAnyArgument(sinkNode.getCursor());
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

    private static boolean isAnyMethodArgument(DataFlowNode node) {
        return node.asExpr().map(e -> MATCHES_ALL.advanced().isAnyArgument(node.getCursor())).orSome(false);
    }

    private static List<FlowGraph> nulledFlowGraphList(int size) {
        return IntStream.range(0, size).mapToObj(__ -> (FlowGraph) null).collect(Collectors.toList());
    }
}
