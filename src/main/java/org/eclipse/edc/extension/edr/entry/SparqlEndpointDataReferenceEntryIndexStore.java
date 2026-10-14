package org.eclipse.edc.extension.edr.entry;

import org.eclipse.edc.edr.spi.store.EndpointDataReferenceEntryIndex;
import org.eclipse.edc.edr.spi.types.EndpointDataReferenceEntry;
import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.TypeManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.eclipse.edc.spi.result.StoreResult.generalError;
import static org.eclipse.edc.spi.result.StoreResult.notFound;
import static org.eclipse.edc.spi.result.StoreResult.success;

public class SparqlEndpointDataReferenceEntryIndexStore
        extends AbstractSparqlStore<EndpointDataReferenceEntry>
        implements EndpointDataReferenceEntryIndex {

    public SparqlEndpointDataReferenceEntryIndexStore(ISparqlClient sparqlClient,
                                                      TypeManager typeManager,
                                                      String nameGraphIri,
                                                      String baseNsIri,
                                                      Monitor monitor) {
        super(sparqlClient, typeManager, nameGraphIri, baseNsIri, monitor, StoreEntity.EDR_ENTRY);
    }

    @Override
    public @Nullable EndpointDataReferenceEntry findById(String transferProcessId) {
        if (transferProcessId == null || transferProcessId.isBlank()) {
            return null;
        }

        try {
            return internalFindById(transferProcessId, EndpointDataReferenceEntry.class);
        } catch (Exception e) {
            // Spec says @Nullable return; no StoreResult here
            monitor.severe("Failed to find EDR Entry for transferProcessId=%s: %s"
                    .formatted(transferProcessId, e.getMessage()));
            return null;
        }
    }

    @Override
    public StoreResult<List<EndpointDataReferenceEntry>> query(QuerySpec querySpec) {
        try {
            var list = internalFindAll(querySpec, EndpointDataReferenceEntry.class).toList();
            return success(list);
        } catch (Exception e) {
            return generalError("Failed to query EDR entries: " + e.getMessage());
        }
    }

    @Override
    public StoreResult<Void> save(EndpointDataReferenceEntry entry) {
        if (entry == null) {
            return generalError("EndpointDataReferenceEntry must not be null");
        }
        var id = entry.getTransferProcessId();
        if (id == null || id.isBlank()) {
            return generalError("EndpointDataReferenceEntry.transferProcessId must not be null/blank");
        }

        try {
            internalUpsert(id, entry);
            return success();
        } catch (Exception e) {
            return generalError("Failed to save EDR entry for transferProcessId=%s: %s"
                    .formatted(id, e.getMessage()));
        }
    }

    @Override
    public StoreResult<EndpointDataReferenceEntry> delete(String transferProcessId) {
        if (transferProcessId == null || transferProcessId.isBlank()) {
            return generalError("transferProcessId must not be null/blank");
        }

        try {
            var existing = internalFindById(transferProcessId, EndpointDataReferenceEntry.class);
            if (existing == null) {
                return notFound(ENDPOINT_DATA_REFERENCE_ENTRY_FOUND.formatted(transferProcessId));
            }

            internalDelete(transferProcessId);
            return success(existing);
        } catch (Exception e) {
            return generalError("Failed to delete EDR entry for transferProcessId=%s: %s"
                    .formatted(transferProcessId, e.getMessage()));
        }
    }
}