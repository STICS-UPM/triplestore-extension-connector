package org.eclipse.edc.extension.common.sparql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.sparql.resultset.ResultsFormat;
import org.eclipse.edc.spi.monitor.Monitor;
import sparql.streamline.core.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import java.util.stream.StreamSupport;


public class StreamlineSparqlClient implements ISparqlClient {

    private final Monitor monitor;
    private final SparqlEndpoint endpoint;
    private final ObjectMapper mapper = new ObjectMapper();
    private Boolean details = false;

    public StreamlineSparqlClient(SparqlEndpointConfiguration configuration, Monitor monitor) {
        this.monitor =  monitor;
        this.endpoint = new SparqlEndpoint(configuration);
    }

    // TODO: Remove this method and monitor for production
    public void setDetails(Boolean details) {
        this.details = details;
    }

    @Override
    public String sparqlSelect(String sparqlQuery) {
        if (details) monitor.info("[StreamlineSparqlClient] Executing SPARQL SELECT query: " + sparqlQuery);

        try{
            ByteArrayOutputStream stream  = endpoint.query(sparqlQuery, ResultsFormat.FMT_RS_JSON);
            String queryResults = stream.toString(StandardCharsets.UTF_8);
            stream.close();
            if (details) monitor.info("[StreamlineSparqlClient] SPARQL SELECT results: " + queryResults);
            return queryResults;
        }catch(Exception e) {
            if (details) monitor.severe("[StreamlineSparqlClient] Error executing SPARQL SELECT query: " + sparqlQuery, e);
            throw new RuntimeException("SPARQL endpoint error: HTTP ");
        }

    }

    @Override
    public void sparqlUpdate(String sparqlUpdate) {
        if (details) monitor.info("[StreamlineSparqlClient] Executing SPARQL UPDATE query: " + sparqlUpdate);
        try {
            endpoint.update(sparqlUpdate);
        }catch(Exception e) {
            if (details) monitor.severe("[StreamlineSparqlClient] Error executing SPARQL UPDATE query: " + sparqlUpdate, e);
            throw new RuntimeException("SPARQL endpoint error: HTTP ");
        }
    }


    /* ============================
       Literal helpers
       ============================ */

    /** "foo" → "foo" */
    public String literal(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + escape(value) + "\"";
    }

    /** Long JSON or text literal */
    public String literalLongString(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + escape(value) + "\"";
    }

    /** Integer / long literal */
    public String literal(long value) {
        return Long.toString(value);
    }

    /** Boolean literal */
    public String literal(boolean value) {
        return value ? "true" : "false";
    }

    /** xsd:dateTime literal */
    public String literalDateTime(Instant instant) {
        var lex = DateTimeFormatter.ISO_INSTANT.format(instant);
        return "\"" + lex + "\"^^<http://www.w3.org/2001/XMLSchema#dateTime>";
    }

    /* ============================
       Escaping
       ============================ */

    private static String escape(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Executes a SELECT and returns all values of a column as a Stream.
     */
    public Stream<String> selectColumnAsStream(String query, String column) {
        try {
            var json = sparqlSelect(query);
            var root = mapper.readTree(json);
            var bindings = root.path("results").path("bindings");

            return StreamSupport.stream(bindings.spliterator(), false)
                    .map(b -> b.path(column).path("value").asText())
                    .filter(v -> !v.isEmpty());
        } catch (Exception e) {
            throw new RuntimeException("SPARQL selectColumnAsStream failed", e);
        }
    }

    /**
     * Executes a SELECT and returns the first literal value of a column, or null.
     */
    public String selectSingleLiteral(String query, String column) {
        try {
            var json = sparqlSelect(query);
            var root = mapper.readTree(json);
            var bindings = root.path("results").path("bindings");

            if (!bindings.isArray() || bindings.isEmpty()) {
                return null;
            }

            return bindings.get(0).path(column).path("value").asText(null);
        } catch (Exception e) {
            throw new RuntimeException("SPARQL selectSingleLiteral failed", e);
        }
    }
}

