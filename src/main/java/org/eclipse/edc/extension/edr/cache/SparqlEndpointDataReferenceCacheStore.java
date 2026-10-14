package org.eclipse.edc.extension.edr.cache;

import org.eclipse.edc.edr.spi.store.EndpointDataReferenceCache;
import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.util.Objects;

import static org.eclipse.edc.spi.result.StoreResult.*;

/**
 * SPARQL-backed EndpointDataReferenceCache.
 *
 * Stores DataAddress objects keyed by transferProcessId.
 * No leasing required (EDRs are immutable runtime artifacts).
 */
public class SparqlEndpointDataReferenceCacheStore extends AbstractSparqlStore<DataAddress> implements EndpointDataReferenceCache {

    public SparqlEndpointDataReferenceCacheStore(
            ISparqlClient sparqlClient,
            TypeManager typeManager,
            String nameGraphIri,
            String baseNsIri,
            Monitor monitor
    ) {
        super(
                sparqlClient,
                typeManager,
                nameGraphIri,
                baseNsIri,
                monitor,
                StoreEntity.EDR_CACHE
        );
    }

    /**
     * Resolves a DataAddress for a transfer process.
     */
    @Override
    public StoreResult<DataAddress> get(String transferProcessId) {
        if (transferProcessId == null || transferProcessId.isBlank()) {
            return generalError("transferProcessId must not be null or blank");
        }

        try {
            var dataAddress = internalFindById(transferProcessId, DataAddress.class);
            if (dataAddress == null) {
                return notFound("No EDR found for transferProcessId=" + transferProcessId);
            }
            return success(dataAddress);
        } catch (Exception e) {
            return generalError(
                    "Failed to resolve EDR for transferProcessId=%s: %s"
                            .formatted(transferProcessId, e.getMessage())
            );
        }
    }

    /**
     * Caches a DataAddress for a transfer process.
     */
    @Override
    public StoreResult<Void> put(String transferProcessId, DataAddress edr) {
        if (transferProcessId == null || transferProcessId.isBlank()) {
            return generalError("transferProcessId must not be null or blank");
        }
        if (Objects.isNull(edr)) {
            return generalError("DataAddress must not be null");
        }

        try {
            monitor.info(
                    "SparqlEndpointDataReferenceCacheStore: caching EDR for transferProcessId=" +
                            transferProcessId
            );
            internalUpsert(transferProcessId, edr);
            return success();
        } catch (Exception e) {
            return generalError(
                    "Failed to cache EDR for transferProcessId=%s: %s"
                            .formatted(transferProcessId, e.getMessage())
            );
        }
    }

    /**
     * Deletes a DataAddress from the cache.
     */
    @Override
    public StoreResult<Void> delete(String transferProcessId) {
        if (transferProcessId == null || transferProcessId.isBlank()) {
            return generalError("transferProcessId must not be null or blank");
        }

        try {
            if (!internalAskIfExistsById(transferProcessId)) {
                return notFound("No EDR found for transferProcessId=" + transferProcessId);
            }
            internalDelete(transferProcessId);
            return success();
        } catch (Exception e) {
            return generalError(
                    "Failed to delete EDR for transferProcessId=%s: %s"
                            .formatted(transferProcessId, e.getMessage())
            );
        }
    }
}