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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.Dataflow;
import org.openrewrite.analysis.util.CursorUtil;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class GlobalDataFlowSummary implements GlobalDataFlow.Summary {
    private final DataFlowNode target;
    private final DataFlowSpec spec;

    @Override
    public boolean isSource() {
        return spec.isSource(target);
    }

    @Override
    public boolean isSink() {
        return SimpleSummary.create(target, spec).getSinks().contains(target.getCursor().<J>getValue());
    }

    @Override
    public boolean isFlowParticipant() {
        return SimpleSummary.create(target, spec).getFlowParticipants().contains(target.getCursor().<J>getValue());
    }

    @Value
    static class SimpleSummary {
        Set<J> sinks;
        Set<J> flowParticipants;

        static SimpleSummary create(DataFlowNode target, DataFlowSpec spec) {
            return CursorUtil
                    .findCallableBlockCursor(target.getCursor())
                    .map(c -> create(c, spec))
                    .orSome(() -> new SimpleSummary(new HashSet<>(), new HashSet<>()));
        }

        private static SimpleSummary create(Cursor callableBlock, DataFlowSpec spec) {
            Set<J> sinks = new HashSet<>();
            Set<J> flowParticipants = new HashSet<>();

            new JavaVisitor<Integer>() {
                @Override
                public J visitExpression(Expression expression, Integer integer) {
                    Dataflow.startingAt(getCursor()).findSinks(spec).forEach(summary -> {
                        sinks.add(summary.getSource());
                        flowParticipants.addAll(summary.getFlowParticipants());
                    });
                    return expression;
                }

                @Override
                public J visitVariable(J.VariableDeclarations.NamedVariable variable, Integer integer) {
                    Dataflow.startingAt(getCursor()).findSinks(spec).forEach(summary -> {
                        sinks.add(summary.getSource());
                        flowParticipants.addAll(summary.getFlowParticipants());
                    });
                    return super.visitVariable(variable, integer);
                }
            }.visit(callableBlock.getValue(), 0, callableBlock.getParentOrThrow());

            return new SimpleSummary(sinks, flowParticipants);
        }
    }


}
