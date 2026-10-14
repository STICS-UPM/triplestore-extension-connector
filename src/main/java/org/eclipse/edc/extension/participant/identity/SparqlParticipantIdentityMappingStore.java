package org.eclipse.edc.extension.participant.identity;

import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.store.AbstractSparqlStore;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.TypeManager;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public class SparqlParticipantIdentityMappingStore extends AbstractSparqlStore<ParticipantIdentityMapping> {

    public SparqlParticipantIdentityMappingStore(ISparqlClient sparqlClient,
                                                 TypeManager typeManager,
                                                 String nameGraphIri,
                                                 String baseNsIri,
                                                 Monitor monitor) {
        super(sparqlClient, typeManager, nameGraphIri, baseNsIri, monitor, StoreEntity.PARTICIPANT_IDENTITY_MAPPING);
    }

    public void saveMapping(String participantContextId, String protocol, String participantId) {
        Objects.requireNonNull(participantContextId, "participantContextId");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(participantId, "participantId");

        var mapping = new ParticipantIdentityMapping(participantContextId, protocol, participantId);
        internalUpsert(compoundId(participantContextId, protocol), mapping);
    }

    public String getParticipantId(String participantContextId, String protocol) {
        Objects.requireNonNull(participantContextId, "participantContextId");
        Objects.requireNonNull(protocol, "protocol");

        var mapping = internalFindById(compoundId(participantContextId, protocol), ParticipantIdentityMapping.class);
        return mapping != null ? mapping.getParticipantId() : null;
    }

    /**
     * Deterministic ID for (participantContextId, protocol).
     * We Base64-url encode to avoid illegal IRI chars and keep it stable.
     */
    private static String compoundId(String participantContextId, String protocol) {
        var raw = participantContextId + "|" + protocol;
        var enc = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "mapping:" + enc; // will become urn:fake_id:mapping:... if no ":"; but it has ":" already
    }
}