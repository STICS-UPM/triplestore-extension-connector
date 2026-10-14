package org.eclipse.edc.extension.participant.identity;

import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SparqlParticipantIdentityResolver implements ParticipantIdentityResolver {

    private final SparqlParticipantIdentityMappingStore store;

    public SparqlParticipantIdentityResolver(SparqlParticipantIdentityMappingStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public @Nullable String getParticipantId(String participantContextId, String protocol) {
        if (participantContextId == null || protocol == null) {
            return null;
        }
        return store.getParticipantId(participantContextId, protocol);
    }
}
