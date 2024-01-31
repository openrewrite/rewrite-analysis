/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.analysis.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.TaintFlowSpec;
import org.openrewrite.analysis.dataflow.global.GlobalDataFlow;
import org.openrewrite.analysis.trait.expr.Call;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.MethodMatcher;

import java.util.function.Predicate;

/**
 * Finds either Taint or Data flow between specified start and end methods.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class FindFlowBetweenMethods extends ScanningRecipe<GlobalDataFlow.Accumulator> {

    /**
     * A method pattern that is used to find matching method invocations.
     * See {@link MethodMatcher} for details on the expression's syntax.
     */
    @Option(displayName = "Start method pattern", description = "A method pattern that is used to find matching the start point's method invocations.", example = "java.util.List add(..)")
    String startMethodPattern;

    @Option(displayName = "Match start method on overrides", description = "When enabled, find methods that are overrides of the method pattern.", required = false)
    @Nullable Boolean startMatchOverrides;
    @Option(displayName = "End method pattern", description = "A method pattern that is used to find matching the end point's method invocations.", example = "java.util.List add(..)")
    String endMethodPattern;

    @Option(displayName = "Match end method on overrides", description = "When enabled, find methods that are overrides of the method pattern.", required = false)
    @Nullable Boolean endMatchOverrides;

    @Option(displayName = "To target", description = "The part of the method flow should traverse to", required = true, valid = {"Select", "Arguments", "Both"})
    String target;

    @Option(displayName = "Show flow", description = "When enabled, show the data or taint flow of the method invocation.", valid = {"Data", "Taint"}, required = true)
    @Nullable String flow;


    @Override
    public String getDisplayName() {
        return "Finds flow between two methods";
    }

    @Override
    public String getDescription() {
        return "Takes two patterns for the start/end methods to find flow between.";
    }

    @Override
    public GlobalDataFlow.Accumulator getInitialValue(ExecutionContext ctx) {
        InvocationMatcher startMatcher = InvocationMatcher.fromMethodMatcher(startMethodPattern, startMatchOverrides);
        InvocationMatcher endMatcher = InvocationMatcher.fromMethodMatcher(endMethodPattern, endMatchOverrides);

        InvocationMatcher.AdvancedInvocationMatcher endAdvanced = endMatcher.advanced();

        final Predicate<Cursor> sinkMatcher;
        switch (target) {
            case "Select":
                sinkMatcher = endAdvanced::isSelect;
                break;
            case "Arguments":
                sinkMatcher = endAdvanced::isAnyArgument;
                break;
            case "Both":
                sinkMatcher = cursor -> endAdvanced.isAnyArgument(cursor) ||
                                        endAdvanced.isSelect(cursor);
                break;
            default:
                throw new IllegalStateException("Unknown target: " + target);
        }


        String flow = this.flow == null ? "Data" : this.flow;
        if ("Taint".equals(flow)) {
            return GlobalDataFlow.accumulator(new TaintFlowSpec() {

                @Override
                public boolean isSource(DataFlowNode srcNode) {
                    return FindFlowBetweenMethods.isSource(srcNode, startMatcher);
                }

                @Override
                public boolean isSink(DataFlowNode sinkNode) {
                    return sinkMatcher.test(sinkNode.getCursor());
                }
            });
        }
        return GlobalDataFlow.accumulator(new DataFlowSpec() {

            @Override
            public boolean isSource(DataFlowNode srcNode) {
                return FindFlowBetweenMethods.isSource(srcNode, startMatcher);
            }

            @Override
            public boolean isSink(DataFlowNode sinkNode) {
                return sinkMatcher.test(sinkNode.getCursor());
            }
        });
    }

    private static boolean isSource(DataFlowNode srcNode, InvocationMatcher startMatcher) {
        return srcNode
                .asExprParent(Call.class)
                .bind(Call::getMethodType)
                .filter(startMatcher::matches)
                .isSome();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(GlobalDataFlow.Accumulator acc) {
        return acc.scanner();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(GlobalDataFlow.Accumulator acc) {
        return acc.renderer();
    }
}
