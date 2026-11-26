package org.eclipse.edc.extension.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.extension.common.lease.SparqlLeaseManager;
import org.eclipse.edc.extension.common.lease.SparqlStoreLease;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.TypeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.eclipse.edc.spi.result.StoreResult.*;

public class SparqlTransferProcessStore implements TransferProcessStore {

    private final SparqlStoreLease<TransferProcess> transferStore;
    private final SparqlLeaseManager leaseManager;
    private final ObjectMapper mapper;
    private final Clock clock;

    private final String leaseOwner;
    private final String leaseGraphIri;
    private final String leaseBaseIri;
    private final Duration leaseDuration;

    public SparqlTransferProcessStore(SparqlStoreLease<TransferProcess> transferStore,
                                      SparqlLeaseManager leaseManager,
                                      TypeManager typeManager,
                                      Clock clock,
                                      String leaseGraphIri,
                                      String leaseBaseIri,
                                      Duration leaseDuration,
                                      String leaseOwner) {
        this.transferStore = Objects.requireNonNull(transferStore);
        this.leaseManager = Objects.requireNonNull(leaseManager);
        this.mapper = Objects.requireNonNull(typeManager).getMapper();
        this.clock = Objects.requireNonNull(clock);
        this.leaseGraphIri = Objects.requireNonNull(leaseGraphIri);
        this.leaseBaseIri = Objects.requireNonNull(leaseBaseIri);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        this.leaseOwner = Objects.requireNonNull(leaseOwner);
    }

    @Override
    public StoreResult<Void> save(TransferProcess entity) {
        try {
            var json = mapper.writeValueAsString(entity);

            // TransferProcess in some versions may have participantContextId; in others it doesn’t.
            // Your SparqlStoreLease requires it, so store empty when not available.
            transferStore.internalUpsertLease(
                    entity.getId(),
                    json,
                    entity.getState(),
                    entity.getStateTimestamp(),
                    ""
            );

            // SQL stores typically break lease after persisting processed state
            leaseManager.breakLease(leaseGraphIri, entity.getId(), null);
            return StoreResult.success();
        } catch (Exception e) {
            //throw new RuntimeException("Failed saving TransferProcess: " + e.getMessage(), e);
            return StoreResult.generalError("Failed saving TransferProcess: " + e.getMessage());
        }
    }

    @Override
    public @Nullable TransferProcess findById(String id) {
        return transferStore.findById(id, TransferProcess.class);
    }

    @Override
    public StoreResult<TransferProcess> findByIdAndLease(String id) {
        if (id == null || id.isBlank()) {
            return generalError("TransferProcess id must not be null/blank");
        }

        if (!transferStore.existsById(id)) {
            return notFound("TransferProcess not found: " + id);
        }

        var ok = leaseManager.acquireForEntity(leaseGraphIri, id, leaseOwner(), leaseDuration);
        if (!ok) {
            return generalError("Entity is currently leased: " + id);
        }

        var entity = findById(id);
        if (entity == null) {
            leaseManager.breakLease(leaseGraphIri, id, null);
            return notFound("TransferProcess not found: " + id);
        }

        return success(entity);
    }

    @Override
    public @NotNull List<TransferProcess> nextNotLeased(int max, Criterion... criteria) {
        var states = extractStates(criteria);
        if (states.length == 0) {
            return List.of();
        }

        Stream<String> leasedIds = Stream.empty();
        for (var s : states) {
            var idsForState = leaseManager.acquireByState(
                    leaseGraphIri,
                    stateIri(s),      // IMPORTANT: this matches your semantic state approach
                    max,
                    leaseOwner(),
                    leaseDuration
            ).stream();
            leasedIds = Stream.concat(leasedIds, idsForState);
        }

        return leasedIds
                .distinct()
                .map(this::findById)
                .filter(Objects::nonNull)
                .toList();
    }

    // If your TransferProcessStore SPI provides query(QuerySpec), keep this.
    // If it doesn’t exist in your version, remove it.
    public @NotNull Stream<TransferProcess> query(QuerySpec querySpec) {
        return transferStore.findAll(querySpec, TransferProcess.class);
    }

