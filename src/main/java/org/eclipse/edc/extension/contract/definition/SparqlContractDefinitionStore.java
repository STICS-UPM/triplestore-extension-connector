package org.eclipse.edc.extension.contract.definition;

import java.util.Objects;
import java.util.stream.Stream;

import org.apache.jena.ext.com.google.common.base.Strings;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.EdcPersistenceException;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.TypeManager;

import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.jetbrains.annotations.NotNull;


import static org.eclipse.edc.spi.result.StoreResult.success;
import static org.eclipse.edc.spi.result.StoreResult.notFound;

public class SparqlContractDefinitionStore extends AbstractSparqlStore<ContractDefinition> implements ContractDefinitionStore {

    private final static String CONTRACT_ID_NULL_OR_BLANK = "Contract id must not be null/blank";


    public SparqlContractDefinitionStore(ISparqlClient sparqlClient, TypeManager typeManager, String nameGraphIri, String baseNsIri, Monitor monitor) {
        super(sparqlClient, typeManager, nameGraphIri, baseNsIri, monitor, StoreEntity.CONTRACT_DEFINITION);
    }


    @Override
    public StoreResult<Void> save(ContractDefinition contractDefinition) {
        String id = contractDefinition.getId();
        if (Strings.isNullOrEmpty(id))
            return StoreResult.generalError(CONTRACT_ID_NULL_OR_BLANK);

        if (internalAskIfExistsById(id))
            return StoreResult.alreadyExists(ContractDefinitionStore.CONTRACT_DEFINITION_EXISTS.formatted(id));

        monitor.info("[SparqlAssetIndex] creating Contract: "+contractDefinition.toString());
        createUpdate(contractDefinition);
        return StoreResult.success();
    }

    @Override
    public StoreResult<Void> update(ContractDefinition contractDefinition) {
        var id = contractDefinition.getId();
        if (Strings.isNullOrEmpty(id))
            return StoreResult.generalError(CONTRACT_ID_NULL_OR_BLANK);
        if (!internalAskIfExistsById(id))
            return StoreResult.alreadyExists(ContractDefinitionStore.CONTRACT_DEFINITION_EXISTS.formatted(id));

        createUpdate(contractDefinition);
        return StoreResult.success();
    }

    private void createUpdate(ContractDefinition contract) {
        String id = contract.getId();
        try {
            internalUpsert(id, contract);
        }catch (Exception e) {
            throw new EdcPersistenceException("Error creating/updating contract %s".formatted(id), e);
        }
    }


    @Override
    public StoreResult<ContractDefinition> deleteById(String contractId) {
        if (Strings.isNullOrEmpty(contractId))
            return StoreResult.generalError(CONTRACT_ID_NULL_OR_BLANK);
        if (!internalAskIfExistsById(contractId))
            return StoreResult.notFound(PolicyDefinitionStore.POLICY_NOT_FOUND.formatted(contractId));

        ContractDefinition existing;
        try {
            existing = internalFindById(contractId, ContractDefinition.class);
            if (Objects.isNull(existing))
                return notFound("Contract not found: " + contractId);
            internalDelete(contractId);
        }catch(Exception e){
            throw new EdcPersistenceException("Error deleting Contract %s".formatted(contractId), e);
        }
        return success(existing);
    }

    @Override
    public ContractDefinition findById(String contractId) {
        return internalFindById(contractId, ContractDefinition.class);
    }

    @Override
    @NotNull
    public Stream<ContractDefinition> findAll(QuerySpec querySpec) {
        return internalFindAll(querySpec, ContractDefinition.class);
    }
}
