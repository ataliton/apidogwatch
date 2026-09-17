package io.apidogwatch.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.apidogwatch.model.Divergence;
import io.apidogwatch.model.DivergenceType;
import io.apidogwatch.util.JsonSupport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight OpenAPI-schema vs live-JSON comparator.
 * <p>
 * Detects extra fields, missing fields and type mismatches. Supports a practical
 * subset of OpenAPI 3 schema keywords: {@code type}, {@code properties},
 * {@code required}, {@code items}, {@code additionalProperties}, {@code $ref}
 * (local only) and simple nullable flags.
 */
public final class SchemaComparator {

    public List<Divergence> compare(String liveJson, JsonNode schemaNode) {
        List<Divergence> divergences = new ArrayList<>();
        if (schemaNode == null || schemaNode.isMissingNode() || schemaNode.isNull()) {
            return divergences;
        }

        JsonNode liveNode;
        try {
            if (liveJson == null || liveJson.isBlank()) {
                divergences.add(new Divergence(
                        DivergenceType.MISSING_FIELD,
                        "$",
                        "Response body is empty but the contract declares a schema"
                ));
                return divergences;
            }
            liveNode = JsonSupport.readTree(liveJson);
        } catch (Exception ex) {
            divergences.add(new Divergence(
                    DivergenceType.INVALID_JSON,
                    "$",
                    "Response body is not valid JSON: " + ex.getMessage()
            ));
            return divergences;
        }

        walk("$", liveNode, schemaNode, schemaNode, divergences);
        return divergences;
    }

    private void walk(String path,
                      JsonNode live,
                      JsonNode schema,
                      JsonNode rootSchema,
                      List<Divergence> out) {
        schema = resolveRef(schema, rootSchema);
        if (schema == null || schema.isNull()) {
            return;
        }

        if (isNullable(schema) && (live == null || live.isNull())) {
            return;
        }

        String expectedType = textOrNull(schema.get("type"));
        if (expectedType != null && !matchesType(expectedType, live)) {
            out.add(new Divergence(
                    DivergenceType.TYPE_MISMATCH,
                    path,
                    "Type mismatch",
                    expectedType,
                    liveType(live)
            ));
            return;
        }

        if ("object".equals(expectedType) || schema.has("properties")) {
            compareObject(path, live, schema, rootSchema, out);
        } else if ("array".equals(expectedType) || schema.has("items")) {
            compareArray(path, live, schema, rootSchema, out);
        }
    }

    private void compareObject(String path,
                               JsonNode live,
                               JsonNode schema,
                               JsonNode rootSchema,
                               List<Divergence> out) {
        if (live == null || !live.isObject()) {
            out.add(new Divergence(
                    DivergenceType.TYPE_MISMATCH,
                    path,
                    "Expected object",
                    "object",
                    liveType(live)
            ));
            return;
        }

        JsonNode properties = schema.path("properties");
        JsonNode required = schema.path("required");
        boolean allowAdditional = schema.path("additionalProperties").asBoolean(true);

        if (required.isArray()) {
            for (JsonNode requiredName : required) {
                String field = requiredName.asText();
                if (!live.has(field) || live.get(field).isNull()) {
                    out.add(new Divergence(
                            DivergenceType.MISSING_FIELD,
                            join(path, field),
                            "Required field is missing",
                            field,
                            null
                    ));
                }
            }
        }

        if (properties.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                if (live.has(fieldName)) {
                    walk(join(path, fieldName), live.get(fieldName), entry.getValue(), rootSchema, out);
                }
            }
        }

        if (!allowAdditional && properties.isObject()) {
            Iterator<String> liveFields = live.fieldNames();
            while (liveFields.hasNext()) {
                String fieldName = liveFields.next();
                if (!properties.has(fieldName)) {
                    out.add(new Divergence(
                            DivergenceType.EXTRA_FIELD,
                            join(path, fieldName),
                            "Field is not declared in the contract",
                            null,
                            fieldName
                    ));
                }
            }
        } else if (properties.isObject()) {
            // Even when additionalProperties is allowed, surface extras as soft alerts
            // so developers can tighten the contract intentionally.
            Iterator<String> liveFields = live.fieldNames();
            while (liveFields.hasNext()) {
                String fieldName = liveFields.next();
                if (!properties.has(fieldName)) {
                    out.add(new Divergence(
                            DivergenceType.EXTRA_FIELD,
                            join(path, fieldName),
                            "Field present in payload but absent from schema properties",
                            null,
                            fieldName
                    ));
                }
            }
        }
    }

    private void compareArray(String path,
                              JsonNode live,
                              JsonNode schema,
                              JsonNode rootSchema,
                              List<Divergence> out) {
        if (live == null || !live.isArray()) {
            out.add(new Divergence(
                    DivergenceType.TYPE_MISMATCH,
                    path,
                    "Expected array",
                    "array",
                    liveType(live)
            ));
            return;
        }

        JsonNode items = schema.get("items");
        if (items == null || items.isMissingNode()) {
            return;
        }

        int index = 0;
        for (JsonNode element : live) {
            walk(path + "[" + index + "]", element, items, rootSchema, out);
            index++;
        }
    }

    private JsonNode resolveRef(JsonNode schema, JsonNode rootSchema) {
        if (schema == null || !schema.has("$ref")) {
            return schema;
        }
        String ref = schema.get("$ref").asText();
        if (!ref.startsWith("#/")) {
            return schema;
        }
        String[] parts = ref.substring(2).split("/");
        JsonNode current = rootSchema;
        // When schema is a response schema fragment, root may already be the schema itself.
        // Prefer walking from an enclosing document if components exist; otherwise no-op.
        JsonNode document = rootSchema.has("components") ? rootSchema : null;
        if (document != null) {
            current = document;
            for (String part : parts) {
                current = current.path(part);
            }
            return current.isMissingNode() ? schema : current;
        }
        return schema;
    }

    private boolean matchesType(String expected, JsonNode live) {
        if (live == null || live.isNull()) {
            return "null".equals(expected);
        }
        return switch (expected.toLowerCase(Locale.ROOT)) {
            case "object" -> live.isObject();
            case "array" -> live.isArray();
            case "string" -> live.isTextual();
            case "integer" -> live.isIntegralNumber();
            case "number" -> live.isNumber();
            case "boolean" -> live.isBoolean();
            case "null" -> live.isNull();
            default -> true;
        };
    }

    private boolean isNullable(JsonNode schema) {
        if (schema.has("nullable") && schema.get("nullable").asBoolean(false)) {
            return true;
        }
        JsonNode type = schema.get("type");
        if (type != null && type.isArray()) {
            for (JsonNode item : type) {
                if ("null".equals(item.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String liveType(JsonNode live) {
        if (live == null) {
            return "null";
        }
        JsonNodeType type = live.getNodeType();
        return switch (type) {
            case ARRAY -> "array";
            case OBJECT -> "object";
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case NUMBER -> live.isIntegralNumber() ? "integer" : "number";
            case NULL -> "null";
            default -> type.name().toLowerCase(Locale.ROOT);
        };
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray() && node.size() > 0) {
            return node.get(0).asText();
        }
        return node.asText();
    }

    private String join(String parent, String child) {
        if ("$".equals(parent)) {
            return "$." + child;
        }
        return parent + "." + child;
    }
}
