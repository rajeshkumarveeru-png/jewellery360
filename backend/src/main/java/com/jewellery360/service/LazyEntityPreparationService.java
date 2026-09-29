package com.jewellery360.service;

import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Initializes Hibernate associations that are reachable from a REST response
 * while the persistence context is definitely open. This prevents lazy proxy
 * failures during Jackson serialization without changing entity mappings to EAGER.
 */
@Service
public class LazyEntityPreparationService {

    private static final int MAX_DEPTH = 6;

    @Transactional(readOnly = true)
    public void prepare(Object body) {
        Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        prepareValue(body, visited, 0);
    }

    private void prepareValue(Object value, Set<Object> visited, int depth) {
        if (value == null || depth > MAX_DEPTH || isSimple(value.getClass())) return;

        if (value instanceof Collection<?> collection) {
            for (Object item : collection) prepareValue(item, visited, depth + 1);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) prepareValue(item, visited, depth + 1);
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) prepareValue(Array.get(value, i), visited, depth + 1);
            return;
        }
        if (!visited.add(value)) return;

        Class<?> type = Hibernate.getClass(value);
        for (Field field : allFields(type)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;

            boolean association = field.isAnnotationPresent(ManyToOne.class)
                    || field.isAnnotationPresent(OneToOne.class)
                    || field.isAnnotationPresent(OneToMany.class)
                    || field.isAnnotationPresent(ManyToMany.class);
            if (!association) continue;

            try {
                field.setAccessible(true);
                Object associationValue = field.get(value);
                if (associationValue != null) {
                    Hibernate.initialize(associationValue);
                    prepareValue(associationValue, visited, depth + 1);
                }
            } catch (RuntimeException | IllegalAccessException ignored) {
                // The response serializer will handle nullable/unavailable associations.
                // Do not break an otherwise valid API response because an optional relation
                // could not be initialized.
            }
        }
    }

    private static Field[] allFields(Class<?> type) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            java.util.Collections.addAll(fields, current.getDeclaredFields());
        }
        return fields.toArray(Field[]::new);
    }

    private static boolean isSimple(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || java.time.temporal.Temporal.class.isAssignableFrom(type)
                || java.util.Date.class.isAssignableFrom(type)
                || java.time.Instant.class == type
                || java.time.LocalDate.class == type
                || java.time.LocalDateTime.class == type
                || java.time.OffsetDateTime.class == type
                || java.time.ZonedDateTime.class == type
                || java.math.BigDecimal.class == type
                || java.math.BigInteger.class == type
                || type.getName().startsWith("java.");
    }
}
