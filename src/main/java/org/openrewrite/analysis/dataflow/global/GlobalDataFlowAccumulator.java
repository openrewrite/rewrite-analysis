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
import lombok.Value;
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
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Value
@AllArgsConstructor(access = lombok.AccessLevel.PACKAGE)
class GlobalDataFlowAccumulator implements GlobalDataFlow.Accumulator {
    private static final InvocationMatcher MATCHES_ALL = e -> true;

    DataFlowSpec spec;

    Set<FlowGraph> sourceFlowGraphs = new HashSet<>();
    Map<JavaType.Method, List<FlowGraph>> methodCallFlowGraphs = new HashMap<>();
    Map<JavaType.Method, List<FlowGraph>> parameterFlowGraphs = new HashMap<>();
    Map<JavaType.Method, List<FlowGraph>> argumentFlowGraphs = new HashMap<>();
    Map<JavaType.Method, List<FlowGraph>> methodReturnFlowGraphs = new HashMap<>();

    @Override
    public TreeVisitor<?, ExecutionContext> scanner() {
        GlobalDataFlowSpec globalDataFlowSpec = new GlobalDataFlowSpec(spec);
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitExpression(Expression expression, ExecutionContext e) {
                DataFlowNode.of(getCursor()).forEach(n -> {
                    if (!spec.isSource(n)) {
                        return;
                    }
                    FlowGraph source = ForwardFlow.findAllFlows(n, globalDataFlowSpec);
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
                                FlowGraph g = ForwardFlow.findAllFlows(n, globalDataFlowSpec);
                                parameterFlowGraphs.computeIfAbsent(m, __ -> nulledFlowGraphList(p.getCallable().getParameters().size()))
                                        .set(p.getPosition(), g);
                                if (argumentFlowGraphs.containsKey(m)) {
                                    argumentFlowGraphs.get(m).get(p.getPosition()).addEdge(g);
                                }
                                if (spec.isSource(n)) {
                                    sourceFlowGraphs.add(g);
                                }
                                walkFlowGraphConnecting(g);
                            });
                        }));
                return super.visitVariable(variable, executionContext);
            }

            private void walkFlowGraphConnecting(FlowGraph flowGraph) {
                walkFlowGraphConnectingRecursive(flowGraph, new HashSet<>());
            }

            private void walkFlowGraphConnectingRecursive(FlowGraph flowGraph, Set<FlowGraph> visited) {
                DataFlowNode n = flowGraph.getNode();
                n.asExprParent(Call.class).bind(Call::getMethodType).forEach(methodType -> {
                    JavaType.Method declaredMethodType = MethodTypeUtils.getDeclarationMethod(methodType);
                    methodCallFlowGraphs
                            .computeIfAbsent(declaredMethodType, __ -> new ArrayList<>())
                            .add(flowGraph);
                    if (methodReturnFlowGraphs.containsKey(declaredMethodType)) {
                        methodReturnFlowGraphs.get(declaredMethodType).forEach(returnGraph -> returnGraph.addEdge(flowGraph));
                    }
                });
                if (isAnyMethodArgument(n)) {
                    MethodCall methodCall = n.getCursor().getParentTreeCursor().firstEnclosing(MethodCall.class);
                    assert methodCall != null;
                    JavaType.Method methodType = methodCall.getMethodType();
                    if (methodType != null) {
                        int argumentIndex = methodCall.getArguments().indexOf(n.getCursor().<Expression>getValue());
                        JavaType.Method declaredMethodType = MethodTypeUtils.getDeclarationMethod(methodType);
                        argumentFlowGraphs
                                .computeIfAbsent(declaredMethodType, __ -> nulledFlowGraphList(methodCall.getArguments().size()))
                                .set(argumentIndex, flowGraph);
                        if (parameterFlowGraphs.containsKey(declaredMethodType)) {
                            flowGraph.addEdge(parameterFlowGraphs.get(declaredMethodType).get(argumentIndex));
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
                if (visited.add(flowGraph)) {
                    flowGraph.getEdges().forEach(edge -> walkFlowGraphConnectingRecursive(edge, visited));
                }
            }
        };
    }

    private Set<Cursor> pruneFlowGraphs() {
        visitDepthFirstAndPruneFlowGraphRecursive(
                sourceFlowGraphs,
                new HashSet<>()
        );
        Set<FlowGraph> foundParticipants = findParticipantsBreadthFirst(sourceFlowGraphs);
        return foundParticipants
                .stream()
                .map(FlowGraph::getNode)
                .map(DataFlowNode::getCursor)
                .collect(Collectors.toSet());
    }

    private void visitDepthFirstAndPruneFlowGraphRecursive(
            Collection<FlowGraph> toVisit,
            Set<FlowGraph> visited
    ) {
        for (FlowGraph flowGraph : toVisit) {
            if (!visited.add(flowGraph)) {
                return;
            }
            pruneFlowGraph(flowGraph);
            visitDepthFirstAndPruneFlowGraphRecursive(flowGraph.getEdges(), visited);
        }
    }

    private void pruneFlowGraph(FlowGraph flowGraph) {
        // If this is a method argument, and it is connected to a parameter, then prune the edge that connects,
        // as long as it was not an additional flow step already
        if (isAnyMethodArgument(flowGraph.getNode())) {
            // Find the edge that connects from this argument to the method call, and prune that edge
            for (FlowGraph edge : flowGraph.getEdges()) {
                // If this is a connection we added to the graph purely for the purposes of Global Data Flow Analysis,
                // then we can remove it, as long as it was not an additional flow step already
                if (GlobalDataFlowSpec.isAdditionalGlobalDataFlowStep(flowGraph.getNode(), edge.getNode()) &&
                    !spec.isFlowStep(flowGraph.getNode(), edge.getNode())
                ) {
                    flowGraph.removeEdge(edge);
                }
            }
        }
    }

    private Set<FlowGraph> findParticipantsBreadthFirst(Set<FlowGraph> flowGraphs) {
        MemoizeReachabilityHolder memoizeReachabilityHolder = new MemoizeReachabilityHolder();
        Set<FlowGraph> foundParticipants = new HashSet<>();
        Set<FlowGraph> visited = new HashSet<>();
        Deque<FlowGraph> toVisit = new ArrayDeque<>(flowGraphs);
        // A flow is only a participant if there is a path to a flow node that is a sink.
        // IE. spec.isSync(flowGraph.getNode()) returns true.
        // If there is a path to that node, then all parents of that node are also participants.

        while (!toVisit.isEmpty()) {
            FlowGraph current = toVisit.poll();
            if (visited.contains(current)) {
                continue;
            }
            if (memoizeReachabilityHolder.isSinkReachable(current)) {
                foundParticipants.add(current);
                // Add all children to the queue, as they are also potential participants
                toVisit.addAll(current.getEdges());
            }
            // If this is not a participant, then we don't need to visit any of its children
            visited.add(current);
        }
        return foundParticipants;
    }

    private class MemoizeReachabilityHolder {
        private final Map<FlowGraph, Boolean> reachable = new IdentityHashMap<>();

        private boolean isSinkReachable(FlowGraph flowGraph) {
            return reachable.computeIfAbsent(flowGraph, fg -> isSinkReachable(fg, new HashSet<>()));
        }

        private boolean isSinkReachable(FlowGraph flowGraph, Set<FlowGraph> visited) {
            if (reachable.getOrDefault(flowGraph, false) || spec.isSink(flowGraph.getNode())) {
                return true;
            }
            if (!visited.add(flowGraph)) {
                return false;
            }
            for (FlowGraph edge : flowGraph.getEdges()) {
                if (isSinkReachable(edge, visited)) {
                    return true;
                }
            }
            return false;
        }
    }

    public GlobalDataFlow.Summary summary(Cursor cursor) {
        Set<Cursor> prunedParticipatingNodes = pruneFlowGraphs();
        if (prunedParticipatingNodes.isEmpty()) {
            return AlwaysFalseSummary.INSTANCE;
        }
        return DataFlowNode
                .of(cursor)
                .map(n -> (GlobalDataFlow.Summary) new ResultSummary(n, prunedParticipatingNodes))
                .orSome(AlwaysFalseSummary.INSTANCE);
    }

    @AllArgsConstructor
    private class ResultSummary implements GlobalDataFlow.Summary {
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

    @AllArgsConstructor
    private static class GlobalDataFlowSpec extends DataFlowSpec {

        private final DataFlowSpec decorated;

        @Override
        public boolean isSource(DataFlowNode srcNode) {
            return decorated.isSource(srcNode);
        }

        @Override
        public boolean isSink(DataFlowNode sinkNode) {
            // This is never used in FindFlow, as we only care about sources for Global Data Flow Analysis
            return false;
        }

        @Override
        public boolean isAdditionalFlowStep(DataFlowNode srcNode, DataFlowNode sinkNode) {
            return decorated.isAdditionalFlowStep(srcNode, sinkNode) ||
                   isAdditionalGlobalDataFlowStep(srcNode, sinkNode);
        }

        @Override
        public boolean isBarrier(DataFlowNode node) {
            return decorated.isBarrier(node);
        }

        @Override
        public boolean isBarrierGuard(Guard guard, boolean branch) {
            return decorated.isBarrierGuard(guard, branch);
        }

        static boolean isAdditionalGlobalDataFlowStep(DataFlowNode srcNode, DataFlowNode sinkNode) {
            return sinkNode
                    .asExprParent(Call.class)
                    .map(call -> call.methodTypeMatcher().advanced().isAnyArgument(srcNode.getCursor()))
                    .orSome(false);
        }
    }

    private static boolean isAnyMethodArgument(DataFlowNode node) {
        return node.asExpr().map(e -> MATCHES_ALL.advanced().isAnyArgument(node.getCursor())).orSome(false);
    }

    private static List<FlowGraph> nulledFlowGraphList(int size) {
        return IntStream.range(0, size).mapToObj(__ -> (FlowGraph) null).collect(Collectors.toList());
    }

}
