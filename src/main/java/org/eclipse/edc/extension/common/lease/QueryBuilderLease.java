package org.eclipse.edc.extension.common.lease;

import org.eclipse.edc.extension.common.store.QueryBuilder;
import org.eclipse.edc.extension.common.store.StoreEntity;

import java.util.Arrays;
import java.util.Optional;

public class QueryBuilderLease {

    private final StoreEntity storeEntity;

    public QueryBuilderLease(StoreEntity storeEntity) {
        this.storeEntity = storeEntity;
    }



    public String updateEntityLease(String namedGraphIri,
                                    String entitySubject,
                                    String stateValue,
                                    String timestamp,
                                    String participantContext,
                                    String jsonEscaped) {

        var hasPc = participantContext != null && !participantContext.isBlank();
        // Same layout as QueryBuilder (plan decision D52): the meta-subject holds the index fields,
        // the lease fields and the JSON, and links to the entity subject with edc:unfoldedArtefact.
        var metaSubject = QueryBuilder.metaSubject(namedGraphIri, entitySubject);
        var pcTriple = hasPc ? "    edc:participantContextId \"" + participantContext + "\" ;\n" : "";

        return  "PREFIX edc: <https://w3id.org/edc/v0.0.1/ns/>\n"
                + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
                + "DELETE {\n" // TODO: Maybe here remove only relevant triplets, those: edc:id, edc:state, edc:stateTimestamp, and edc:json11Serialization
                + "  GRAPH <" + namedGraphIri + "> { <" + metaSubject + "> ?p ?o . }\n"
                + "}\n"
                + "INSERT {\n"
                + "  GRAPH <" + namedGraphIri + "> {\n"
                + "    <" + metaSubject + "> a edc:" + storeEntity + " ;\n"
                + "    edc:unfoldedArtefact <" + entitySubject + "> ;\n"
                + "    edc:id \"" + entitySubject + "\" ;\n"
                + "    edc:state \"" + stateValue + "\" ;\n"
                + "    edc:stateTimestamp \"" + timestamp + "\"^^xsd:long ;\n"
                + pcTriple
                + "    edc:json11Serialization \"" + jsonEscaped + "\" .\n"
                + "  }\n"
                + "}\n"
                + "WHERE {\n"
                + "  OPTIONAL { GRAPH <" + namedGraphIri + "> { <" + metaSubject + "> ?p ?o . } }\n"
                + "}\n";
    }



    /**
     * Deprecated helper: selects candidate ids ordered by stateTimestamp ASC that are not leased (or lease expired).
     *
     * IMPORTANT: this assumes leases are written in the SAME graph as the entities (namedGraphIri),
     * because your SparqlLeaseManager stores edc:leaseUntil etc. in indexGraphIri and you are passing the entity graph.
     *
     * If you still use a separate lease graph, you must keep the join. Otherwise, remove leaseGraphIri/leaseBaseIri.
     */
    @Deprecated
    public String nextNotLeaseIds(String namedGraphIri,
                                  String states,
                                  String leaseGraphIri,
                                  String leaseBaseIri,
                                  String now,
                                  String limit) {

        // Expect `states` to be a space-separated list of integer states like:  "1 2 3"
        // We store state as STRING literal, so values must be:  "1" "2" "3"
        var values = Arrays.stream(states.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(s -> "\"" + s + "\"")
                .reduce("", (a, b) -> a + " " + b)
                .trim();

        return ""
                + "PREFIX edc: <https://w3id.org/edc/v0.0.1/ns/>\n"
                + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
                + "SELECT ?id ?ts WHERE {\n"
                + "  GRAPH <" + namedGraphIri + "> {\n"
                + "    VALUES ?st { " + values + " }\n"
                + "    ?e edc:id ?id ;\n"
                + "       edc:state ?s ;\n"
                + "       edc:stateTimestamp ?ts .\n"
                + "    FILTER(?s = ?st)\n"
                + "  }\n"
                + "  OPTIONAL {\n"
                + "    GRAPH <" + namedGraphIri + "> { ?e edc:leaseUntil ?u }\n"
                + "  }\n"
                + "  FILTER(!BOUND(?u) || ?u < NOW())\n"
                + "}\n"
                + "ORDER BY ASC(?ts)\n"
                + "LIMIT " + limit + "\n";
    }
}
