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

import lombok.*;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.dataflow.internal.csv.CsvLoader;
import org.openrewrite.analysis.dataflow.internal.csv.GenericExternalModel;
import org.openrewrite.analysis.dataflow.internal.csv.Mergeable;
import org.openrewrite.analysis.trait.expr.Call;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads and stores models from the `model.csv` file to be used for data flow and taint tracking analysis.
 */
@Incubating(since = "7.24.1")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExternalFlowModels {
    private static final String CURSOR_MESSAGE_KEY = "OPTIMIZED_FLOW_MODELS";
    private static final ExternalFlowModels instance = new ExternalFlowModels();

    public static ExternalFlowModels instance() {
        return instance;
    }

    private WeakReference<FullyQualifiedNameToFlowModels> fullyQualifiedNameToFlowModels;

    FullyQualifiedNameToFlowModels getFullyQualifiedNameToFlowModels() {
        FullyQualifiedNameToFlowModels f;
        if (this.fullyQualifiedNameToFlowModels == null) {
            f = Loader.create().load();
            this.fullyQualifiedNameToFlowModels = new WeakReference<>(f);
        } else {
            f = this.fullyQualifiedNameToFlowModels.get();
            if (f == null) {
                f = Loader.create().load();
                this.fullyQualifiedNameToFlowModels = new WeakReference<>(f);
            }
        }
        return f;
    }

    private OptimizedFlowModels getOptimizedFlowModelsForTypesInUse(TypesInUse typesInUse) {
        return Optimizer.optimize(getFullyQualifiedNameToFlowModels().forTypesInUse(typesInUse));
    }

    private OptimizedFlowModels getOrComputeOptimizedFlowModels(Cursor cursor) {
        Cursor cuCursor = cursor.dropParentUntil(JavaSourceFile.class::isInstance);
        return cuCursor.computeMessageIfAbsent(
                CURSOR_MESSAGE_KEY,
                __ -> getOptimizedFlowModelsForTypesInUse(cuCursor.<JavaSourceFile>getValue().getTypesInUse())
        );
    }

    boolean isAdditionalFlowStep(
            DataFlowNode srcNode,
            DataFlowNode sinkNode
    ) {
        for (AdditionalFlowStepPredicate value : getOrComputeOptimizedFlowModels(srcNode.getCursor()).getValuePredicates()) {
            if (value.isAdditionalFlowStep(srcNode, sinkNode)) {
                return true;
            }
        }
        return false;
    }

    boolean isAdditionalTaintStep(
            DataFlowNode srcNode,
            DataFlowNode sinkNode
    ) {
        for (AdditionalFlowStepPredicate taint : getOrComputeOptimizedFlowModels(srcNode.getCursor()).getTaintPredicates()) {
            if (taint.isAdditionalFlowStep(srcNode, sinkNode)) {
                return true;
            }
        }
        return false;
    }

    @AllArgsConstructor
    static final class OptimizedFlowModels {
        private final Map<AdditionalFlowStepPredicate, Set<FlowModel>> value;
        private final Map<AdditionalFlowStepPredicate, Set<FlowModel>> taint;

        Set<AdditionalFlowStepPredicate> getValuePredicates() {
            return value.keySet();
        }

        Set<AdditionalFlowStepPredicate> getTaintPredicates() {
            return taint.keySet();
        }

        Set<FlowModel> getValueFlowModels() {
            return value.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        }

        Set<FlowModel> getTaintFlowModels() {
            return taint.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        }
    }

    /**
     * Dedicated optimization step that attempts to optimize the {@link AdditionalFlowStepPredicate}s
     * and reduce the number of them by merging similar method signatures into a single {@link InvocationMatcher}.
     * <p>
     * <p>
     * As an example, take the following model method signatures:
     * <ul>
     *     <li>{@code "java.lang","String",false,"toLowerCase","","","Argument[-1]","ReturnValue","taint","manual"}</li>
     *     <li>{@code "java.lang","String",false,"toUpperCase","","","Argument[-1]","ReturnValue","taint","manual"}</li>
     *     <li>{@code "java.lang","String",false,"trim","","","Argument[-1]","ReturnValue","taint","manual"}</li>
     * </ul>
     * <p>
     * These can be merged into a single {@link InvocationMatcher} that matches all these methods.
     * <p>
     * From there, a single {@link InvocationMatcher.AdvancedInvocationMatcher} can be called by the
     * {@link AdditionalFlowStepPredicate}.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    static class Optimizer {

        /**
         * Return the 'optimized' {@link AdditionalFlowStepPredicate} for the {@link MethodMatcher}.
         */
        private AdditionalFlowStepPredicate forFlowFromArgumentIndexToReturn(
                int argumentIndex,
                Collection<? extends InvocationMatcher> methodMatchers
        ) {
            InvocationMatcher callMatcher = InvocationMatcher.from(methodMatchers);
            if (argumentIndex == -1) {
                // Argument[-1] is the 'select' or 'qualifier' of a method call
                return (srcNode, sinkNode) ->
                        sinkNode.asExprParent(Call.class).map(call -> call.matches(callMatcher)).orSome(false) &&
                                callMatcher.advanced().isSelect(srcNode.getCursor());
            } else {
                return (srcNode, sinkNode) ->
                        sinkNode.asExprParent(Call.class).map(call -> call.matches(callMatcher)).orSome(false) &&
                                callMatcher.advanced().isParameter(srcNode.getCursor(), argumentIndex);
            }
        }

        /**
         * Return the 'optimized' {@link AdditionalFlowStepPredicate} for the {@link MethodMatcher}.
         */
        private AdditionalFlowStepPredicate forFlowFromArgumentIndexToQualifier(
                int argumentIndex,
                Collection<? extends InvocationMatcher> methodMatchers
        ) {
            InvocationMatcher callMatcher = InvocationMatcher.from(methodMatchers);
            assert argumentIndex != -1 : "Argument[-1] is the 'select' or 'qualifier' of a method call. Flow would be cyclic.";
            return (srcNode, sinkNode) ->
                    callMatcher.advanced().isSelect(sinkNode.getCursor()) &&
                            callMatcher.advanced().isParameter(srcNode.getCursor(), argumentIndex);
        }

        private AdditionalFlowStepPredicate forFlowFromArgumentIndexToArgumentIndex(
                ArgumentIndices argumentIndices,
                Collection<? extends InvocationMatcher> methodMatchers
        ) {
            InvocationMatcher callMatcher = InvocationMatcher.from(methodMatchers);
            if (argumentIndices.inputIndex == -1) {
                return (srcNode, sinkNode) ->
                        callMatcher.advanced().isSelect(srcNode.getCursor()) &&
                                callMatcher.advanced().isParameter(sinkNode.getCursor(), argumentIndices.outputIndex);
            } else {
                return (srcNode, sinkNode) ->
                        callMatcher.advanced().isParameter(srcNode.getCursor(), argumentIndices.inputIndex) &&
                                callMatcher.advanced().isParameter(sinkNode.getCursor(), argumentIndices.outputIndex);
            }
        }

        @Value
        static class PredicateToFlowModels {
            AdditionalFlowStepPredicate predicate;
            Set<FlowModel> models;
        }

        @Value
        static class ArgumentIndices {
            int inputIndex;
            int outputIndex;
        }

        private Map<AdditionalFlowStepPredicate, Set<FlowModel>> optimize(Collection<FlowModel> models) {
            Map<Integer, Set<FlowModel>> flowFromArgumentIndexToReturn = new HashMap<>();
            Map<Integer, Set<FlowModel>> flowFromArgumentIndexToQualifier = new HashMap<>();
            Map<ArgumentIndices, Set<FlowModel>> flowFromArgumentIndexToArgumentIndex = new HashMap<>();
            models.forEach(model -> {
                if ("ReturnValue".equals(model.output) || model.isConstructor()) {
                    model.getArgumentRange().ifPresent(argumentRange -> {
                        for (int i = argumentRange.getStart(); i <= argumentRange.getEnd(); i++) {
                            flowFromArgumentIndexToReturn.computeIfAbsent(i, __ -> new HashSet<>())
                                    .add(model);
                        }
                    });
                }
                if ("Argument[-1]".equals(model.output) && !model.isConstructor()) {
                    model.getArgumentRange().ifPresent(argumentRange -> {
                        for (int i = argumentRange.getStart(); i <= argumentRange.getEnd(); i++) {
                            flowFromArgumentIndexToQualifier.computeIfAbsent(i, __ -> new HashSet<>())
                                    .add(model);
                        }
                    });
                }
                Optional<GenericExternalModel.ArgumentRange> inputRange = GenericExternalModel.computeArgumentRange(model.input);
                Optional<GenericExternalModel.ArgumentRange> outputRange = GenericExternalModel.computeArgumentRange(model.output);
                if (inputRange.isPresent() && outputRange.isPresent()) {
                    for (int i = inputRange.get().getStart(); i <= inputRange.get().getEnd(); i++) {
                        for (int j = outputRange.get().getStart(); j <= outputRange.get().getEnd(); j++) {
                            if (j >= 0) {
                                flowFromArgumentIndexToArgumentIndex.computeIfAbsent(new ArgumentIndices(i, j), __ -> new HashSet<>())
                                        .add(model);
                            }
                        }
                    }
                }
            });

            Stream<PredicateToFlowModels> flowFromArgumentIndexToReturnStream =
                    flowFromArgumentIndexToReturn
                            .entrySet()
                            .stream()
                            .map(entry -> new PredicateToFlowModels(forFlowFromArgumentIndexToReturn(
                                        entry.getKey(),
                                        entry.getValue()
                                ), entry.getValue()));
            Stream<PredicateToFlowModels> flowFromArgumentIndexToQualifierStream =
                    flowFromArgumentIndexToQualifier
                            .entrySet()
                            .stream()
                            .map(entry -> new PredicateToFlowModels(forFlowFromArgumentIndexToQualifier(
                                        entry.getKey(),
                                        entry.getValue()
                                ), entry.getValue()));
            Stream<PredicateToFlowModels> flowFromArgumentIndexToArgumentIndexStream =
                    flowFromArgumentIndexToArgumentIndex
                            .entrySet()
                            .stream()
                            .map(entry -> new PredicateToFlowModels(forFlowFromArgumentIndexToArgumentIndex(
                                        entry.getKey(),
                                        entry.getValue()
                                ), entry.getValue()));

            Stream<PredicateToFlowModels> s1 = Stream.concat(flowFromArgumentIndexToReturnStream, flowFromArgumentIndexToQualifierStream);
            return Stream.concat(s1, flowFromArgumentIndexToArgumentIndexStream)
                    .collect(Collectors.toMap(PredicateToFlowModels::getPredicate,
                            PredicateToFlowModels::getModels,
                            (a, b) -> {
                                throw new IllegalStateException("Not expecting duplicate keys");
                            },
                            IdentityHashMap::new));
        }

        static OptimizedFlowModels optimize(FlowModels flowModels) {
            Optimizer optimizer = new Optimizer();
            return new OptimizedFlowModels(
                    optimizer.optimize(flowModels.value),
                    optimizer.optimize(flowModels.taint)
            );
        }
    }

    @AllArgsConstructor
    static class FlowModels {
        Set<FlowModel> value;
        Set<FlowModel> taint;
    }

    @AllArgsConstructor
    static class FullyQualifiedNameToFlowModels implements Mergeable<FullyQualifiedNameToFlowModels> {
        private final Map<String, List<FlowModel>> value;
        private final Map<String, List<FlowModel>> taint;

        boolean isEmpty() {
            return value.isEmpty() && taint.isEmpty();
        }

        @Override
        public FullyQualifiedNameToFlowModels merge(FullyQualifiedNameToFlowModels other) {
            if (this.isEmpty()) {
                return other;
            } else if (other.isEmpty()) {
                return this;
            }
            Map<String, List<FlowModel>> value = new HashMap<>(this.value);
            other.value.forEach((k, v) -> value.computeIfAbsent(k, kk -> new ArrayList<>(v.size())).addAll(v));
            Map<String, List<FlowModel>> taint = new HashMap<>(this.taint);
            other.taint.forEach((k, v) -> taint.computeIfAbsent(k, kk -> new ArrayList<>(v.size())).addAll(v));
            return new FullyQualifiedNameToFlowModels(value, taint);
        }

        FlowModels forAll() {
            return new FlowModels(
                    this.value.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()),
                    this.taint.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())
            );
        }

        /**
         * Loads the subset of {@link FlowModel}s that are relevant for the given {@link TypesInUse}.
         * <p>
         * This optimization prevents the generation of {@link AdditionalFlowStepPredicate} and {@link InvocationMatcher}
         * for method signatures that aren't even present in {@link J.CompilationUnit}.
         */
        FlowModels forTypesInUse(TypesInUse typesInUse) {
            Set<FlowModel> value = new HashSet<>();
            Set<FlowModel> taint = new HashSet<>();
            //noinspection ConstantConditions
            typesInUse
                    .getUsedMethods()
                    .stream()
                    .map(JavaType.Method::getDeclaringType)
                    .filter(o -> o != null && !(o instanceof JavaType.Unknown))
                    .flatMap(FullyQualifiedNameToFlowModels::getAllTypesInHierarchy)
                    .map(JavaType.FullyQualified::getFullyQualifiedName)
                    .distinct()
                    .forEach(fqn -> {
                        value.addAll(this.value.getOrDefault(
                                fqn,
                                Collections.emptyList()
                        ));
                        taint.addAll(this.taint.getOrDefault(
                                fqn,
                                Collections.emptyList()
                        ));
                    });
            return new FlowModels(
                    value,
                    taint
            );
        }

        static Stream<JavaType.FullyQualified> getAllTypesInHierarchy(JavaType.FullyQualified type) {
            if (type.getSupertype() == null) {
                return Stream.concat(
                        Stream.of(type),
                        type.getInterfaces().stream().flatMap(FullyQualifiedNameToFlowModels::getAllTypesInHierarchy)
                );
            }
            return Stream.concat(
                    Stream.of(type),
                    Stream.concat(
                            type.getInterfaces().stream().flatMap(FullyQualifiedNameToFlowModels::getAllTypesInHierarchy),
                            getAllTypesInHierarchy(type.getSupertype())
                    )
            );
        }

        static FullyQualifiedNameToFlowModels empty() {
            return new FullyQualifiedNameToFlowModels(new HashMap<>(0), new HashMap<>(0));
        }
    }

    @AllArgsConstructor
    @ToString
    static class FlowModel implements GenericExternalModel {
        // package, type, subtypes, name, signature, ext, input, output, kind, provenance
        @Getter
        String namespace;

        @Getter
        String type;

        @Getter
        boolean subtypes;

        @Getter
        String name;

        @Getter
        String signature;

        String ext;
        String input;
        String output;
        String kind;
        String provenance;

        @Override
        public String getArguments() {
            return input;
        }
    }

    private static class Loader {

        private static Loader create() {
            return new Loader();
        }

        FullyQualifiedNameToFlowModels load() {
            return loadModelFromFile();
        }

        private FullyQualifiedNameToFlowModels loadModelFromFile() {
            return CsvLoader.loadFromFile(
                    "model.csv",
                    FullyQualifiedNameToFlowModels.empty(),
                    Loader::createFullyQualifiedNameToFlowModels,
                    tokens -> new FlowModel(
                            tokens[0],
                            tokens[1],
                            Boolean.parseBoolean(tokens[2]),
                            tokens[3],
                            tokens[4],
                            tokens[5],
                            tokens[6],
                            tokens[7],
                            tokens[8],
                            tokens[9]
                    )
            );
        }

        private static FullyQualifiedNameToFlowModels createFullyQualifiedNameToFlowModels(Iterable<FlowModel> flowModels) {
            Map<String, List<FlowModel>> value = new HashMap<>();
            Map<String, List<FlowModel>> taint = new HashMap<>();
            for (FlowModel model : flowModels) {
                if ("value".equals(model.kind)) {
                    value.computeIfAbsent(model.getFullyQualifiedName(), k -> new ArrayList<>()).add(model);
                } else if ("taint".equals(model.kind)) {
                    taint.computeIfAbsent(model.getFullyQualifiedName(), k -> new ArrayList<>()).add(model);
                } else {
                    throw new IllegalArgumentException("Unknown kind: " + model.kind);
                }
            }
            return new FullyQualifiedNameToFlowModels(value, taint);
        }
    }
}
