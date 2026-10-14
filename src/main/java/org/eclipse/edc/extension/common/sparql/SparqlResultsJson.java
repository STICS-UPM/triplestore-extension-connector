package org.eclipse.edc.extension.common.sparql;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal helper for SPARQL JSON results.
 *
 * Replace with proper JSON parsing if you prefer (e.g., Jackson).
 */
public class SparqlResultsJson {

    public static String firstBindingValue(String sparqlJson, String varName) {
        var all = allBindingValues(sparqlJson, varName);
        return all.isEmpty() ? null : all.get(0);
    }

    public static List<String> allBindingValues(String sparqlJson, String varName) {
        var out = new ArrayList<String>();
        if (sparqlJson == null) return out;

        // Find occurrences of: "<varName>":{"type":"...","value":"..."}
        var needle = "\"" + varName + "\":";
        int i = 0;
        while (true) {
            i = sparqlJson.indexOf(needle, i);
            if (i < 0) break;

            var valueKey = "\"value\":\"";
            var v = sparqlJson.indexOf(valueKey, i);
            if (v < 0) { i += needle.length(); continue; }

            var start = v + valueKey.length();
            var end = sparqlJson.indexOf("\"", start);
            if (end < 0) break;

            var raw = sparqlJson.substring(start, end);

            // Minimal unescape
            out.add(raw.replace("\\\"", "\"")
                       .replace("\\n", "\n")
                       .replace("\\r", "\r")
                       .replace("\\t", "\t")
                       .replace("\\\\", "\\"));

            i = end + 1;
        }
        return out;
    }
}
