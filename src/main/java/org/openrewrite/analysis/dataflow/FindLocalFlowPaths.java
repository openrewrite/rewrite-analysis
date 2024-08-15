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
package org.openrewrite.analysis.dataflow;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.Tree;
import org.openrewrite.analysis.dataflow.analysis.SinkFlowSummary;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

import java.util.*;

import static java.util.Objects.requireNonNull;

@Incubating(since = "7.24.0")
@RequiredArgsConstructor
public class FindLocalFlowPaths<P> extends JavaIsoVisitor<P> {
    private static final String FLOW_GRAPHS = "flowGraphs";
    private final DataFlowSpec spec;

    @Override
    public @Nullable J visit(@Nullable Tree tree, P p) {
        if (tree instanceof JavaSourceFile) {
            getCursor().putMessage(FLOW_GRAPHS, new ArrayList<>());
            JavaSourceFile c = (JavaSourceFile) super.visit(tree, p);

            Set<J> flowSteps = Collections.newSetFromMap(new IdentityHashMap<>());
            List<SinkFlowSummary> sinkFlows = getCursor().getMessage(FLOW_GRAPHS);
            for (SinkFlowSummary flowGraphSummary : requireNonNull(sinkFlows)) {
                flowSteps.addAll(flowGraphSummary.getFlowParticipants());
            }

            if (!flowSteps.isEmpty()) {
                return new JavaIsoVisitor<P>() {
                    @Override
                    public Expression visitExpression(Expression expression, P p) {
                        return flowSteps.contains(expression) ?
                                SearchResult.found(expression) :
                                expression;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, P p) {
                        return flowSteps.contains(variable) ?
                                SearchResult.found(super.visitVariable(variable, p)) :
                                super.visitVariable(variable, p);
                    }
                }.visit(c, p);
            }
            return c;
        }
        return super.visit(tree, p);
    }

    @Override
    public Expression visitExpression(Expression expression, P p) {
        Dataflow.startingAt(getCursor()).findSinks(spec).forEach(flow -> {
            if (flow.isNotEmpty()) {
                List<SinkFlowSummary> flowGraphs = getCursor().getNearestMessage(FLOW_GRAPHS);
                assert flowGraphs != null;
                flowGraphs.add(flow);
            }
        });
        return expression;
    }

    @Override
    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, P p) {
        Dataflow.startingAt(getCursor()).findSinks(spec).forEach(flow -> {
            if (flow.isNotEmpty()) {
                List<SinkFlowSummary> flowGraphs = getCursor().getNearestMessage(FLOW_GRAPHS);
                assert flowGraphs != null;
                flowGraphs.add(flow);
            }
        });
        return super.visitVariable(variable, p);
    }

    public static boolean anyMatch(Cursor cursor, DataFlowSpec spec) {
        JavaSourceFile enclosing = cursor.firstEnclosingOrThrow(JavaSourceFile.class);
        return new FindLocalFlowPaths<Integer>(spec).visit(enclosing, 0) != enclosing;
    }

    public static boolean noneMatch(Cursor cursor, DataFlowSpec spec) {
        return !anyMatch(cursor, spec);
    }
}
