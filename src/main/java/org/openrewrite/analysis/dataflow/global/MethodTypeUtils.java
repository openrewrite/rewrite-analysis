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
package org.openrewrite.analysis.dataflow.global;

import lombok.NoArgsConstructor;
import org.openrewrite.java.tree.JavaType;

import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
final class MethodTypeUtils {
    static JavaType.Method getDeclarationMethod(JavaType.Method method) {
        // Look into the method's declaring class to check and see if there is an equivalent generic method declaration
        for (JavaType.Method declaredMethod : method.getDeclaringType().getMethods()) {
            // If we find an exact match, return that immediately.
            if (method.equals(declaredMethod)) {
                return declaredMethod;
            }

            // Compare components that will not be different between the two methods, regardless of generics
            if (!declaredMethod.getName().equals(method.getName()) ||
                declaredMethod.getParameterTypes().size() != method.getParameterTypes().size() ||
                !declaredMethod.getFlags().equals(method.getFlags())) {
                continue;
            }
            // Try to convert the declared generic method
            JavaTypeGenericTypeSolver solver = new JavaTypeGenericTypeSolver();

            // Solve for the return value, in case that's a generic type
            JavaType solvedReturnType = solver.solve(method.getReturnType(), declaredMethod.getReturnType());
            if (!solvedReturnType.equals(method.getReturnType())) {
                continue;
            }
            // Now solve for each parameter
            fj.data.List<JavaType> declaredParameters = fj.data.List.iterableList(declaredMethod.getParameterTypes());
            fj.data.List<JavaType> parameters = fj.data.List.iterableList(method.getParameterTypes());
            fj.data.List<JavaType> declaredParametersSolved = parameters.zipWith(declaredParameters, solver::solve);
            // If the solved parameters are the same as the method's parameters, then we have a match
            if (declaredParametersSolved.toJavaList().equals(method.getParameterTypes())) {
                return declaredMethod;
            }
            // Otherwise, keep looking
        }
        // If we can't find a match, just return the original method
        return method;
    }

    static class JavaTypeGenericTypeSolver {
        private final Map<JavaType.GenericTypeVariable, JavaType> typeVariableMap = new HashMap<>();

        JavaType solve(JavaType accessType, JavaType declaredType) {
            if (declaredType instanceof JavaType.GenericTypeVariable) {
                // If we've already solved for this type variable, return the solved type
                return typeVariableMap.computeIfAbsent((JavaType.GenericTypeVariable) declaredType, __ -> accessType);
            }
            // If this is not a GenericTypeVariable, then we can just return the declared type
            return declaredType;
        }
    }
}
