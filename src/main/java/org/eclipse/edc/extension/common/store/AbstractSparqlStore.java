package org.eclipse.edc.extension.common.store;

import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.sparql.SparqlResultsJson;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.types.TypeManager;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class AbstractSparqlStore<T> {

    protected final ISparqlClient sparqlClient;
    protected final TypeManager typeManager;
    protected final String nameGraphIri;
    protected final String baseNsIri;
    protected final Monitor monitor;
    protected final QueryBuilder queryBuilder;

    public AbstractSparqlStore(ISparqlClient sparqlClient, TypeManager typeManager, String nameGraphIri, String baseNsIri, Monitor monitor, StoreEntity storeEntity) {
        this.monitor = monitor;
        this.sparqlClient = Objects.requireNonNull(sparqlClient);
        this.typeManager =  Objects.requireNonNull(typeManager);
        // Named Graph IRI
        this.nameGraphIri =  Objects.requireNonNull(nameGraphIri);
        // Default IRI (base)
        this.baseNsIri = Objects.requireNonNull(baseNsIri);
        queryBuilder = new QueryBuilder(storeEntity);
    }

   public String getNamedGraphIri() {
        return nameGraphIri;
   }

   public String getBaseNsIri() {
        return baseNsIri;
   }


    /** Methods: From <T> to JsonLD1.1 **/

    protected String toJson(T object){
        String json;
        try {
            json = typeManager.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize PolicyDefinition to JSON", e);
        }
        return escapeStringForQuery(json);
    }

    protected T fromJson(String json, Class<T> clazz){
        return typeManager.readValue(json, clazz);
    }

    protected String escapeStringForQuery(String value) {
        return value.replace("\\\\", "\\\\\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\\\n")
                .replace("\r", "\\\\r")
                .replace("\t", "\\\\t");
    }

    /** Validation **/

    private void validateEntityId(String entityId){
        if (entityId == null || entityId.isBlank()) {
            throw new RuntimeException("Failed using provided entity id, since it's null");
        }
    }

    protected String isValidURI(String id){
        String result = id;
        try {
            new URL(id);
        } catch (MalformedURLException e) {
            monitor.info("Invalid entity id '" + id + "'", e);
            if(!id.contains(":")) {
                // TODO: Fix URI, for instance encode id if its not a valid URI
                result =  baseNsIri + id;
            }else{
                throw new RuntimeException("Invalid entity id '" + id + "'");
            }
        }
        return result;
    }

    /** Query methods **/

    protected void internalUpsert(String entityId, T instance){
        // DONE: Check if entityId is an IRI or rely on the base namespace
        validateEntityId(entityId);
        String entityIri = isValidURI(entityId);
        String rawJsonLd11 = toJson(instance);
        monitor.info("AbstractSparqlStore (internalUpsert): update graph <"+this.nameGraphIri+"> subject : <"+entityIri+">");
        // TODO: INSERT VALIDATION HERE
        String query = queryBuilder.buildUpdateQuery(nameGraphIri, entityIri, rawJsonLd11);
        monitor.info("SparqlStore (internalUpsert:"+queryBuilder.getStoreEntity()+"): " + query);
        sparqlClient.sparqlUpdate(query);
    }


    protected T internalFindById(String entityId, Class<T> clazz){
        // TODO: Check if entityId is an IRI or rely on the base namespace
        validateEntityId(entityId);
        String entityIri = isValidURI(entityId);
        monitor.info("AbstractSparqlStore (internalFindById): query graph <"+this.nameGraphIri+"> subject : <"+entityIri+">");
        String query = queryBuilder.findById(this.nameGraphIri, entityIri);
        String json = sparqlClient.sparqlSelect(query);
        String bindindedValue = SparqlResultsJson.firstBindingValue(json, "json");

        // TODO: Wrap in try/catch?
        if (!Objects.isNull(bindindedValue))
            return fromJson(bindindedValue, clazz);

        return null;
    }
    
    protected boolean internalAskIfExistsById(String entityId){
        validateEntityId(entityId);
        String entityIri = isValidURI(entityId);
        monitor.info("AbstractSparqlStore (internalAskIfExistsById): query graph <"+this.nameGraphIri+"> subject : <"+entityIri+">");
        String query = queryBuilder.findByIdASK(this.nameGraphIri, entityIri);
        String queryResult = sparqlClient.sparqlSelect(query);
        this.monitor.info("[TOREMOVE] AbstractSparqlStore (quickExists): bindedValue"+queryResult);
        return queryResult.contains("true");
    }

    protected Stream<T> internalFindAll(QuerySpec querySpecification, Class<T> clazz){
        Integer limit = querySpecification != null && querySpecification.getLimit() > 0 ? querySpecification.getLimit() : null;
        Integer offset = querySpecification != null && querySpecification.getOffset() > 0 ? querySpecification.getOffset() : null;

        monitor.info("AbstractSparqlStore (internalFindAll): query graph <"+this.nameGraphIri+"> ");
        String query = queryBuilder.findAllJsonLd(this.nameGraphIri, offset, limit);
        monitor.info("AbstractSparqlStore (internalFindAll) query: " + query);
        String jsonResults = sparqlClient.sparqlSelect(query);

        return SparqlResultsJson.allBindingValues(jsonResults, "json")
                .stream()
                .map(entityJsonSerialization -> {
                    try {
                        return fromJson(entityJsonSerialization, clazz);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to deserialize PolicyDefinition JSON from triplestore", e);
                    }
                });
    }


    protected void internalDelete(String entityId){
        validateEntityId(entityId);
        String entityIri = isValidURI(entityId);
        monitor.info("AbstractSparqlStore (internalDelete): query graph <"+this.nameGraphIri+"> subject : <"+entityIri+">");

        var update = queryBuilder.deleteById(nameGraphIri, entityIri);
        monitor.info("QUERY (delete): " + update);

        sparqlClient.sparqlUpdate(update);
    }
}
