package org.eclipse.edc.extension.common.store;

public enum StoreEntity {
    ASSET("Asset"),
    POLICY("PolicyDefinition"),
    CONTRACT_DEFINITION("ContractDefinition"),
    CONTRACT_NEGOTIATION("ContractNegotiation"),
    CONTRACT_AGREEMENT("ContractAgreement"),
    TRANSFER_PROCESS("TransferProcess"),
    EDR_ENTRY("EndpointDataReferenceEntry"),
    EDR_CACHE("EndpointDataReferenceCache"),
    DATA_PLANE_INSTANCE("DataPlaneInstance"),
    // NEW:
    PARTICIPANT_CONTEXT("ParticipantContext"),
    PARTICIPANT_CONTEXT_CONFIG("ParticipantContextConfig"),
    // NEW2:
    PARTICIPANT_IDENTITY_MAPPING("ParticipantIdentityMapping");

    private final String typeName;

    StoreEntity(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public String toString() {
        return typeName;
    }
}
