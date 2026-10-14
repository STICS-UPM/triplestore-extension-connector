package org.eclipse.edc.extension.common.lease;

import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.sparql.SparqlResultsJson;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.QueryBuilder;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.types.TypeManager;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Generic JSON-blob storage + minimal indexed fields in SPARQL.
 *
 * Triples (in entity graph):
 *   <urn:edc:entity:<id>> a edc:ContractNegotiation ;
 *       edc:id "<id>" ;
 *       edc:state 12 ;
 *       edc:stateTimestamp 1700000000000 ;
 *       edc:participantContextId "<pcId>" ;
 *       edc:json "{...}" .
 */
public class SparqlStoreLease<T> extends AbstractSparqlStore<T> {

    private final QueryBuilderLease queryBuilderLease;

    public SparqlStoreLease(ISparqlClient sparqlClient, TypeManager typeManager, String nameGraphIri, String baseNsIri, Monitor monitor, StoreEntity storeEntity) {
        super(sparqlClient, typeManager, nameGraphIri, baseNsIri, monitor, storeEntity);
        this.queryBuilderLease = new QueryBuilderLease(storeEntity);
    }


    /** Expose entity IRI computation for lease manager usage */
    public String entityIri(String entityId) {
        return ensureIsIRI(entityId);
    }

    public void internalUpsertLease(String entityId, String json, int state, long stateTimestamp, String participantContextId) {
        var entitySubject = ensureIsIRI(entityId);// TODO: write code to ensure this is a valid IRI
        var stateValue = String.valueOf(state);
        String participantContext = sparqlString((Optional.ofNullable(participantContextId).orElse("")));

        var query = this.queryBuilderLease.updateEntityLease(this.nameGraphIri, entitySubject, stateValue, String.valueOf(stateTimestamp), participantContext, json);
        this.monitor.info("AbstractSparqlStoreLease (internalUpsertLease): "+query);
        this.sparqlClient.sparqlUpdate(query);
    }




    /**
     * Get candidate IDs for processing: state in allowedStates, not leased (leaseUntil <= now or absent),
     * ordered by stateTimestamp ASC, limit N.
     *
     * Note: lease check is done against the lease graph by joining on derived lease IRI.
     */
    @Deprecated
    public Stream<String> nextNotLeasedIds(
            String leaseGraphIri, String leaseBaseIri, Instant now, int limit, int... allowedStates
    ) {
        var states = String.join(" ", java.util.Arrays.stream(allowedStates).mapToObj(String::valueOf).toList());
        String limitStr = String.valueOf(limit);
        var query = this.queryBuilderLease.nextNotLeaseIds(this.nameGraphIri, states, leaseGraphIri, sparqlString(leaseBaseIri), now.toString(), limitStr);
        this.monitor.info("AbstractSparqlStoreLease (nextNotLeasedIds): "+query);
        return sparqlClient.selectColumnAsStream(query, "id");
    }



    /** Wrapping protected methods for composite pattern**/

    public T findById(String id, Class<T> clazz) {
        return internalFindById(id, clazz);
    }

    public Stream<T> findAll(QuerySpec querySpec, Class<T> clazz) {
        return internalFindAll(querySpec, clazz);
    }

    public boolean existsById(String id) {
        return internalAskIfExistsById(id);
    }

    public void deleteByIdInternal(String id) {
        internalDelete(id);
    }


    public String selectSingleLiteral(String query, String column) {
        return sparqlClient.selectSingleLiteral(query, column);
    }

    public Stream<String> selectColumnAsStream(String query, String column) {
        return sparqlClient.selectColumnAsStream(query, column);
    }

    public String graphIri() {
        return nameGraphIri;
    }

    public void sparqlUpdate(String update) {
        sparqlClient.sparqlUpdate(update);
    }

    /** Expose SPARQL literal escaping to callers (sparqlString is protected in AbstractSparqlStore). */
    public String escapeLiteral(String s) {
        return sparqlString(s);
    }

}