    // If your SPI has releaseLease(entityId), keep this.
    public void releaseLease(String entityId) {
        if (entityId != null && !entityId.isBlank()) {
            leaseManager.breakLease(leaseGraphIri, entityId, null);
        }
    }


    private String leaseOwner() {
        return (leaseOwner != null && !leaseOwner.isBlank()) ? leaseOwner : "edc-runtime";
    }

    private String stateIri(int state) {
        return "https://w3id.org/edc/state/" + state;
    }

    private static int[] extractStates(Criterion... criteria) {
        if (criteria == null || criteria.length == 0) {
            return new int[0];
        }

        return Stream.of(criteria)
                .filter(Objects::nonNull)
                .filter(c -> "state".equalsIgnoreCase((String) c.getOperandLeft()))
                .map(Criterion::getOperandRight)
                .filter(Objects::nonNull)
                .mapToInt(v -> {
                    if (v instanceof Number n) {
                        return n.intValue();
                    }
                    return Integer.parseInt(String.valueOf(v));
                })
                .distinct()
                .toArray();
    }


    // TODO: Implement these methods

    // -------------------- TransferProcessStore extra methods --------------------

    @Override
    public @Nullable TransferProcess findForCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }

        // This requires that you store edc:correlationId on each TransferProcess subject.
        // If you aren’t storing it yet, see the note below.
        var query =
                "PREFIX edc: <https://w3id.org/edc/v0.0.1/ns/>\n" +
                        "SELECT ?j WHERE {\n" +
                        "  GRAPH <" + transferStore.graphIri() + "> {\n" +
                        "    ?s edc:correlationId \"" + escape(correlationId) + "\" ;\n" +
                        "       edc:json ?j .\n" +
                        "  }\n" +
                        "}\n" +
                        "LIMIT 1";

        var json = transferStore.selectSingleLiteral(query, "j");
        if (json == null) {
            return null;
        }

        try {
            return mapper.readValue(json, TransferProcess.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public StoreResult<Void> delete(String processId) {
        if (processId == null || processId.isBlank()) {
            return StoreResult.notFound("TransferProcess id must not be null or blank");
        }
        transferStore.deleteByIdInternal(processId);
        leaseManager.breakLease(leaseGraphIri, processId, null);
        return StoreResult.success();
    }

    @Override
    public Stream<TransferProcess> findAll(QuerySpec querySpec) {
        return transferStore.findAll(querySpec, TransferProcess.class);
    }

    // -------------------- helpers --------------------

    private void upsertCorrelation(TransferProcess entity) {
        // if TransferProcess has correlationId; if not, remove this method and findForCorrelationId will never work.
        String correlationId;
        try {
            correlationId = entity.getCorrelationId();
        } catch (Exception e) {
            return;
        }

        if (correlationId == null || correlationId.isBlank()) {
            return;
        }

        var update =
                "PREFIX edc: <https://w3id.org/edc/v0.0.1/ns/>\n" +
                        "DELETE { GRAPH <" + transferStore.graphIri() + "> { <" + entity.getId() + "> edc:correlationId ?old } }\n" +
                        "INSERT { GRAPH <" + transferStore.graphIri() + "> { <" + entity.getId() + "> edc:correlationId \"" + escape(correlationId) + "\" } }\n" +
                        "WHERE  { OPTIONAL { GRAPH <" + transferStore.graphIri() + "> { <" + entity.getId() + "> edc:correlationId ?old } } }";

        // we reuse the lease-store’s SPARQL client wrapper
        transferStore.selectSingleLiteral("SELECT (\"ok\" AS ?j) WHERE {}", "j"); // no-op to ensure class initialized (optional)
        // better: expose sparqlUpdate in SparqlStoreLease too; if you prefer, I’ll give that patch.
        // For now: easiest is to add `public void update(String q)` wrapper (see below).
        transferStore.sparqlUpdate(update);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}