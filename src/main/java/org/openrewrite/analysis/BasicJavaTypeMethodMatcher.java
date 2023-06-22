package org.openrewrite.analysis;

import io.micrometer.core.lang.Nullable;
import lombok.NoArgsConstructor;
import org.openrewrite.java.tree.JavaType;

import java.util.List;

public interface BasicJavaTypeMethodMatcher extends JavaTypeMethodMatcher {

    @Override
    default boolean matches(@Nullable JavaType.Method type) {
        if (type == null) {
            return false;
        }
        if (!matchesTargetType(type.getDeclaringType())) {
            return false;
        }

        if (!matchesMethodName(type.getName())) {
            return false;
        }

        return matchesParameterTypes(type.getParameterTypes());
    }


    /**
     * @param type The declaring type of the method invocation or constructor.
     * @return True if the declaring type matches the criteria of this matcher.
     * @implNote {@link #isMatchOverrides()} will be used to determine if parent types should also be checked
     */
    default boolean matchesTargetType(@Nullable JavaType.FullyQualified type) {
        if (type == null || type instanceof JavaType.Unknown) {
            return false;
        }

        if (matchesTargetTypeName(type.getFullyQualifiedName())) {
            return true;
        }

        if (isMatchOverrides()) {
            if (!"java.lang.Object".equals(type.getFullyQualifiedName()) &&
                    matchesTargetType(SimpleMethodMatcherHolder.OBJECT_CLASS)) {
                return true;
            }

            if (matchesTargetType(type.getSupertype())) {
                return true;
            }

            for (JavaType.FullyQualified anInterface : type.getInterfaces()) {
                if (matchesTargetType(anInterface)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return True if this method matcher should match on overrides of the target type.
     * @implNote This will be used to determine if the target type should be checked against the supertypes
     * passing super types to {@link #matchesTargetTypeName(String)}.
     * @implSpec MUST return a constant value for all invocations.
     */
    boolean isMatchOverrides();

    boolean matchesTargetTypeName(String fullyQualifiedTypeName);

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean matchesMethodName(String methodName);

    boolean matchesParameterTypes(List<JavaType> parameterTypes);

}

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
final class SimpleMethodMatcherHolder {
    static final JavaType.ShallowClass OBJECT_CLASS =
            JavaType.ShallowClass.build("java.lang.Object");
}
