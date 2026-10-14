package org.eclipse.edc.extension.policy;


import org.apache.jena.ext.com.google.common.base.Strings;
import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.types.TypeManager;

import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.stream.Stream;

/**
 * Stores PolicyDefinitions in an RDF triple store using SPARQL UPDATE/SELECT.
 *
 * Storage model:
 *   GRAPH <graphIri> {
 *     <policyIri(id)> a edc:PolicyDefinition ;
 *       edc:policyDefinitionId "..." ;
 *       edc:policyDefinitionJson "...json..." .
 *   }
 */
public class SparqlPolicyDefinitionStore extends AbstractSparqlStore<PolicyDefinition> implements PolicyDefinitionStore {

    private final static String POLICY_ID_NULL_OR_BLANK = "PolicyDefinition id must not be null/blank";

    public SparqlPolicyDefinitionStore(ISparqlClient sparqlClient, TypeManager typeManager, String nameGraphIri, String baseNsIri, Monitor monitor) {
       super(sparqlClient, typeManager, nameGraphIri, baseNsIri, monitor, StoreEntity.POLICY);
    }


    public StoreResult<PolicyDefinition> create(PolicyDefinition policyDefinition) {
        var id = policyDefinition.getId();
        if (Strings.isNullOrEmpty(id))
            return StoreResult.generalError(POLICY_ID_NULL_OR_BLANK);

        if (internalAskIfExistsById(id))
            return StoreResult.alreadyExists(PolicyDefinitionStore.POLICY_ALREADY_EXISTS.formatted(id));

        return createUpdate(policyDefinition);
    }

    public StoreResult<PolicyDefinition> update(PolicyDefinition policyDefinition) {
        var id = policyDefinition.getId();
        if (Strings.isNullOrEmpty(id))
            return StoreResult.generalError(POLICY_ID_NULL_OR_BLANK);
        if (!internalAskIfExistsById(id))
            return StoreResult.alreadyExists(PolicyDefinitionStore.POLICY_NOT_FOUND.formatted(id));

        return createUpdate(policyDefinition);
    }

    private StoreResult<PolicyDefinition> createUpdate(PolicyDefinition policyDefinition) {
        String id = policyDefinition.getId();
        try {
            internalUpsert(id, policyDefinition);
        }catch (Exception e) {
            throw new EdcPersistenceException("Error creating/updating policy %s".formatted(id), e);
        }
        return StoreResult.success(policyDefinition);
    }


    public StoreResult<PolicyDefinition> delete(String id) {
        if (Strings.isNullOrEmpty(id))
            return StoreResult.generalError(POLICY_ID_NULL_OR_BLANK);

        if (!internalAskIfExistsById(id))
            return StoreResult.notFound(PolicyDefinitionStore.POLICY_NOT_FOUND.formatted(id));

        try {
            internalDelete(id);
        }catch (Exception e) {
            throw new EdcPersistenceException("Error deleting policy %s".formatted(id), e);
        }

        return StoreResult.success();
    }

    public PolicyDefinition findById(String elementId) {
        return internalFindById(elementId, PolicyDefinition.class);
    }

    public Stream<PolicyDefinition> findAll(QuerySpec querySpec) {
        return internalFindAll(querySpec, PolicyDefinition.class);
    }

}
