package gg.lightpractice.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small reflection toolkit used by the optional dependency bridges.
 *
 * <p>FastAsyncWorldEdit/WorldEdit, ProtocolLib, Citizens and Vault are all optional on a 1.8.9
 * server and their jars are frequently not resolvable at build time (for example ProtocolLib 4.x is
 * no longer published to any reachable repository). Rather than failing the build or hard depending
 * on a version that may not match the server, those integrations resolve their types through this
 * helper at runtime and degrade gracefully when a call cannot be made.</p>
 */
public final class Reflect {

    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<String, Method>();
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<String, Class<?>>();
    private static final Class<?> MISSING = Reflect.class;

    private Reflect() {
    }

    public static Class<?> findClass(String name) {
        Class<?> cached = CLASS_CACHE.get(name);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }
        Class<?> resolved;
        try {
            resolved = Class.forName(name, false, Reflect.class.getClassLoader());
        } catch (Throwable throwable) {
            resolved = null;
        }
        CLASS_CACHE.put(name, resolved == null ? MISSING : resolved);
        return resolved;
    }

    public static boolean isAvailable(String className) {
        return findClass(className) != null;
    }

    /** Looks a method up by exact signature, returning {@code null} when it does not exist. */
    public static Method method(Class<?> type, String name, Class<?>... parameters) {
        if (type == null) {
            return null;
        }
        String key = type.getName() + '#' + name + signatureKey(parameters);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> walker = type;
        while (walker != null && walker != Object.class) {
            try {
                Method method = walker.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                METHOD_CACHE.put(key, method);
                return method;
            } catch (NoSuchMethodException ignored) {
                walker = walker.getSuperclass();
            }
        }
        return null;
    }

    /** Looks a method up by name and parameter count, used when signatures differ between builds. */
    public static Method methodByName(Class<?> type, String name, int parameterCount) {
        if (type == null) {
            return null;
        }
        String key = type.getName() + '#' + name + "/" + parameterCount;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> walker = type;
        while (walker != null && walker != Object.class) {
            for (Method candidate : walker.getDeclaredMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterTypes().length == parameterCount) {
                    candidate.setAccessible(true);
                    METHOD_CACHE.put(key, candidate);
                    return candidate;
                }
            }
            walker = walker.getSuperclass();
        }
        return null;
    }

    public static Constructor<?> constructor(Class<?> type, Class<?>... parameters) {
        if (type == null) {
            return null;
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameters);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    public static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> walker = target.getClass();
        while (walker != null && walker != Object.class) {
            try {
                Field field = walker.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                walker = walker.getSuperclass();
            } catch (IllegalAccessException exception) {
                return null;
            }
        }
        return null;
    }

    /** Invokes a method, returning {@code null} when the method is missing or the call fails. */
    public static Object call(Object target, String name, Object... arguments) {
        if (target == null) {
            return null;
        }
        Class<?>[] parameters = parameterTypes(arguments);
        Method method = method(target.getClass(), name, parameters);
        if (method == null) {
            method = methodByName(target.getClass(), name, arguments.length);
        }
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.INTEGRATION, "Reflective call {}#{} failed: {}",
                    target.getClass().getSimpleName(), name, throwable.getMessage());
            return null;
        }
    }

    /** Invokes a static method, returning {@code null} when the call fails. */
    public static Object callStatic(Class<?> type, String name, Object... arguments) {
        if (type == null) {
            return null;
        }
        Class<?>[] parameters = parameterTypes(arguments);
        Method method = method(type, name, parameters);
        if (method == null) {
            method = methodByName(type, name, arguments.length);
        }
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, arguments);
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.INTEGRATION, "Reflective static call {}#{} failed: {}",
                    type.getName(), name, throwable.getMessage());
            return null;
        }
    }

    public static Object newInstance(Class<?> type, Object... arguments) {
        if (type == null) {
            return null;
        }
        Constructor<?> constructor = constructor(type, parameterTypes(arguments));
        if (constructor == null) {
            for (Constructor<?> candidate : type.getDeclaredConstructors()) {
                if (candidate.getParameterTypes().length == arguments.length) {
                    constructor = candidate;
                    break;
                }
            }
        }
        if (constructor == null) {
            return null;
        }
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.INTEGRATION, "Reflective construction of {} failed: {}",
                    type.getName(), throwable.getMessage());
            return null;
        }
    }

    public static Enum<?> enumValue(Class<?> enumType, String constant) {
        if (enumType == null || !enumType.isEnum()) {
            return null;
        }
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Enum<?> value = Enum.valueOf((Class) enumType, constant);
            return value;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Invokes a method and propagates failures, used by bridges that have to report an honest
     * success or failure to their caller instead of silently returning {@code null}.
     */
    public static Object invoke(Object target, String name, Object... arguments) throws Exception {
        if (target == null) {
            throw new IllegalArgumentException("target is null");
        }
        Class<?>[] parameters = parameterTypes(arguments);
        Method method = method(target.getClass(), name, parameters);
        if (method == null) {
            method = methodByName(target.getClass(), name, arguments.length);
        }
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        }
        try {
            return method.invoke(target, arguments);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw exception;
        }
    }

    /** Invokes a static method and propagates failures. */
    public static Object invokeStatic(Class<?> type, String name, Object... arguments) throws Exception {
        if (type == null) {
            throw new IllegalArgumentException("type is null");
        }
        Class<?>[] parameters = parameterTypes(arguments);
        Method method = method(type, name, parameters);
        if (method == null) {
            method = methodByName(type, name, arguments.length);
        }
        if (method == null) {
            throw new NoSuchMethodException(type.getName() + "#" + name);
        }
        try {
            return method.invoke(null, arguments);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw exception;
        }
    }

    /** Reads a static field, returning {@code null} when it is missing. */
    public static Object staticField(Class<?> type, String name) {
        if (type == null) {
            return null;
        }
        Class<?> walker = type;
        while (walker != null && walker != Object.class) {
            try {
                Field field = walker.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
                walker = walker.getSuperclass();
            } catch (IllegalAccessException exception) {
                return null;
            }
        }
        return null;
    }

    /** Enumerates the static fields of a type, used to discover available formats. */
    public static List<String> staticFieldNames(Class<?> type) {
        List<String> names = new ArrayList<String>();
        if (type == null) {
            return names;
        }
        for (Field field : type.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private static Class<?>[] parameterTypes(Object[] arguments) {
        if (arguments == null) {
            return new Class<?>[0];
        }
        Class<?>[] types = new Class<?>[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            types[index] = arguments[index] == null ? Object.class : primitiveOf(arguments[index].getClass());
        }
        return types;
    }

    private static Class<?> primitiveOf(Class<?> type) {
        if (type == Integer.class) {
            return Integer.TYPE;
        }
        if (type == Long.class) {
            return Long.TYPE;
        }
        if (type == Double.class) {
            return Double.TYPE;
        }
        if (type == Float.class) {
            return Float.TYPE;
        }
        if (type == Boolean.class) {
            return Boolean.TYPE;
        }
        if (type == Short.class) {
            return Short.TYPE;
        }
        if (type == Byte.class) {
            return Byte.TYPE;
        }
        if (type == Character.class) {
            return Character.TYPE;
        }
        return type;
    }

    private static String signatureKey(Class<?>[] parameters) {
        StringBuilder builder = new StringBuilder("(");
        if (parameters != null) {
            for (Class<?> parameter : parameters) {
                builder.append(parameter == null ? "null" : parameter.getName()).append(',');
            }
        }
        return builder.append(')').toString();
    }

    /**
     * Attempts an exact signature lookup first and falls back to a name based lookup. Arguments that
     * are primitives are matched against their wrapper types too, which keeps bridge code readable.
     */
    public static Method resolve(Class<?> type, String name, Class<?>[] declared, Object[] arguments) {
        Method method = method(type, name, declared);
        if (method != null) {
            return method;
        }
        return methodByName(type, name, arguments == null ? 0 : arguments.length);
    }
}
