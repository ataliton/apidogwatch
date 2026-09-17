package io.apidogwatch.comparator;

import com.fasterxml.jackson.databind.JsonNode;
import io.apidogwatch.model.Divergence;
import io.apidogwatch.model.DivergenceType;
import io.apidogwatch.util.JsonSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaComparatorTest {

    private final SchemaComparator comparator = new SchemaComparator();

    @Test
    void detectsExtraMissingAndTypeMismatch() throws Exception {
        String schemaJson = """
                {
                  "type": "object",
                  "required": ["id", "name"],
                  "properties": {
                    "id": { "type": "integer" },
                    "name": { "type": "string" },
                    "active": { "type": "boolean" }
                  }
                }
                """;
        String liveJson = """
                {
                  "id": "not-an-int",
                  "nickname": "dog"
                }
                """;

        JsonNode schema = JsonSupport.readTree(schemaJson);
        List<Divergence> divergences = comparator.compare(liveJson, schema);

        assertTrue(divergences.stream().anyMatch(d -> d.getType() == DivergenceType.TYPE_MISMATCH));
        assertTrue(divergences.stream().anyMatch(d -> d.getType() == DivergenceType.MISSING_FIELD));
        assertTrue(divergences.stream().anyMatch(d -> d.getType() == DivergenceType.EXTRA_FIELD));
        assertEquals(3, divergences.size());
    }
}
