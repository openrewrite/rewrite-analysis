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
package org.openrewrite.analysis.trait.util;

import fj.Semigroup;
import fj.data.Validation;
import org.openrewrite.Cursor;

import javax.annotation.concurrent.Immutable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Immutable
public final class TraitErrors implements Iterable<TraitError> {
    private final List<TraitError> errors;

    private TraitErrors(List<TraitError> errors) {
        // Defensive copy
        this.errors =
                unmodifiableList(new ArrayList<>(Objects.requireNonNull(errors, "errors cannot be null")));
    }

    @Override
    public Iterator<TraitError> iterator() {
        return errors.iterator();
    }

    public <V> V doThrow() {
        throw new TraitErrorsException(this);
    }

    @Override
    public String toString() {
        return "TraitErrors: " + errors.stream()
                .map(TraitError::getError)
                .collect(joining("\n\t- ", "\n\t- ", ""));
    }

    public static TraitErrors fromSingle(TraitError error) {
        return new TraitErrors(singletonList(error));
    }

    public static TraitErrors fromSingleError(String error) {
        return fromSingle(new TraitError(error));
    }

    public static <V> Validation<TraitErrors, V> invalidTraitCreationError(String error) {
        return Validation.fail(TraitErrors.fromSingleError(error));
    }

    public static <V extends U, U> Validation<TraitErrors, V> invalidTraitCreationType(Class<U> traitType, Cursor cursor, Class<?> expectedType) {
        return Validation.fail(TraitErrors.fromSingleError(
                traitType.getSimpleName() + " must be created from " + expectedType + " but was " + cursor.getValue().getClass()
        ));
    }

    public static <V extends U, U> Validation<TraitErrors, V> invalidTraitCreationType(Class<U> traitType, Cursor cursor, Class<?> expectedTypeFirst, Class<?> expectedTypeSecond) {
        return Validation.fail(TraitErrors.fromSingleError(
                traitType.getSimpleName() + " must be created from " + expectedTypeFirst + " or " + expectedTypeSecond + " but was " + cursor.getValue().getClass()
        ));
    }

    public static <V extends U, U> Validation<TraitErrors, V> invalidTraitCreation(Class<U> traitType, String error) {
        return Validation.fail(TraitErrors.fromSingleError(
                traitType.getSimpleName() + " could not be created: " + error
        ));
    }

    public static Semigroup<TraitErrors> semigroup = Semigroup.semigroupDef((a, b) -> new TraitErrors(
            Stream.concat(a.errors.stream(), b.errors.stream()).collect(toList())
    ));
}
