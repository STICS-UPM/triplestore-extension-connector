package org.eclipse.edc.extension.contract.negotiation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
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


/**
 * SPARQL-backed ContractNegotiationStore:
 * - Negotiations stored as JSON blobs + indexed fields
 * - Agreements stored as JSON blobs too (keyed by contractId)
 * - Leasing is done in a dedicated lease graph
 */
public class SparqlContractNegotiationStore implements ContractNegotiationStore {

    private static final String RDF_TYPE_NEGOTIATION = "ContractNegotiation";
    private static final String RDF_TYPE_AGREEMENT = "ContractAgreement";

    private final SparqlStoreLease<ContractNegotiation> negotiationStore;
    private final SparqlStoreLease<ContractAgreement> agreementStore;
    private final SparqlLeaseManager leaseManager;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String leaseOwner;
    private final String leaseGraphIri;
    private final String leaseBaseIri;

    private final Duration leaseDuration;

    public SparqlContractNegotiationStore(
            SparqlStoreLease<ContractNegotiation> negotiationStore,
            SparqlStoreLease<ContractAgreement> agreementStore,
            SparqlLeaseManager leaseManager,
            TypeManager typeManager,
            Clock clock,
            String leaseGraphIri,
            String leaseBaseIri,
            Duration leaseDuration,
            String leaseOwner
    ) {
        this.negotiationStore = Objects.requireNonNull(negotiationStore);
        this.agreementStore = Objects.requireNonNull(agreementStore);
        this.leaseManager = Objects.requireNonNull(leaseManager);
        this.mapper = Objects.requireNonNull(typeManager).getMapper();
        this.clock = Objects.requireNonNull(clock);
        this.leaseGraphIri = Objects.requireNonNull(leaseGraphIri);
        this.leaseBaseIri = Objects.requireNonNull(leaseBaseIri);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        this.leaseOwner = Objects.requireNonNull(leaseOwner);
    }


    @Override
    public @Nullable ContractAgreement findContractAgreement(String contractId) {
        // In 0.15.x the "contractId" passed here is the agreement id
        return agreementStore.findById(contractId, ContractAgreement.class);
    }

    @Override
    public StoreResult<Void> deleteById(String negotiationId) {
        if (negotiationId == null || negotiationId.isBlank()) {
            return generalError("Negotiation id must not be null/blank");
        }

        if (!negotiationStore.existsById(negotiationId)) {
            return notFound("ContractNegotiation not found: " + negotiationId);
        }

        try {
            negotiationStore.deleteByIdInternal(negotiationId);

            // best effort: remove any lease data on the negotiation subject
            leaseManager.breakLease(leaseGraphIri, negotiationId, null);

            return success();
        } catch (Exception e) {
            return generalError("Failed deleting ContractNegotiation: " + e.getMessage());
        }
    }

    @Override
    public @NotNull Stream<ContractNegotiation> queryNegotiations(QuerySpec querySpec) {
        return negotiationStore.findAll(querySpec, ContractNegotiation.class);
    }

    @Override
    public @NotNull Stream<ContractAgreement> queryAgreements(QuerySpec querySpec) {
        return agreementStore.findAll(querySpec, ContractAgreement.class);
    }

    @Override
    public @Nullable ContractNegotiation findById(String id) {
        return negotiationStore.findById(id, ContractNegotiation.class);
    }

    @Override
    public @NotNull List<ContractNegotiation> nextNotLeased(int max, Criterion... criteria) {
        // Extract state constraints from criteria (EDC worker calls nextNotLeased(max, hasState(...)) etc.)
        var states = extractStates(criteria);
        if (states.length == 0) {
            return List.of();
        }

        // Acquire leases per state; token-based leasing in SparqlLeaseManager.
        var leasedIds = Stream.<String>empty();
        for (var s : states) {
            var idsForState = leaseManager.acquireByState(
                    leaseGraphIri,
                    stateIri(s),  // IMPORTANT: acquireByState expects the state as a STRING literal
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

    private String stateIri(int state) {
        return "https://w3id.org/edc/state/" + state;
    }

    @Override
    public StoreResult<ContractNegotiation> findByIdAndLease(String id) {
        if (id == null || id.isBlank()) {
            return generalError("Negotiation id must not be null/blank");
        }

        if (!negotiationStore.existsById(id)) {
            return notFound("ContractNegotiation not found: " + id);
        }

        // Acquire lease for a single entity.
        var ok = leaseManager.acquireForEntity(leaseGraphIri, id, leaseOwner(), leaseDuration);
        if (!ok) {
            return generalError("Entity is currently leased: " + id);
        }

        var entity = findById(id);
        if (entity == null) {
            leaseManager.breakLease(leaseGraphIri, id, null);
            return notFound("ContractNegotiation not found: " + id);
        }
        return success(entity);
    }

    @Override
    public StoreResult<Void> save(ContractNegotiation entity) {
        try {
            var json = mapper.writeValueAsString(entity);

            // Your SparqlStoreLease expects participantContextId even if you don’t use it yet.
            // ContractNegotiation in your model doesn’t provide it -> store empty string.
            negotiationStore.internalUpsertLease(
                    entity.getId(),
                    json,
                    entity.getState(),
                    entity.getStateTimestamp(),
                    ""
            );

            // Persist agreement if present (stored by agreement id)
            var agreement = entity.getContractAgreement();
            if (agreement != null && agreement.getId() != null && !agreement.getId().isBlank()) {
                var agreementJson = mapper.writeValueAsString(agreement);
                agreementStore.internalUpsertLease(
                        agreement.getId(),
                        agreementJson,
                        0,
                        entity.getStateTimestamp(),
                        ""
                );
            }

            // Release lease after save/update (same semantic the SQL stores follow after processing a work item)
            leaseManager.breakLease(leaseGraphIri, entity.getId(), null);
            return StoreResult.success();
        } catch (Exception e) {
            //throw new RuntimeException(, e);
            return StoreResult.generalError("Failed saving ContractNegotiation: " + e.getMessage());
        }
    }

    private String leaseOwner() {
        // Best-effort stable runtime owner. Replace with injected runtime id if you have one.
        var owner = this.leaseOwner;
        return (owner != null && !owner.isBlank()) ? owner : "edc-runtime";
    }

    private static int[] extractStates(Criterion... criteria) {
        if (criteria == null || criteria.length == 0) {
            return new int[0];
        }

        return Stream.of(criteria)
                .filter(Objects::nonNull)
                .filter(c -> "state".equalsIgnoreCase((String) c.getOperandLeft()))
                .map(c -> c.getOperandRight())
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
}