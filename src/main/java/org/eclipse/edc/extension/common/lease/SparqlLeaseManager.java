package org.eclipse.edc.extension.common.lease;


import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.sparql.SparqlResultsJson;

/**
 * Generic leasing for SPARQL triple stores.
 *
 * Strategy:
 *  1) SPARQL UPDATE: pick up to N eligible entities and set lease fields + a leaseToken
 *  2) SPARQL SELECT: fetch ids of entities updated with that leaseToken
 */
public class SparqlLeaseManager {

    // use the same EDC namespace as your other stores
    public static final String EDC_NS = "https://w3id.org/edc/v0.0.1/ns/";
    private static final String XSD_DATE_TIME = "http://www.w3.org/2001/XMLSchema#dateTime";

    private final ISparqlClient sparql;
    private final Clock clock;

    public SparqlLeaseManager(ISparqlClient sparql, Clock clock) {
        this.sparql = sparql;
        this.clock = clock;
    }

    /**
     * Acquire up to {@code limit} leases for entities in {@code indexGraphIri} that match the given state and are not leased (or lease expired).
     *
     * @param indexGraphIri graph containing index triples (state, lease fields, id)
     * @param stateValue state value to match (string or int serialized as string)
     * @param limit max number of items
     * @param owner lease owner name (e.g. runtime id)
     * @param leaseDuration how long the lease is valid
     * @return list of edc:id values that were leased by this call
     */
    public List<String> acquireByState(String indexGraphIri, String stateValue, int limit, String owner, Duration leaseDuration) {
        var token = UUID.randomUUID().toString();
        var until = Instant.now(clock).plus(leaseDuration);
        var untilLex = DateTimeFormatter.ISO_INSTANT.format(until);

        // 1) Update: select eligible subjects and set lease fields + token.
        // We remove old lease fields if present, then insert new ones.
        var update =
                "PREFIX edc: <" + EDC_NS + ">\n" +
                        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                        "DELETE {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    ?s edc:leasedBy ?oldBy .\n" +
                        "    ?s edc:leaseUntil ?oldUntil .\n" +
                        "    ?s edc:leaseToken ?oldToken .\n" +
                        "  }\n" +
                        "}\n" +
                        "INSERT {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    ?s edc:leasedBy \"" + escape(owner) + "\" .\n" +
                        "    ?s edc:leaseUntil \"" + untilLex + "\"^^xsd:dateTime .\n" +
                        "    ?s edc:leaseToken \"" + token + "\" .\n" +
                        "  }\n" +
                        "}\n" +
                        "WHERE {\n" +
                        "  {\n" +
                        "    SELECT ?s WHERE {\n" +
                        "      GRAPH <" + indexGraphIri + "> {\n" +
                        "           ?s edc:state \"" + stateValue + "\" . \n" +
                        "           OPTIONAL { ?s edc:stateTimestamp ?ts } \n"+
                        "           OPTIONAL { ?s edc:leaseUntil ?lu }\n" +
                        "           FILTER (!BOUND(?lu) || ?lu < NOW())\n" +
                        "      }\n" +
                        "    }\n" +
                        "    ORDER BY ?ts\n" +
                        "    LIMIT " + limit + "\n" +
                        "  }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { ?s edc:leasedBy ?oldBy } }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { ?s edc:leaseUntil ?oldUntil } }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { ?s edc:leaseToken ?oldToken } }\n" +
                        "}";

        sparql.sparqlUpdate(update);

        // 2) Select back the leased ids by token
        var select =
                "PREFIX edc: <" + EDC_NS + ">\n" +
                        "SELECT ?id WHERE {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    ?s edc:leaseToken \"" + token + "\" .\n" +
                        "    ?s edc:id ?id .\n" +
                        "  }\n" +
                        "}\n" +
                        "ORDER BY ?id";

        var json = sparql.sparqlSelect(select);
        return SparqlResultsJson.allBindingValues(json, "id");
    }

    private static String escapeIri(String iri) {
        // very conservative: SPARQL IRIs should not contain quotes, but avoid angle brackets etc.
        return iri.replace(">", "%3E").replace("<", "%3C").trim();
    }

    /**
     * Break lease for a single entity id (optional "owner" check).
     */
    public void breakLease(String indexGraphIri, String entityIri, String ownerOrNull) {
        var whereOwner = ownerOrNull != null
                ? "    ?s edc:leasedBy \"" + escape(ownerOrNull) + "\" .\n"
                : "    OPTIONAL { ?s edc:leasedBy ?anyBy }\n";

        var update =
                "PREFIX edc: <" + EDC_NS + ">\n" +
                        "DELETE {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    ?s edc:leasedBy ?by .\n" +
                        "    ?s edc:leaseUntil ?until .\n" +
                        "    ?s edc:leaseToken ?tok .\n" +
                        "  }\n" +
                        "}\n" +
                        "WHERE {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    BIND(<" + entityIri + "> AS ?s)\n" +
                        whereOwner +
                        "    OPTIONAL { ?s edc:leasedBy ?by }\n" +
                        "    OPTIONAL { ?s edc:leaseUntil ?until }\n" +
                        "    OPTIONAL { ?s edc:leaseToken ?tok }\n" +
                        "  }\n" +
                        "}";

        sparql.sparqlUpdate(update);
    }

