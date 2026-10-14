package org.eclipse.edc.extension.participant.identity;

public class ParticipantIdentityMapping {

    private String participantContextId;
    private String protocol;
    private String participantId;

    public ParticipantIdentityMapping() {
    }

    public ParticipantIdentityMapping(String participantContextId, String protocol, String participantId) {
        this.participantContextId = participantContextId;
        this.protocol = protocol;
        this.participantId = participantId;
    }

    public String getParticipantContextId() {
        return participantContextId;
    }

    public void setParticipantContextId(String participantContextId) {
        this.participantContextId = participantContextId;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }
}
