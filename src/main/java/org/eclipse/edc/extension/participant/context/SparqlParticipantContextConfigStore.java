package org.eclipse.edc.extension.participant.context;

import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.store.ParticipantContextConfigStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;

import java.util.Objects;

import static org.eclipse.edc.spi.result.StoreResult.success;

public class SparqlParticipantContextConfigStore
        extends AbstractSparqlStore<ParticipantContextConfiguration>
        implements ParticipantContextConfigStore {

    public SparqlParticipantContextConfigStore(ISparqlClient sparqlClient,
                                               TypeManager typeManager,
                                               String graphIri,
                                               String baseNsIri,
                                               Monitor monitor) {
        super(sparqlClient, typeManager, graphIri, baseNsIri, monitor, StoreEntity.PARTICIPANT_CONTEXT_CONFIG);
    }

    @Override
    public void save(ParticipantContextConfiguration config) {
        Objects.requireNonNull(config);
        internalUpsert(config.getParticipantContextId(), config);
    }

    @Override
    public ParticipantContextConfiguration get(String participantContextId) {
        return internalFindById(participantContextId, ParticipantContextConfiguration.class);
    }
}