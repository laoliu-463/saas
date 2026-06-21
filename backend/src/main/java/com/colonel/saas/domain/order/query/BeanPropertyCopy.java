package com.colonel.saas.domain.order.query;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

final class BeanPropertyCopy {

    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            int.class, Integer.class,
            long.class, Long.class,
            float.class, Float.class,
            double.class, Double.class,
            char.class, Character.class
    );

    private BeanPropertyCopy() {
    }

    static void copy(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        try {
            Map<String, PropertyDescriptor> sourceProperties = readableProperties(source.getClass());
            for (PropertyDescriptor targetProperty : Introspector.getBeanInfo(target.getClass(), Object.class)
                    .getPropertyDescriptors()) {
                Method writeMethod = targetProperty.getWriteMethod();
                if (writeMethod == null) {
                    continue;
                }
                PropertyDescriptor sourceProperty = sourceProperties.get(targetProperty.getName());
                if (sourceProperty == null || sourceProperty.getReadMethod() == null) {
                    continue;
                }
                if (!isAssignable(targetProperty.getPropertyType(), sourceProperty.getPropertyType())) {
                    continue;
                }
                Object value = sourceProperty.getReadMethod().invoke(source);
                writeMethod.invoke(target, value);
            }
        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("Failed to copy bean properties", ex);
        }
    }

    private static Map<String, PropertyDescriptor> readableProperties(Class<?> type) throws IntrospectionException {
        Map<String, PropertyDescriptor> properties = new HashMap<>();
        for (PropertyDescriptor property : Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors()) {
            if (property.getReadMethod() != null) {
                properties.put(property.getName(), property);
            }
        }
        return properties;
    }

    private static boolean isAssignable(Class<?> targetType, Class<?> sourceType) {
        if (targetType.isAssignableFrom(sourceType)) {
            return true;
        }
        Class<?> wrappedTarget = PRIMITIVE_WRAPPERS.get(targetType);
        return wrappedTarget != null && wrappedTarget.equals(sourceType);
    }
}
