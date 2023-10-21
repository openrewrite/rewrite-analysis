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
import org.openrewrite.java.internal.TypesInUse;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Incubating(since = "7.25.0")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExternalSinkModels {
    private static final String CURSOR_MESSAGE_KEY = "OPTIMIZED_SINK_MODELS";
    private static final ExternalSinkModels instance = new ExternalSinkModels();

    /**
     * @deprecated Use {@link #instance()} instead.
     */
    @Deprecated
    public static ExternalSinkModels getInstance() {
        return instance;
    }

    public static ExternalSinkModels instance() {
        return instance;
    }

    private WeakReference<FullyQualifiedNameToSinkModels> fullyQualifiedNameToSinkModel;

    FullyQualifiedNameToSinkModels getFullyQualifiedNameToSinkModel() {
        FullyQualifiedNameToSinkModels f;
        if (fullyQualifiedNameToSinkModel == null) {
            f = Loader.load();
            fullyQualifiedNameToSinkModel = new WeakReference<>(f);
        } else {
            f = fullyQualifiedNameToSinkModel.get();
            if (f == null) {
                f = Loader.load();
                fullyQualifiedNameToSinkModel = new WeakReference<>(f);
            }
        }
        return f;
    }

    private OptimizedSinkModels getOptimizedSinkModelsForTypesInUse(TypesInUse typesInUse) {
        return Optimizer.optimize(getFullyQualifiedNameToSinkModel().forTypesInUse(typesInUse));
    }

    private OptimizedSinkModels getOrComputeOptimizedSinkModels(Cursor cursor) {
        Cursor cuCursor = cursor.dropParentUntil(JavaSourceFile.class::isInstance);
        return cuCursor.computeMessageIfAbsent(
                CURSOR_MESSAGE_KEY,
                __ -> getOptimizedSinkModelsForTypesInUse(cuCursor.<JavaSourceFile>getValue().getTypesInUse())
        );
    }

    /**
     * True if the {@code expression} {@code cursor} is specified as a sink with the given {@code kind} in the
     * CSV flow model.
     *
     * @return If this is a sink of the given {@code kind}.
     */
    public boolean isSinkNode(DataFlowNode sinkNode, String kind) {
        for (SinkNodePredicate predicate : getOrComputeOptimizedSinkModels(sinkNode.getCursor()).forKind(kind)) {
            if (predicate.isSinkNode(sinkNode)) {
                return true;
            }
        }
        return false;
    }

    private interface SinkNodePredicate {
        boolean isSinkNode(DataFlowNode sinkNode);
    }

    @Value
    static class PredicateToSinkModels implements SinkNodePredicate {
        SinkNodePredicate predicate;
        Set<SinkModel> sinkModels;

        @Override
        public boolean isSinkNode(DataFlowNode sinkNode) {
            return predicate.isSinkNode(sinkNode);
        }
    }

    @AllArgsConstructor
    static class OptimizedSinkModels {
        private final Map<String, Set<PredicateToSinkModels>> sinkKindToPredicates;

        private Set<? extends SinkNodePredicate> forKind(String kind) {
            return sinkKindToPredicates.getOrDefault(kind, Collections.emptySet());
        }

        Set<SinkModel> getSinkModels() {
            return sinkKindToPredicates.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .map(PredicateToSinkModels::getSinkModels)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
        }

    }

    static class Optimizer {

        private SinkNodePredicate sinkNodePredicateForArgumentIndex(
                int argumentIndex,
                Collection<? extends InvocationMatcher> methodMatchers
        ) {
            InvocationMatcher invocationMatcher = InvocationMatcher.from(methodMatchers);
            return argumentIndex == -1 ?
                    (sinkNode -> invocationMatcher.advanced().isSelect(sinkNode.getCursor())) :
                    (sinkNode -> invocationMatcher.advanced().isParameter(sinkNode.getCursor(), argumentIndex));
        }

        private SinkNodePredicate sinkNodePredicateForReturnValue(
                Collection<? extends InvocationMatcher> methodMatchers
        ) {
            InvocationMatcher invocationMatcher = InvocationMatcher.from(methodMatchers);
            return sinkNode -> sinkNode.asExprParent(Call.class).map(call -> call.matches(invocationMatcher)).orSome(false);
        }

        private Set<PredicateToSinkModels> optimize(Collection<SinkModel> models) {
            Map<Integer, Set<SinkModel>> sinkForArgument = new HashMap<>();
            // Uncommon, so don't allocate unless needed
            Set<SinkModel> sinkForReturnValue = new HashSet<>(0);
            for (SinkModel model : models) {
                model.getArgumentRange().ifPresent(argumentRange -> {
                    for (int i = argumentRange.getStart(); i <= argumentRange.getEnd(); i++) {
                        sinkForArgument.computeIfAbsent(i, __ -> new HashSet<>()).add(model);
                    }
                });
                if ("ReturnValue".equals(model.input)) {
                    sinkForReturnValue.add(model);
                }
            }
            Stream<PredicateToSinkModels> predicateToSinkModelsStream =
                    sinkForArgument
                            .entrySet()
                            .stream()
                            .map(entry -> new PredicateToSinkModels(
                                    sinkNodePredicateForArgumentIndex(entry.getKey(), entry.getValue()),
                                    entry.getValue()
                            ));
            Stream<PredicateToSinkModels> returnValuePredicateToSinkModelsStream =
                    sinkForReturnValue.isEmpty() ? Stream.empty() : Stream.of(new PredicateToSinkModels(
                            sinkNodePredicateForReturnValue(sinkForReturnValue),
                            sinkForReturnValue
                    ));
            return Stream
                    .concat(predicateToSinkModelsStream, returnValuePredicateToSinkModelsStream)
                    .collect(Collectors.toSet());
        }

        static OptimizedSinkModels optimize(SinkModels sinkModels) {
            Optimizer optimizer = new Optimizer();
            Map<String, Set<PredicateToSinkModels>> sinkKindToPredicates =
                    sinkModels
                            .sinkModels
                            .entrySet()
                            .stream()
                            .map(e -> new AbstractMap.SimpleEntry<>(
                                    e.getKey(),
                                    optimizer.optimize(e.getValue())
                            ))
                            .collect(Collectors.toMap(
                                    AbstractMap.SimpleEntry::getKey,
                                    AbstractMap.SimpleEntry::getValue
                            ));
            return new OptimizedSinkModels(sinkKindToPredicates);
        }
    }

    @AllArgsConstructor
    static class SinkModels {
        Map<String /* SinkModel.kind */, Set<SinkModel>> sinkModels;

        Set<SinkModel> getSinkModels() {
            return sinkModels.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        }
    }

    @AllArgsConstructor
    static class FullyQualifiedNameToSinkModels implements Mergeable<FullyQualifiedNameToSinkModels> {
        private final Map<String, List<SinkModel>> fqnToSinkModels;

        boolean isEmpty() {
            return fqnToSinkModels.isEmpty();
        }

        @Override
        public FullyQualifiedNameToSinkModels merge(FullyQualifiedNameToSinkModels other) {
            if (other.isEmpty()) {
                return this;
            } else if (isEmpty()) {
                return other;
            }
            Map<String, List<SinkModel>> merged = new HashMap<>(this.fqnToSinkModels);
            other.fqnToSinkModels.forEach((k, v) -> merged.computeIfAbsent(k, kk -> new ArrayList<>(v.size())).addAll(v));
            return new FullyQualifiedNameToSinkModels(merged);
        }

        /**
         * Loads the subset of {@link SinkModel}s that are relevant to the given {@link TypesInUse}.
         * <p>
         * This optimization prevents the generation of {@link AdditionalFlowStepPredicate} and {@link InvocationMatcher}
         * for method signatures that aren't even present in {@link J.CompilationUnit}.
         */
        SinkModels forTypesInUse(TypesInUse typesInUse) {
            Map<String, Set<SinkModel>> sinkModels = new HashMap<>();
            //noinspection ConstantConditions
            typesInUse
                    .getUsedMethods()
                    .stream()
                    .map(JavaType.Method::getDeclaringType)
                    .filter(o -> o != null && !(o instanceof JavaType.Unknown))
                    .map(JavaType.FullyQualified::getFullyQualifiedName)
                    .distinct()
                    .flatMap(fqn -> fqnToSinkModels.getOrDefault(
                                    fqn,
                                    Collections.emptyList()
                            ).stream()
                    ).forEach(sinkModel ->
                            sinkModels.computeIfAbsent(sinkModel.kind, k -> new HashSet<>(1)).add(sinkModel)
                    );
            return new SinkModels(sinkModels);
        }

        SinkModels forAll() {
            return new SinkModels(fqnToSinkModels.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> new HashSet<>(e.getValue())
            )));
        }

        static FullyQualifiedNameToSinkModels empty() {
            return new FullyQualifiedNameToSinkModels(new HashMap<>(0));
        }
    }

    @AllArgsConstructor
    @ToString
    static class SinkModel implements GenericExternalModel {
        // package, type, subtypes, name, signature, ext, input, kind, provenance
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
        String kind;
        String provenance;

        @Override
        public String getArguments() {
            return input;
        }
    }

    static class Loader {
        static FullyQualifiedNameToSinkModels load() {
            return CsvLoader.loadFromFile(
                    "sinks.csv",
                    FullyQualifiedNameToSinkModels.empty(),
                    Loader::createFullyQualifiedNameToFlowModels,
                    tokens -> new SinkModel(
                            tokens[0],
                            tokens[1],
                            Boolean.parseBoolean(tokens[2]),
                            tokens[3],
                            tokens[4],
                            tokens[5],
                            tokens[6],
                            tokens[7],
                            tokens[8]
                    )
            );
        }

        private static FullyQualifiedNameToSinkModels createFullyQualifiedNameToFlowModels(Iterable<SinkModel> sinkModels) {
            Map<String, List<SinkModel>> fqnToSinkModels = new HashMap<>();
            for (SinkModel sinkModel : sinkModels) {
                fqnToSinkModels.computeIfAbsent(sinkModel.getFullyQualifiedName(), k -> new ArrayList<>()).add(sinkModel);
            }
            return new FullyQualifiedNameToSinkModels(fqnToSinkModels);
        }
    }
}
