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
package org.openrewrite.analysis.dataflow.analysis;

import lombok.*;
import org.openrewrite.Incubating;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.java.tree.J;

import javax.annotation.CheckReturnValue;
import java.util.*;

import static java.util.Collections.emptyList;

@Incubating(since = "7.24.0")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class FlowGraph {
    @Getter
    private final DataFlowNode node;
    private Map<J, FlowGraph> edges = Collections.emptyMap();

    public List<FlowGraph> getEdges() {
        if (edges.isEmpty()) {
            return emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(edges.values()));
    }

    /**
     * Add an edge to the graph returning the newly added {@link FlowGraph} leaf.
     *
     * @param node The node of the new leaf.
     * @return The newly added {@link FlowGraph} leaf.
     */
    @CheckReturnValue
    public FlowGraph addEdge(DataFlowNode node) {
        if (edges.isEmpty()) {
            edges = new IdentityHashMap<>(1);
        }
        return edges.computeIfAbsent(node.getCursor().getValue(), __ -> new FlowGraph(node));
    }

    /**
     * @return The edge argument.
     */
    public FlowGraph addEdge(FlowGraph edge) {
        if (edges.isEmpty()) {
            edges = new IdentityHashMap<>(1);
        }
        edges.put(edge.getNode().getCursor().getValue(), edge);
        return edge;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + "edges=" + edges.size() + ", cursor=" + node.getCursor() + '}';
    }

    public void removeEdge(FlowGraph edge) {
        edges.remove(edge.getNode().getCursor().<J>getValue(), edge);
    }
}
