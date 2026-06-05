/*
 * Copyright 2025 the original author or authors.
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

import lombok.Value;
import org.openrewrite.analysis.InvocationMatcher;

/**
 * A higher-order ("lambda call") flow model derived from a CodeQL-style summary whose access path
 * references a functional argument's parameter or return value. Mirrors CodeQL's
 * {@code lambdaCall}/{@code lambdaCreation}: when a method is invoked with a lambda (or anonymous
 * functional implementation) argument, data can flow into that lambda's parameter or out of its
 * return value.
 * <ul>
 *     <li>{@link Direction#INTO}: data flows from the {@link #other} position of the call into
 *     parameter {@link #parameter} of the lambda passed as argument {@link #callbackArgument}
 *     (e.g. {@code Iterable.forEach}: {@code Argument[-1] -> Argument[0].Parameter[0]}).</li>
 *     <li>{@link Direction#OUT}: data flows from the return value of the lambda passed as argument
 *     {@link #callbackArgument} to the {@link #other} position of the call
 *     (e.g. {@code Map.computeIfAbsent}: {@code Argument[1].ReturnValue -> ReturnValue}).</li>
 * </ul>
 */
@Value
public class CallbackFlowModel {
    public enum Direction {
        INTO,
        OUT
    }

    InvocationMatcher matcher;

    Direction direction;

    /** The argument index of the call holding the lambda. {@code -1} denotes the qualifier. */
    int callbackArgument;

    /** For {@link Direction#INTO}, the lambda parameter index that receives the data. */
    int parameter;

    /** The non-callback side of the flow (the source for {@code INTO}, the destination for {@code OUT}). */
    Position other;

    /** A position at a call site: the qualifier, an argument, or the return value. */
    @Value
    public static class Position {
        public enum Kind {
            QUALIFIER,
            ARGUMENT,
            RETURN_VALUE
        }

        Kind kind;

        /** The argument index when {@link #kind} is {@link Kind#ARGUMENT}; otherwise unused. */
        int argument;

        public static Position qualifier() {
            return new Position(Kind.QUALIFIER, -1);
        }

        public static Position returnValue() {
            return new Position(Kind.RETURN_VALUE, -1);
        }

        public static Position argument(int index) {
            return index == -1 ? qualifier() : new Position(Kind.ARGUMENT, index);
        }
    }
}