    public boolean acquireForEntity(String indexGraphIri, String entityIri, String owner, Duration leaseDuration) {
        var token = UUID.randomUUID().toString();
        var until = Instant.now(clock).plus(leaseDuration);
        var untilLex = DateTimeFormatter.ISO_INSTANT.format(until);

        var update =
                "PREFIX edc: <" + EDC_NS + ">\n" +
                        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                        "DELETE {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    <" + entityIri + "> edc:leasedBy ?oldBy .\n" +
                        "    <" + entityIri + "> edc:leaseUntil ?oldUntil .\n" +
                        "    <" + entityIri + "> edc:leaseToken ?oldToken .\n" +
                        "  }\n" +
                        "}\n" +
                        "INSERT {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    <" + entityIri + "> edc:leasedBy \"" + escape(owner) + "\" .\n" +
                        "    <" + entityIri + "> edc:leaseUntil \"" + untilLex + "\"^^xsd:dateTime .\n" +
                        "    <" + entityIri + "> edc:leaseToken \"" + token + "\" .\n" +
                        "  }\n" +
                        "}\n" +
                        "WHERE {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    OPTIONAL { <" + entityIri + "> edc:leaseUntil ?lu }\n" +
                        "    FILTER(!BOUND(?lu) || ?lu < NOW())\n" +
                        "  }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { <" + entityIri + "> edc:leasedBy ?oldBy } }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { <" + entityIri + "> edc:leaseUntil ?oldUntil } }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { <" + entityIri + "> edc:leaseToken ?oldToken } }\n" +
                        "}";

        sparql.sparqlUpdate(update);

        var check =
                "PREFIX edc: <" + EDC_NS + ">\n" +
                        "ASK {\n" +
                        "  GRAPH <" + indexGraphIri + "> { <" + entityIri + "> edc:leaseToken \"" + token + "\" }\n" +
                        "}";

        return sparql.sparqlSelect(check).contains("true");
    }

    public List<String> acquireByStatesOldestFirst(String indexGraphIri,
                                                   List<Integer> stateValues,
                                                   int limit,
                                                   String owner,
                                                   Duration leaseDuration) {
        if (stateValues == null || stateValues.isEmpty() || limit <= 0) {
            return List.of();
        }

        var token = UUID.randomUUID().toString();
        var until = Instant.now(clock).plus(leaseDuration);
        var untilLex = DateTimeFormatter.ISO_INSTANT.format(until);

        // VALUES must match string literal states: "1" "2" "3"
        var values = stateValues.stream()
                .map(String::valueOf)
                .map(v -> "\"" + escape(v) + "\"")
                .reduce("", (acc, v) -> acc + " " + v)
                .trim();

        var update =
                "PREFIX edc: <" + EDC_NS + ">\n" +
                        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                        "DELETE {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    ?s edc:leasedBy ?oldBy .\n" +
                        "    ?s edc:leaseUntil ?oldUntil .\n" +
                        "    ?s edc:leaseToken ?oldToken .\n" +
                        "  }\n" +
                        "}\n" +
                        "INSERT {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    ?s edc:leasedBy \"" + escape(owner) + "\" .\n" +
                        "    ?s edc:leaseUntil \"" + untilLex + "\"^^xsd:dateTime .\n" +
                        "    ?s edc:leaseToken \"" + token + "\" .\n" +
                        "  }\n" +
                        "}\n" +
                        "WHERE {\n" +
                        "  {\n" +
                        "    SELECT ?s WHERE {\n" +
                        "      GRAPH <" + indexGraphIri + "> {\n" +
                        "        VALUES ?st { " + values + " }\n" +
                        "        ?s edc:state ?sval .\n" +
                        "        FILTER(?sval = ?st)\n" +
                        "        OPTIONAL { ?s edc:stateTimestamp ?ts }\n" +
                        "        OPTIONAL { ?s edc:leaseUntil ?lu }\n" +
                        "        FILTER(!BOUND(?lu) || ?lu < NOW())\n" +
                        "      }\n" +
                        "    }\n" +
                        "    ORDER BY ?ts\n" +
                        "    LIMIT " + limit + "\n" +
                        "  }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { ?s edc:leasedBy ?oldBy } }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { ?s edc:leaseUntil ?oldUntil } }\n" +
                        "  OPTIONAL { GRAPH <" + indexGraphIri + "> { ?s edc:leaseToken ?oldToken } }\n" +
                        "}";

        sparql.sparqlUpdate(update);

        var select =
                "PREFIX edc: <" + EDC_NS + ">\n" +
                        "SELECT ?id WHERE {\n" +
                        "  GRAPH <" + indexGraphIri + "> {\n" +
                        "    ?s edc:leaseToken \"" + token + "\" .\n" +
                        "    ?s edc:id ?id .\n" +
                        "  }\n" +
                        "}\n" +
                        "ORDER BY ?id";

        var json = sparql.sparqlSelect(select);
        return SparqlResultsJson.allBindingValues(json, "id");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }


}