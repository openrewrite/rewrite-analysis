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
package org.openrewrite.analysis.dataflow.analysis;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
public class SinkFlowSummary {
    private final SinkFlowSummaryFlowGraphWalker flowGraphWalker;

    @Getter
    private final Cursor sourceCursor;

    @Getter(lazy = true)
    private final List<List<Cursor>> flows = flowGraphWalker.computeFlows();

    @Getter(lazy = true)
    private final List<Cursor> sinkCursors = computeSinkCursors();

    private List<Cursor> computeSinkCursors() {
        List<List<Cursor>> flows = getFlows();
        List<Cursor> sinkCursors = new ArrayList<>(flows.size());
        for (List<Cursor> flow : flows) {
            sinkCursors.add(flow.get(flow.size() - 1));
        }
        return sinkCursors;
    }

    @Getter(lazy = true)
    private final List<J> sinks =
            getSinkCursors()
                    .stream()
                    .map(Cursor::<J>getValue)
                    .collect(Collectors.toList());

    @Getter(lazy = true)
    private final Set<Cursor> flowCursorParticipants =
            getFlows()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));

    @Getter(lazy = true)
    private final Set<Expression> flowParticipants =
            getFlowCursorParticipants()
                    .stream()
                    .map(Cursor::<Expression>getValue)
                    .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>())));

    public J getSource() {
        return getSourceCursor().getValue();
    }

    public boolean isEmpty() {
        return getSinks().isEmpty();
    }

    public boolean isNotEmpty() {
        return !isEmpty();
    }

    public static SinkFlowSummary create(
            FlowGraph start,
            DataFlowSpec spec,
            Set<Expression> reachable
    ) {
        return new SinkFlowSummary(
                SinkFlowSummaryFlowGraphWalker.create(spec, reachable, start),
                start.getNode().getCursor()
        );
    }


    @AllArgsConstructor(access = AccessLevel.PRIVATE, staticName = "create")
    private static class SinkFlowSummaryFlowGraphWalker {
        private final DataFlowSpec spec;
        private final Set<Expression> reachable;

        private final FlowGraph start;

        public List<List<Cursor>> computeFlows() {
            // IMPORTANT NOTE!
            // A naive implementation would return emptyList if the edges are empty.
            // But we need to consider that the source can also be a valid sink.
            List<List<Cursor>> flows = new ArrayList<>();
            Deque<Cursor> path = new ArrayDeque<>();
            path.push(start.getNode().getCursor());
            recurseGetFlows(start, path, flows);
            return flows;
        }

        private void recurseGetFlows(
          FlowGraph flowGraph,
          Deque<Cursor> pathToHere,
          List<List<Cursor>> pathsToSinks
        ) {
            Cursor cursor = flowGraph.getNode().getCursor();
            if (cursor.getValue() instanceof Expression && !reachable.contains(cursor.<Expression>getValue())) {
                return;
            }

            if (spec.isSink(flowGraph.getNode())) {
                List<Cursor> flow = new ArrayList<>(pathToHere);
                flow.add(cursor);
                pathsToSinks.add(flow);
            }

            for (FlowGraph edge : flowGraph.getEdges()) {
                Cursor edgeCursor = edge.getNode().getCursor();
                pathToHere.push(edgeCursor);
                // Recurse here with the longer path
                recurseGetFlows(edge, pathToHere, pathsToSinks);
                Cursor poppedCursor = pathToHere.pop();
                assert poppedCursor == edgeCursor : "Expected " + edgeCursor + " but got " + poppedCursor;
            }
        }
    }
}
