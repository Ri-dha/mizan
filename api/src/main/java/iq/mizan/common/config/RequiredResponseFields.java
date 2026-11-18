package iq.mizan.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;

import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Marks every component of a response record required in the OpenAPI document unless it is
 * {@code @Nullable}, so generated clients see the fields the server always sends as present
 * (ADR api-1). Request records keep their bean-validation rules instead.
 */
@Component
class RequiredResponseFields implements ModelConverter {

    private static final String RESPONSE_SUFFIX = "Response";
    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema<?> schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        Class<?> raw = type.getType() == null ? null : Json.mapper().constructType(type.getType()).getRawClass();
        if (schema == null || raw == null || !isResponseRecord(raw)) {
            return schema;
        }
        Schema<?> model = definition(schema, context);
        if (model != null && model.getProperties() != null) {
            markRequired(model, raw.getRecordComponents());
        }
        return schema;
    }

    private static boolean isResponseRecord(Class<?> type) {
        if (!type.isRecord()) {
            return false;
        }
        Class<?> enclosing = type.getEnclosingClass();
        return type.getSimpleName().endsWith(RESPONSE_SUFFIX)
                || (enclosing != null && enclosing.getSimpleName().endsWith(RESPONSE_SUFFIX));
    }

    private static @Nullable Schema<?> definition(Schema<?> schema, ModelConverterContext context) {
        String ref = schema.get$ref();
        if (ref == null) {
            return schema;
        }
        return context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static void markRequired(Schema<?> model, RecordComponent[] components) {
        List<String> required = model.getRequired() == null ? List.of() : model.getRequired();
        for (RecordComponent component : components) {
            String name = component.getName();
            if (model.getProperties().containsKey(name) && !required.contains(name) && !isNullable(component)) {
                model.addRequiredItem(name);
            }
        }
    }

    private static boolean isNullable(RecordComponent component) {
        return component.getAnnotatedType().isAnnotationPresent(Nullable.class)
                || component.isAnnotationPresent(Nullable.class);
    }
}
