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

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Top is the root of the trait type hierarchy; it defines some default
 * methods for equality.
 */
public interface Top {
    /**
     * @return An identifier that can be used to uniquely identify this element. Used for equality checking.
     */
    UUID getId();

    static boolean equals(Top e1, @Nullable Object other) {
        if (e1 == other) {
            return true;
        }
        if (other == null || e1.getClass() != other.getClass()) {
            return false;
        }
        Top e2 = (Top) other;
        return e1.getId().equals(e2.getId());
    }

    static int hashCode(Top e) {
        return 41 * e.getId().hashCode();
    }

    /**
     * A base class for implementing the {@link Top} interface that
     * provides default implementations for {@link #equals(Object)}
     * and {@link #hashCode()}.
     */
    abstract class Base implements Top {
        @Override
        public int hashCode() {
            return Top.hashCode(this);
        }

        @Override
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        public boolean equals(Object obj) {
            return Top.equals(this, obj);
        }
    }
}
