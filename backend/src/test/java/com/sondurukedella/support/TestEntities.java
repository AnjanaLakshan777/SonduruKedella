package com.sondurukedella.support;

import java.util.Map;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds JPA entities for unit tests, including fields that only the database or JPA normally set. */
public final class TestEntities {

    private TestEntities() {
    }

    public static <T> T create(Class<T> type, Map<String, Object> fields) {
        T entity = BeanUtils.instantiateClass(type);
        fields.forEach((name, value) -> ReflectionTestUtils.setField(entity, name, value));
        return entity;
    }
}
