package net.earthcomputer.dekotlinify.surrogate;

import java.util.Objects;

@SuppressWarnings("unused")
public class IntrinsicsSurrogate {
    public static void noOp(Object object) {}
    public static void noOp(Object object, String string) {}
    public static void noOp(Object object, String string1, String string2) {}
    public static void noOp(Object object, String string1, String string2, String string3) {}

    public static void checkNotNullJava8(Object object) {
        //noinspection ResultOfMethodCallIgnored
        object.getClass();
    }

    public static void checkNotNullJava9(Object object) {
        Objects.requireNonNull(object);
    }

    public static void checkNotNullJava8(Object object, String message) {
        if (object == null) {
            throw new NullPointerException(message);
        }
    }

    public static void checkNotNullJava9(Object object, String message) {
        Objects.requireNonNull(object, message);
    }

    public static void throwNpe() {
        throw new NullPointerException();
    }

    public static void throwNpe(String message) {
        throw new NullPointerException(message);
    }

    public static void throwJavaNpe() {
        throwNpe();
    }

    public static void throwJavaNpe(String message) {
        throwNpe(message);
    }

    public static void throwUninitializedProperty(String message) {
        throw new IllegalStateException("Uninitialized property: " + message);
    }

    public static void throwUninitializedPropertyAccessException(String propertyName) {
        throwUninitializedProperty(propertyName);
    }

    public static void throwAssert() {
        throw new AssertionError();
    }

    public static void throwAssert(String message) {
        throw new AssertionError(message);
    }

    public static void throwIllegalArgument() {
        throw new IllegalArgumentException();
    }

    public static void throwIllegalArgument(String message) {
        throw new IllegalArgumentException(message);
    }

    public static void throwIllegalState() {
        throw new IllegalStateException();
    }

    public static void throwIllegalState(String message) {
        throw new IllegalStateException(message);
    }

    public static void checkExpressionValueIsNotNull(Object value, String expression) {
        if (value == null) {
            throw new IllegalStateException(expression + " must not be null");
        }
    }

    public static void checkNotNullExpressionValue(Object value, String expression) {
        if (value == null) {
            throw new NullPointerException(expression + " must not be null");
        }
    }

    public static void checkReturnedValueIsNotNull(Object value, String className, String methodName) {
        if (value == null) {
            throw new IllegalStateException("Method specified as non-null returned null: " + className + "." + methodName);
        }
    }

    public static void checkReturnedValueIsNotNull(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }

    public static void checkFieldIsNotNull(Object value, String className, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Field specified as non-null is null: " + className + "." + fieldName);
        }
    }

    public static void checkFieldIsNotNull(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }

    public static void checkParameterIsNotNull(Object value, String paramName, String className, String methodName) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter specified as non-null is null: method " + className + "." + methodName + ", parameter " + paramName);
        }
    }

    public static void checkNotNullParameter(Object value, String paramName, String className, String methodName) {
        if (value == null) {
            throw new NullPointerException("Parameter specified as non-null is null: method " + className + "." + methodName + ", parameter " + paramName);
        }
    }

    public static void throwUndefinedForReified() {
        throwUndefinedForReified("This function has a reified type parameter and thus can only be inlined at compilation time, not called directly.");
    }

    public static void throwUndefinedForReified(String message) {
        throw new UnsupportedOperationException(message);
    }

    public static void reifiedOperationMarker(int id, String typeParameterIdentifier) {
        throwUndefinedForReified();
    }

    public static void reifiedOperationMarker(int id, String typeParameterIdentifier, String message) {
        throwUndefinedForReified(message);
    }

    public static void needClassReification() {
        throwUndefinedForReified();
    }

    public static void checkHasClass(String internalName) {
    }

    public static void checkHasClass(String internalName, String requiredVersion) {
    }
}
