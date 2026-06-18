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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.java.tree.Expression;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * To create an instance call {@link ControlFlow#findControlFlow()}.
 */
@AllArgsConstructor(staticName = "forGraph", access = AccessLevel.PACKAGE)
@Incubating(since = "7.25.0")
public final class ControlFlowSummary {
    private final ControlFlowNode.Start start;
    private final ControlFlowNode.End end;

    @Getter(lazy = true)
    private final Set<ControlFlowNode> allNodes = getAllControlFlowNodes(start, end);

    private static Set<ControlFlowNode> getAllControlFlowNodes(ControlFlowNode.Start start, ControlFlowNode.End end) {
        // LinkedHashSet to preserve insertion order of nodes
        Set<ControlFlowNode> all = new LinkedHashSet<>();
        // Use getSuccessorsForTraversal() so that broken graphs (BasicBlocks with no successor) can
        // still be fully traversed for visualization and validation — without throwing prematurely.
        recurseGetAllControlFlowNodes(start, all, ControlFlowNode::getSuccessorsForTraversal);
        // Sometimes the end may not be reachable because of an infinite loop.
        // In this case, we need to add the end node and look backwards as well to capture 'all' nodes.
        recurseGetAllControlFlowNodes(end, all, ControlFlowNode::getPredecessors);
        return all;
    }


    private static void recurseGetAllControlFlowNodes(ControlFlowNode current, Set<ControlFlowNode> visited, Function<ControlFlowNode, Set<ControlFlowNode>> getNext) {
        visited.add(current);
        Queue<ControlFlowNode> toVisit = new LinkedList<>(getNext.apply(current));
        toVisit.removeAll(visited);
        toVisit.forEach(node -> recurseGetAllControlFlowNodes(node, visited, getNext));
    }

    public Set<ControlFlowNode.BasicBlock> getBasicBlocks() {
        return getAllNodes()
                .stream()
                .filter(ControlFlowNode.BasicBlock.class::isInstance)
                .map(ControlFlowNode.BasicBlock.class::cast)
                .collect(toSet());
    }

    public Set<ControlFlowNode.ConditionNode> getConditionNodes() {
        return getAllNodes()
                .stream()
                .filter(ControlFlowNode.ConditionNode.class::isInstance)
                .map(ControlFlowNode.ConditionNode.class::cast)
                .collect(toSet());
    }

    public Set<Expression> computeReachableExpressions(BarrierGuardPredicate predicate) {
        return computeExecutableCodePoints(predicate)
                .stream()
                .filter(cursor -> cursor.getValue() instanceof Expression)
                .map(cursor -> (Expression) cursor.getValue())
                .collect(toSet());
    }

    public Set<Cursor> computeExecutableCodePoints(BarrierGuardPredicate predicate) {
        return computeReachableBasicBlock(predicate)
                .stream()
                .flatMap(b -> b.getNodeCursors().stream())
                .collect(toSet());
    }

    public Set<ControlFlowNode.BasicBlock> computeReachableBasicBlock(BarrierGuardPredicate predicate) {
        Set<ControlFlowNode> reachable = new LinkedHashSet<>();
        recurseComputeReachableBasicBlock(start, predicate, reachable);
        return reachable
                .stream()
                .filter(ControlFlowNode.BasicBlock.class::isInstance)
                .map(ControlFlowNode.BasicBlock.class::cast)
                .collect(toSet());
    }

    private void recurseComputeReachableBasicBlock(ControlFlowNode visit, BarrierGuardPredicate predicate, Set<ControlFlowNode> reachable) {
        reachable.add(visit);
        final Queue<ControlFlowNode> toVisit = new LinkedList<>();
        if (visit instanceof ControlFlowNode.ConditionNode) {
            toVisit.addAll(((ControlFlowNode.ConditionNode) visit).visit(predicate));
        } else {
            // For LAMBDA-typed End nodes (used for lambda bodies and anonymous class
            // bodies), `getSuccessors()` returns the node that follows the sub-flow in
            // the surrounding control flow, so traversal must continue. METHOD-typed
            // Ends always return an empty set, so this branch is a no-op for them.
            toVisit.addAll(visit.getSuccessors());
        }
        toVisit.removeAll(reachable);
        toVisit.forEach(n -> recurseComputeReachableBasicBlock(n, predicate, reachable));
    }

    int getBasicBlockCount() {
        return getBasicBlocks().size();
    }

    int getConditionNodeCount() {
        return getConditionNodes().size();
    }

    int getExitCount() {
        return end.getPredecessors().size();
    }

    /**
     * Validates that the control flow graph satisfies all structural invariants.
     * On violation, generates a DOT representation of the (possibly malformed) graph and throws
     * a {@link ControlFlowIllegalStateException} with both the violation details and the DOT embedded,
     * so the graph can be visualized even when broken.
     */
    void validate() {
        List<ControlFlowNode.BasicBlock> blocksWithNoSuccessor = getAllNodes()
                .stream()
                .filter(ControlFlowNode.BasicBlock.class::isInstance)
                .map(ControlFlowNode.BasicBlock.class::cast)
                .filter(bb -> !bb.hasSuccessor())
                .collect(toList());
        if (!blocksWithNoSuccessor.isEmpty()) {
            String dot = ControlFlowDotFileGenerator.create().visualizeAsDotfile("broken_graph", false, this);
            ControlFlowIllegalStateException.Message.MessageBuilder builder =
                    ControlFlowIllegalStateException.exceptionMessageBuilder(
                            "Control flow graph has " + blocksWithNoSuccessor.size() + " basic block(s) with no successor"
                    ).additionalContext("DOT representation of the malformed graph:\n" + dot);
            for (int i = 0; i < blocksWithNoSuccessor.size(); i++) {
                builder.addNode("BasicBlock without successor " + (i + 1), blocksWithNoSuccessor.get(i));
            }
            throw new ControlFlowIllegalStateException(builder);
        }
    }
}
