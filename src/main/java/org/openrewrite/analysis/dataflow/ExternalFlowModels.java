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
import org.openrewrite.analysis.dataflow.internal.csv.AccessPath;
import org.openrewrite.analysis.dataflow.internal.csv.CsvLoader;
import org.openrewrite.analysis.dataflow.internal.csv.GenericExternalModel;
import org.openrewrite.analysis.dataflow.internal.csv.Mergeable;
import org.openrewrite.analysis.trait.expr.Call;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

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

    private SoftReference<FullyQualifiedNameToFlowModels> fullyQualifiedNameToFlowModels;

    FullyQualifiedNameToFlowModels getFullyQualifiedNameToFlowModels() {
        FullyQualifiedNameToFlowModels f;
        if (this.fullyQualifiedNameToFlowModels == null) {
            f = Loader.create().load();
            this.fullyQualifiedNameToFlowModels = new SoftReference<>(f);
        } else {
            f = this.fullyQualifiedNameToFlowModels.get();
            if (f == null) {
                f = Loader.create().load();
                this.fullyQualifiedNameToFlowModels = new SoftReference<>(f);
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

    /**
     * The higher-order ("lambda call") value-flow models applicable to the compilation unit
     * enclosing {@code cursor}. Callers should filter these to the ones whose matcher matches a
     * particular call.
     */
    List<CallbackFlowModel> valueCallbackFlowModels(Cursor cursor) {
        return getOrComputeOptimizedFlowModels(cursor).getValueCallbacks();
    }

    List<CallbackFlowModel> taintCallbackFlowModels(Cursor cursor) {
        return getOrComputeOptimizedFlowModels(cursor).getTaintCallbacks();
    }

    @AllArgsConstructor
    static final class OptimizedFlowModels {
        private final Map<AdditionalFlowStepPredicate, Set<FlowModel>> value;
        private final Map<AdditionalFlowStepPredicate, Set<FlowModel>> taint;
        @Getter
        private final List<CallbackFlowModel> valueCallbacks;
        @Getter
        private final List<CallbackFlowModel> taintCallbacks;

        Set<AdditionalFlowStepPredicate> getValuePredicates() {
            return value.keySet();
        }

        Set<AdditionalFlowStepPredicate> getTaintPredicates() {
            return taint.keySet();
        }

        Set<FlowModel> getValueFlowModels() {
            return value.values().stream().flatMap(Collection::stream).collect(toSet());
        }

        Set<FlowModel> getTaintFlowModels() {
            return taint.values().stream().flatMap(Collection::stream).collect(toSet());
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
            }
            return (srcNode, sinkNode) ->
                    sinkNode.asExprParent(Call.class).map(call -> call.matches(callMatcher)).orSome(false) &&
                            callMatcher.advanced().isParameter(srcNode.getCursor(), argumentIndex);
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
            }
            return (srcNode, sinkNode) ->
                    callMatcher.advanced().isParameter(srcNode.getCursor(), argumentIndices.inputIndex) &&
                            callMatcher.advanced().isParameter(sinkNode.getCursor(), argumentIndices.outputIndex);
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

        /** Holds if the access path denotes exactly the receiver — {@code Argument[-1]} or {@code Argument[this]}. */
        private static boolean isReceiver(Optional<GenericExternalModel.ArgumentRange> range) {
            return range.map(r -> r.getStart() == -1 && r.getEnd() == -1).orElse(false);
        }

        private Map<AdditionalFlowStepPredicate, Set<FlowModel>> optimize(Collection<FlowModel> models) {
            Map<Integer, Set<FlowModel>> flowFromArgumentIndexToReturn = new HashMap<>();
            Map<Integer, Set<FlowModel>> flowFromArgumentIndexToQualifier = new HashMap<>();
            Map<ArgumentIndices, Set<FlowModel>> flowFromArgumentIndexToArgumentIndex = new HashMap<>();
            models.forEach(model -> {
                Optional<GenericExternalModel.ArgumentRange> inputRange = GenericExternalModel.computeArgumentRange(model.input);
                Optional<GenericExternalModel.ArgumentRange> outputRange = GenericExternalModel.computeArgumentRange(model.output);
                if ("ReturnValue".equals(model.output) || model.isConstructor()) {
                    inputRange.ifPresent(argumentRange -> {
                        for (int i = argumentRange.getStart(); i <= argumentRange.getEnd(); i++) {
                            flowFromArgumentIndexToReturn.computeIfAbsent(i, __ -> new HashSet<>())
                                    .add(model);
                        }
                    });
                }
                // Flow into the receiver, spelled `Argument[-1]` (older dialect) or `Argument[this]`
                // (current dialect); both resolve to position -1.
                if (isReceiver(outputRange) && !model.isConstructor()) {
                    inputRange.ifPresent(argumentRange -> {
                        for (int i = argumentRange.getStart(); i <= argumentRange.getEnd(); i++) {
                            flowFromArgumentIndexToQualifier.computeIfAbsent(i, __ -> new HashSet<>())
                                    .add(model);
                        }
                    });
                }
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
                    .collect(toMap(PredicateToFlowModels::getPredicate,
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
                    optimizer.optimize(flowModels.taint),
                    buildCallbackModels(flowModels.value),
                    buildCallbackModels(flowModels.taint)
            );
        }

        /**
         * Builds the higher-order ("lambda call") models from rows whose access path references a
         * functional argument's parameter or return value. Content components have already been
         * collapsed by {@link AccessPath}. Rows where <em>both</em> sides are callbacks, or where
         * either side is unparseable, are skipped.
         */
        private static List<CallbackFlowModel> buildCallbackModels(Collection<FlowModel> models) {
            List<CallbackFlowModel> callbacks = new ArrayList<>();
            for (FlowModel model : models) {
                // A callback path is always `Argument[i].ReturnValue` (OUT) or `Argument[i].Parameter[j]`
                // (INTO), both of which contain a '.'. The plain-flow majority has none, so skip it here
                // rather than allocating two access-path parses per model on this per-compilation-unit path.
                if (model.input.indexOf('.') < 0 && model.output.indexOf('.') < 0) {
                    continue;
                }
                Optional<AccessPath> maybeIn = AccessPath.parse(model.input);
                Optional<AccessPath> maybeOut = AccessPath.parse(model.output);
                if (!maybeIn.isPresent() || !maybeOut.isPresent()) {
                    continue;
                }
                AccessPath in = maybeIn.get();
                AccessPath out = maybeOut.get();
                InvocationMatcher matcher = InvocationMatcher.from(java.util.Collections.singletonList(model));
                if (out.getCallbackKind() == AccessPath.CallbackKind.PARAMETER &&
                    !in.isCallback() && in.getRoot() == AccessPath.Root.ARGUMENT) {
                    // INTO: in (an argument/qualifier) flows to Argument[i].Parameter[j].
                    forEachIndex(out.getRootRange(), i ->
                            forEachIndex(out.getCallbackRange(), j ->
                                    forEachIndex(in.getRootRange(), k ->
                                            callbacks.add(new CallbackFlowModel(
                                                    matcher,
                                                    CallbackFlowModel.Direction.INTO,
                                                    i,
                                                    j,
                                                    CallbackFlowModel.Position.argument(k))))));
                } else if (in.getCallbackKind() == AccessPath.CallbackKind.RETURN_VALUE && !out.isCallback()) {
                    // OUT: Argument[i].ReturnValue flows to out (the return value or an argument/qualifier).
                    forEachIndex(in.getRootRange(), i -> {
                        if (out.getRoot() == AccessPath.Root.RETURN_VALUE) {
                            callbacks.add(new CallbackFlowModel(
                                    matcher,
                                    CallbackFlowModel.Direction.OUT,
                                    i,
                                    -1,
                                    CallbackFlowModel.Position.returnValue()));
                        } else {
                            forEachIndex(out.getRootRange(), k ->
                                    callbacks.add(new CallbackFlowModel(
                                            matcher,
                                            CallbackFlowModel.Direction.OUT,
                                            i,
                                            -1,
                                            CallbackFlowModel.Position.argument(k))));
                        }
                    });
                }
            }
            return callbacks;
        }

        private static void forEachIndex(GenericExternalModel.ArgumentRange range, java.util.function.IntConsumer consumer) {
            for (int i = range.getStart(); i <= range.getEnd(); i++) {
                consumer.accept(i);
            }
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
            }
            if (other.isEmpty()) {
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
                    this.value.values().stream().flatMap(Collection::stream).collect(toSet()),
                    this.taint.values().stream().flatMap(Collection::stream).collect(toSet())
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
                                emptyList()
                        ));
                        taint.addAll(this.taint.getOrDefault(
                                fqn,
                                emptyList()
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
        // CSV columns: package, type, subtypes, name, signature, ext, input, output, kind, provenance.
        // `ext` and `provenance` are not read by the engine, so they are dropped rather than retained
        // across every row of the (large) model set.
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

        String input;
        String output;
        String kind;

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
                            // tokens[5] = ext (unused)
                            tokens[6],
                            tokens[7],
                            tokens[8]
                            // tokens[9] = provenance (unused)
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
