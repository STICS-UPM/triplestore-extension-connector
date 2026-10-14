package org.eclipse.edc.extension.participant.context;

import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.TypeManager;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.eclipse.edc.spi.result.StoreResult.success;
import static org.eclipse.edc.spi.result.StoreResult.*;



public class SparqlParticipantContextStore
        extends AbstractSparqlStore<ParticipantContext>
        implements ParticipantContextStore {

    public SparqlParticipantContextStore(ISparqlClient sparqlClient,
                                         TypeManager typeManager,
                                         String graphIri,
                                         String baseNsIri,
                                         Monitor monitor) {
        super(sparqlClient, typeManager, graphIri, baseNsIri, monitor, StoreEntity.PARTICIPANT_CONTEXT);
    }

    // ------------------------------------------------------------
    // QUERY
    // ------------------------------------------------------------

    /**
     * Extracts a filter like: leftOperand = rightOperand, where leftOperand matches `field`.
     * Returns rightOperand as String if found, null otherwise.
     */
    private static String tryExtractEqualsFilter(QuerySpec querySpec, String field) {
        if (querySpec == null) {
            return null;
        }
        var filters = querySpec.getFilterExpression();
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        for (var f : filters) {
            if (!(f instanceof Criterion)) {
                continue;
            }
            Criterion criterion = (Criterion) f;
            var left = criterion.getOperandLeft();
            var op = criterion.getOperator();
            var right = criterion.getOperandRight();

            if (left != null && left.equals(field) && "=".equals(op) && right != null) {
                return String.valueOf(right);
            }
        }
        return null;
    }

    @Override
    public StoreResult<Collection<ParticipantContext>> query(QuerySpec querySpec) {
        try {
            //  queryByParticipantContextId(...) acaba siendo un filtro "=" por participantContextId
            var id = tryExtractEqualsFilter(querySpec, "participantContextId");
            if (id == null) {
                // Algunas implementaciones usan "id" directamente
                id = tryExtractEqualsFilter(querySpec, "id");
            }

            if (id != null) {
                var entity = internalFindById(id, ParticipantContext.class);
                if (entity == null) {
                    return success(List.of()); // el SPI dice "empty list" si no hay
                }
                return success(List.of(entity));
            }

            // Fallback: comportamiento actual (sin filtros SPARQL todavía)
            List<ParticipantContext> results = internalFindAll(querySpec, ParticipantContext.class)
                    .collect(Collectors.toList());

            return success(results);
        } catch (Exception ex) {
            return generalError("Failed querying ParticipantContexts: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------
    // CREATE
    // ------------------------------------------------------------

    @Override
    public StoreResult<Void> create(ParticipantContext participantContext) {
        Objects.requireNonNull(participantContext);

        var id = participantContext.getId();

        if (internalAskIfExistsById(id)) {
            return alreadyExists(alreadyExistsErrorMessage(id));
        }

        internalUpsert(id, participantContext);
        return success();
    }

    // ------------------------------------------------------------
    // UPDATE
    // ------------------------------------------------------------

    @Override
    public StoreResult<Void> update(ParticipantContext participantContext) {
        Objects.requireNonNull(participantContext);

        var id = participantContext.getId();

        if (!internalAskIfExistsById(id)) {
            return notFound(notFoundErrorMessage(id));
        }

        internalUpsert(id, participantContext);
        return success();
    }

    // ------------------------------------------------------------
    // DELETE
    // ------------------------------------------------------------

    @Override
    public StoreResult<Void> deleteById(String id) {
        if (!internalAskIfExistsById(id)) {
            return notFound(notFoundErrorMessage(id));
        }

        internalDelete(id);
        return success();
    }
}