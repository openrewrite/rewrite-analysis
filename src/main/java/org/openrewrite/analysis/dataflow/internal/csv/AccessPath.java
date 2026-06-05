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
package org.openrewrite.analysis.dataflow.internal.csv;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.analysis.dataflow.internal.csv.GenericExternalModel.ArgumentRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A structured representation of a CodeQL-style data-flow access path such as {@code Argument[0]},
 * {@code Argument[-1].Element}, {@code Argument[0].Parameter[1]} or {@code Argument[1].ReturnValue}.
 * <p>
 * An access path is parsed into a {@link Root} (an argument position or the method return value) and
 * an optional <em>callback</em> component describing flow into a functional argument's parameter
 * ({@link CallbackKind#PARAMETER}) or out of a functional argument's return value
 * ({@link CallbackKind#RETURN_VALUE}). This is what enables higher-order ("lambda call") flow.
 * <p>
 * Content components ({@code Element}, {@code ArrayElement}, {@code MapKey}, {@code MapValue},
 * {@code Field[...]}, {@code SyntheticField[...]}) are <em>collapsed</em> onto their container: this
 * analysis is not content/field sensitive, so {@code Argument[-1].Element} is treated as
 * {@code Argument[-1]}. This is an over-approximation consistent with the engine's whole-value
 * granularity.
 */
@Value
public class AccessPath {
    public enum Root {
        ARGUMENT,
        RETURN_VALUE
    }

    public enum CallbackKind {
        NONE,
        /** Flow into a functional argument's parameter, e.g. {@code Argument[0].Parameter[1]}. */
        PARAMETER,
        /** Flow out of a functional argument's return value, e.g. {@code Argument[1].ReturnValue}. */
        RETURN_VALUE
    }

    Root root;

    /** The argument range when {@link #root} is {@link Root#ARGUMENT}; otherwise {@code null}. */
    @Nullable
    ArgumentRange rootRange;

    CallbackKind callbackKind;

    /** The parameter range when {@link #callbackKind} is {@link CallbackKind#PARAMETER}; otherwise {@code null}. */
    @Nullable
    ArgumentRange callbackRange;

    public boolean isCallback() {
        return callbackKind != CallbackKind.NONE;
    }

    public static Optional<AccessPath> parse(String path) {
        List<String> components = tokenize(path);
        if (components.isEmpty()) {
            return Optional.empty();
        }

        String head = components.get(0);
        Root root;
        ArgumentRange rootRange = null;
        if ("ReturnValue".equals(head)) {
            root = Root.RETURN_VALUE;
        } else if (head.startsWith("Argument[")) {
            Optional<ArgumentRange> range = parseRange(head, "Argument");
            if (!range.isPresent()) {
                return Optional.empty();
            }
            root = Root.ARGUMENT;
            rootRange = range.get();
        } else {
            return Optional.empty();
        }

        CallbackKind callbackKind = CallbackKind.NONE;
        ArgumentRange callbackRange = null;
        for (int i = 1; i < components.size() && callbackKind == CallbackKind.NONE; i++) {
            String component = components.get(i);
            if (isContent(component)) {
                // Collapsed onto the container; ignore.
                continue;
            }
            if (root == Root.ARGUMENT && "ReturnValue".equals(component)) {
                callbackKind = CallbackKind.RETURN_VALUE;
            } else if (root == Root.ARGUMENT && component.startsWith("Parameter[")) {
                Optional<ArgumentRange> range = parseRange(component, "Parameter");
                if (!range.isPresent()) {
                    return Optional.empty();
                }
                callbackKind = CallbackKind.PARAMETER;
                callbackRange = range.get();
            } else {
                // An access-path component we don't model; treat the whole path as unparseable so it
                // is conservatively ignored rather than silently misinterpreted.
                return Optional.empty();
            }
        }

        return Optional.of(new AccessPath(root, rootRange, callbackKind, callbackRange));
    }

    private static boolean isContent(String component) {
        return "Element".equals(component) ||
               "ArrayElement".equals(component) ||
               "MapKey".equals(component) ||
               "MapValue".equals(component) ||
               component.startsWith("Field[") ||
               component.startsWith("SyntheticField[");
    }

    private static Optional<ArgumentRange> parseRange(String component, String prefix) {
        // component looks like "<prefix>[x]" or "<prefix>[x..y]"
        if (!component.startsWith(prefix + "[") || !component.endsWith("]")) {
            return Optional.empty();
        }
        return GenericExternalModel.computeArgumentRange("Argument" + component.substring(prefix.length()));
    }

    /** Splits an access path on top-level {@code .} separators, respecting {@code [...]} brackets. */
    private static List<String> tokenize(String path) {
        List<String> components = new ArrayList<>();
        if (path.isEmpty()) {
            return components;
        }
        int depth = 0;
        int start = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '.' && depth == 0) {
                components.add(path.substring(start, i));
                start = i + 1;
            }
        }
        components.add(path.substring(start));
        return components;
    }
}
