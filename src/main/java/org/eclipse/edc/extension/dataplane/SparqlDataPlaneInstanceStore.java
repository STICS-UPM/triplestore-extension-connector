package org.eclipse.edc.extension.dataplane;

import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.extension.common.lease.SparqlLeaseManager;
import org.eclipse.edc.extension.common.lease.SparqlStoreLease;
import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.TypeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.eclipse.edc.spi.result.StoreResult.generalError;
import static org.eclipse.edc.spi.result.StoreResult.notFound;
import static org.eclipse.edc.spi.result.StoreResult.success;

/**
 * SPARQL-backed DataPlaneInstanceStore with leasing (SPI extends StateEntityStore).
 *
 * Entities + lease fields are stored in the same named graph (store.graphIri()).
 */
public class SparqlDataPlaneInstanceStore implements DataPlaneInstanceStore {

    private final SparqlStoreLease<DataPlaneInstance> store;
    private final SparqlLeaseManager leaseManager;
    private final TypeManager typeManager;
    private final Monitor monitor;

    private final Duration leaseDuration;
    private final String leaseOwner;

    public SparqlDataPlaneInstanceStore(ISparqlClient sparqlClient,
                                        TypeManager typeManager,
                                        Monitor monitor,
                                        String nameGraphIri,
                                        String baseNsIri,
                                        SparqlLeaseManager leaseManager,
                                        Duration leaseDuration,
                                        String leaseOwner) {
        this.typeManager = Objects.requireNonNull(typeManager);
        this.monitor = Objects.requireNonNull(monitor);
        this.leaseManager = Objects.requireNonNull(leaseManager);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        this.leaseOwner = (leaseOwner == null || leaseOwner.isBlank()) ? "edc-runtime" : leaseOwner;

        this.store = new SparqlStoreLease<>(
                Objects.requireNonNull(sparqlClient),
                this.typeManager,
                Objects.requireNonNull(nameGraphIri),
                Objects.requireNonNull(baseNsIri),
                this.monitor,
                StoreEntity.DATA_PLANE_INSTANCE
        );
    }

    // -------------------------------------------------------------------------
    // DataPlaneInstanceStore
    // -------------------------------------------------------------------------

    @Override
    public StoreResult<DataPlaneInstance> deleteById(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return generalError("instanceId must not be null/blank");
        }

        try {
            var existing = store.findById(instanceId, DataPlaneInstance.class);
            if (existing == null) {
                return notFound("DataPlaneInstance not found: " + instanceId);
            }

            store.deleteByIdInternal(instanceId);

            // best-effort lease cleanup
            leaseManager.breakLease(store.graphIri(), store.entityIri(instanceId), null);

            return success(existing);
        } catch (Exception e) {
            return generalError("Failed deleting DataPlaneInstance %s: %s".formatted(instanceId, e.getMessage()));
        }
    }

    @Override
    public Stream<DataPlaneInstance> getAll() {
        var qs = QuerySpec.Builder.newInstance().build();
        return store.findAll(qs, DataPlaneInstance.class);
    }

    // -------------------------------------------------------------------------
    // StateEntityStore
    // -------------------------------------------------------------------------

    @Override
    public @Nullable DataPlaneInstance findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return store.findById(id, DataPlaneInstance.class);
    }

    @Override
    public @NotNull List<DataPlaneInstance> nextNotLeased(int max, Criterion... criteria) {
        if (max <= 0) {
            return List.of();
        }

        var state = extractState(criteria);
        if (state == null) {
            // safer: most state machines always pass hasState(x)
            return List.of();
        }

        var leasedIds = leaseManager.acquireByState(
                store.graphIri(),
                String.valueOf(state),
                max,
                leaseOwner,
                leaseDuration
        );

        return leasedIds.stream()
                .map(id -> store.findById(id, DataPlaneInstance.class))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public StoreResult<DataPlaneInstance> findByIdAndLease(String id) {
        if (id == null || id.isBlank()) {
            return generalError("id must not be null/blank");
        }

        var entity = store.findById(id, DataPlaneInstance.class);
        if (entity == null) {
            return notFound("DataPlaneInstance not found: " + id);
        }

        var ok = leaseManager.acquireForEntity(store.graphIri(), store.entityIri(id), leaseOwner, leaseDuration);
        if (!ok) {
            return generalError("DataPlaneInstance is currently leased: " + id);
        }

        return success(entity);
    }

    @Override
    public StoreResult<Void> save(DataPlaneInstance entity) {
        if (entity == null) {
            throw new IllegalArgumentException("DataPlaneInstance must not be null");
        }
        var id = entity.getId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("DataPlaneInstance.id must not be null/blank");
        }

        try {
            var json = typeManager.writeValueAsString(entity);
            var jsonEscaped = store.escapeLiteral(json);

            store.internalUpsertLease(
                    id,
                    jsonEscaped,
                    entity.getState(),
                    entity.getStateTimestamp(),
                    null // no participantContextId here
            );

            // IMPORTANT: break lease on save/update
            leaseManager.breakLease(store.graphIri(), store.entityIri(id), null);
            return StoreResult.success();
        } catch (Exception e) {
            //throw new RuntimeException("Failed to save DataPlaneInstance %s: %s".formatted(id, e.getMessage()), e);
            return StoreResult.generalError("Failed to save DataPlaneInstance %s: %s".formatted(id, e.getMessage()));
        }
    }

    private Integer extractState(Criterion... criteria) {
        if (criteria == null) {
            return null;
        }

        for (var c : criteria) {
            if (c == null) {
                continue;
            }
            if ("state".equals(String.valueOf(c.getOperandLeft())) && "=".equals(String.valueOf(c.getOperator()))) {
                var right = c.getOperandRight();
                if (right instanceof Number n) {
                    return n.intValue();
                }
                try {
                    return Integer.parseInt(String.valueOf(right));
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}