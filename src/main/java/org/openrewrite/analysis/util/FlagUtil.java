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
package org.openrewrite.analysis.util;

import lombok.NoArgsConstructor;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;

import java.util.Collection;
import java.util.stream.Collectors;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class FlagUtil {
    public static Flag fromModifierType(J.Modifier.Type modifierType) {
        switch (modifierType) {
            case Default:
                return Flag.Default;
            case Public:
                return Flag.Public;
            case Protected:
                return Flag.Protected;
            case Private:
                return Flag.Private;
            case Abstract:
                return Flag.Abstract;
            case Static:
                return Flag.Static;
            case Final:
                return Flag.Final;
            case Transient:
                return Flag.Transient;
            case Volatile:
                return Flag.Volatile;
            case Synchronized:
                return Flag.Synchronized;
            case Native:
                return Flag.Native;
            case Strictfp:
                return Flag.Strictfp;
            default:
                return Flag.PotentiallyAmbiguous;
        }
    }

    public static Collection<Flag> fromModifiers(Collection<J.Modifier> modifiers) {
        return modifiers.stream().map(J.Modifier::getType).map(FlagUtil::fromModifierType).collect(Collectors.toSet());
    }
}
