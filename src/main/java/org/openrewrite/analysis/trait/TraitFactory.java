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
package org.openrewrite.analysis.trait;

import fj.data.NonEmptyList;
import fj.data.Option;
import fj.data.Validation;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.util.TraitErrors;

import java.util.Iterator;

public interface TraitFactory<T extends Top> {
    Validation<TraitErrors, T> viewOf(Cursor cursor);

    default Validation<TraitErrors, T> firstEnclosingViewOf(Cursor cursor) {
        Iterator<Cursor> cursors = cursor.getPathAsCursors();
        TraitErrors errors = null;
        while (cursors.hasNext()) {
            Cursor c = cursors.next();
            Validation<TraitErrors, T> view = viewOf(c);
            if (view.isSuccess()) {
                return view;
            }
            if (errors == null) {
                errors = view.fail();
            } else {
                errors = TraitErrors.semigroup.sum(errors, view.fail());
            }
        }
        if (errors != null) {
            return Validation.fail(errors);
        }
        return Validation.fail(
                TraitErrors.fromSingleError(
                        "No view found for cursor " + cursor
                )
        );
    }

    /**
     * Find the first view of a given type that matches a cursor.
     *
     * @param cursor    The cursor to create a trait view of.
     * @param factory   The first factory to try to create a view with.
     * @param factories The remaining factories to try to create a view with.
     * @param <T>       The type of trait to find a view of.
     * @return A validation of either the first view of the given type, or a list of errors.
     */
    @SafeVarargs
    static <T extends Top> Validation<TraitErrors, T> findFirstViewOf(
            Cursor cursor,
            TraitFactory<? extends T> factory,
            TraitFactory<? extends T>... factories
    ) {
        return findFirstViewOf(
                cursor,
                NonEmptyList.nel(factory, factories)
        );
    }

    /**
     * Find the first view of a given type that matches a cursor.
     *
     * @param cursor    The cursor to create a trait view of.
     * @param factories The factories to try to create a view with.
     * @param <T>       The type of trait to find a view of.
     * @return A validation of either the first view of the given type, or a list of errors.
     */
    static <T extends Top> Validation<TraitErrors, T> findFirstViewOf(
            Cursor cursor,
            NonEmptyList<TraitFactory<? extends T>> factories
    ) {
        Validation<TraitErrors, T> view = factories.head().viewOf(cursor).map(t -> t);
        Option<NonEmptyList<TraitFactory<? extends T>>> remainder =
                NonEmptyList.fromList(
                        factories.tail()
                );
        if (remainder.isNone()) {
            return view;
        }
        return view.f().bind(fail -> {
            Validation<TraitErrors, T> next = findFirstViewOf(
                    cursor,
                    remainder.some()
            );
            return next.f().bind(nextFail -> Validation.fail(TraitErrors.semigroup.sum(
                    fail,
                    nextFail
            )));
        });
    }
}
