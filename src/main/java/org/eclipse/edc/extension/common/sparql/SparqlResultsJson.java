package org.eclipse.edc.extension.common.sparql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads SPARQL 1.1 Query Results JSON (https://www.w3.org/TR/sparql11-results-json/) with Jackson.
 *
 * The values come back unescaped (quotes, backslashes, line breaks), whatever the spacing of the
 * JSON. The previous text search looked for "value":" without a space, while Jena writes
 * "value": " (FN-16), and it cut each value at its first escaped quote.
 */
public class SparqlResultsJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String firstBindingValue(String sparqlJson, String varName) {
        var all = allBindingValues(sparqlJson, varName);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The value of {@code varName} in every row of results.bindings; rows without it are skipped. */
    public static List<String> allBindingValues(String sparqlJson, String varName) {
        var out = new ArrayList<String>();
        if (sparqlJson == null || sparqlJson.isBlank()) {
            return out;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(sparqlJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not a SPARQL JSON results document", e);
        }
        for (JsonNode binding : root.path("results").path("bindings")) {
            JsonNode value = binding.path(varName).path("value");
            if (value.isValueNode()) {
                out.add(value.asText());
            }
        }
        return out;
    }
}
