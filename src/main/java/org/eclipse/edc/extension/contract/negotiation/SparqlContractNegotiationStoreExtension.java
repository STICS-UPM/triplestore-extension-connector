package org.eclipse.edc.extension.contract.negotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.extension.common.lease.SparqlLeaseManager;
import org.eclipse.edc.extension.common.lease.SparqlStoreLease;
import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.extension.common.store.StoreEntity;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import java.time.Clock;
import java.time.Duration;

@Extension(value = "SPARQL ContractNegotiationStore")
@Provides(ContractNegotiationStore.class)
public class SparqlContractNegotiationStoreExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;
    @Inject
    private TypeManager typeManager;

    // ==== Negotiations (leased) ====
    private static final String NEGOTIATION_INDEX_GRAPH = "edc.triplestore.contract.negotiation.index.graph.iri";
    private static final String NEGOTIATION_BASE_IRI = "edc.triplestore.contract.negotiation.graph.base.iri";
    private static final String NEGOTIATION_DEFAULT_INDEX_GRAPH = "urn:edc:graph:contractnegotiations";
    private static final String NEGOTIATION_DEFAULT_BASE_IRI = "urn:edc:contractnegotiation:";

    // ==== Agreements (non-leased, stored separately as blobs) ====
    private static final String AGREEMENT_INDEX_GRAPH = "edc.triplestore.contract.agreement.index.graph.iri";
    private static final String AGREEMENT_BASE_IRI = "edc.triplestore.contract.agreement.graph.base.iri";
    private static final String AGREEMENT_DEFAULT_INDEX_GRAPH = "urn:edc:graph:contractagreements";
    private static final String AGREEMENT_DEFAULT_BASE_IRI = "urn:edc:ns:contractagreement:";

    // ==== Lease settings ====
    private static final String LEASE_OWNER = "edc.triplestore.lease.owner";
    private static final String LEASE_DURATION = "edc.triplestore.lease.duration";
    private static final String LEASE_DEFAULT_OWNER = "edc-runtime";
    private static final String LEASE_DEFAULT_DURATION = "PT60S"; // ISO-8601 duration

    // If your SparqlLeaseManager uses a base IRI for lease IRIs, keep it configurable:
    private static final String LEASE_BASE_IRI = "edc.triplestore.lease.base.iri";
    private static final String LEASE_DEFAULT_BASE_IRI = "urn:edc:ns:lease:";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        var typeManager = context.getService(TypeManager.class);

        // Streamlined SPARQL
        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        // Graphs / base IRIs
        String negotiationGraphIri = cfg.getString(NEGOTIATION_INDEX_GRAPH, NEGOTIATION_DEFAULT_INDEX_GRAPH);
        String negotiationBaseIri = cfg.getString(NEGOTIATION_BASE_IRI, NEGOTIATION_DEFAULT_BASE_IRI);

        String agreementGraphIri = cfg.getString(AGREEMENT_INDEX_GRAPH, AGREEMENT_DEFAULT_INDEX_GRAPH);
        String agreementBaseIri = cfg.getString(AGREEMENT_BASE_IRI, AGREEMENT_DEFAULT_BASE_IRI);

        // Lease settings
        String leaseOwner = cfg.getString(LEASE_OWNER, LEASE_DEFAULT_OWNER);
        String leaseDurationStr = cfg.getString(LEASE_DURATION, LEASE_DEFAULT_DURATION);
        String leaseBaseIri = cfg.getString(LEASE_BASE_IRI, LEASE_DEFAULT_BASE_IRI);

        var leaseDuration = Duration.parse(leaseDurationStr);
        var clock = Clock.systemUTC();

        monitor.info("Using SPARQL ContractNegotiationStore (graph=" + negotiationGraphIri + ")");
        monitor.info("Using SPARQL negotiationBaseIri (uri=" + negotiationBaseIri + ")");
        monitor.info("Using SPARQL ContractAgreement store (graph=" + agreementGraphIri + ")");
        monitor.info("Using SPARQL agreementBaseIri (uri=" + agreementBaseIri + ")");
        monitor.info("Using SPARQL leasing (owner=" + leaseOwner + ", duration=" + leaseDuration + ", leaseBaseIri=" + leaseBaseIri + ")");

        // Lease manager
        var leaseManager = new SparqlLeaseManager(sparql, clock);

        // Leased store for negotiations (stores state/lease fields in the same index graph)
        var negotiationStore = new SparqlStoreLease<ContractNegotiation>(
                sparql,
                typeManager,
                negotiationGraphIri,
                negotiationBaseIri,
                monitor,
                StoreEntity.CONTRACT_NEGOTIATION
        );

        // Agreement store (can be plain AbstractSparqlStore too; using SparqlStoreLease is fine if it supports non-leased usage)
        var agreementStore = new SparqlStoreLease<ContractAgreement>(
                sparql,
                typeManager,
                agreementGraphIri,
                agreementBaseIri,
                monitor,
                StoreEntity.CONTRACT_AGREEMENT
        );

        // Final store
        var store = new SparqlContractNegotiationStore(
                negotiationStore,
                agreementStore,
                leaseManager,
                typeManager,
                clock,
                negotiationGraphIri, // leaseGraphIri: leasing triples are stored here in your design
                leaseBaseIri,
                leaseDuration,
                leaseOwner
        );

        context.registerService(ContractNegotiationStore.class, store);
    }
}