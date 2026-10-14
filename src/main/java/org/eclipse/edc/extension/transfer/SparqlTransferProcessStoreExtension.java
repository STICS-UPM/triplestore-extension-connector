package org.eclipse.edc.extension.transfer;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
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

@Extension(value = "SPARQL TransferProcessStore")
@Provides(TransferProcessStore.class)
public class SparqlTransferProcessStoreExtension implements ServiceExtension {

    @Inject private Monitor monitor;
    @Inject private TypeManager typeManager;

    private static final String TRANSFER_INDEX_GRAPH = "edc.triplestore.transfer.index.graph.iri";
    private static final String TRANSFER_BASE_IRI = "edc.triplestore.transfer.graph.base.iri";
    private static final String TRANSFER_DEFAULT_INDEX_GRAPH = "urn:edc:graph:transferprocesses";
    private static final String TRANSFER_DEFAULT_BASE_IRI = "urn:edc:transferprocess:";

    private static final String LEASE_OWNER = "edc.triplestore.lease.owner";
    private static final String LEASE_DURATION = "edc.triplestore.lease.duration";
    private static final String LEASE_BASE_IRI = "edc.triplestore.lease.base.iri";

    private static final String LEASE_DEFAULT_OWNER = "edc-runtime";
    private static final String LEASE_DEFAULT_DURATION = "PT60S";
    private static final String LEASE_DEFAULT_BASE_IRI = "urn:edc:lease:";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        //var typeManager = context.getService(TypeManager.class);

        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        String graphIri = cfg.getString(TRANSFER_INDEX_GRAPH, TRANSFER_DEFAULT_INDEX_GRAPH);
        String baseIri = cfg.getString(TRANSFER_BASE_IRI, TRANSFER_DEFAULT_BASE_IRI);

        String leaseOwner = cfg.getString(LEASE_OWNER, LEASE_DEFAULT_OWNER);
        String leaseDurationStr = cfg.getString(LEASE_DURATION, LEASE_DEFAULT_DURATION);
        String leaseBaseIri = cfg.getString(LEASE_BASE_IRI, LEASE_DEFAULT_BASE_IRI);

        var leaseDuration = Duration.parse(leaseDurationStr);
        var clock = Clock.systemUTC();

        monitor.info("Using SPARQL TransferProcessStore (graph=" + graphIri + ")");
        monitor.info("Using SPARQL transferBaseIri (uri=" + baseIri + ")");
        monitor.info("Using SPARQL leasing (owner=" + leaseOwner + ", duration=" + leaseDuration + ", leaseBaseIri=" + leaseBaseIri + ")");

        var leaseManager = new SparqlLeaseManager(sparql, clock);

        var storeLease = new SparqlStoreLease<TransferProcess>(
                sparql,
                typeManager,
                graphIri,
                baseIri,
                monitor,
                StoreEntity.TRANSFER_PROCESS   // <-- add this enum value (see note below)
        );

        var store = new SparqlTransferProcessStore(
                storeLease,
                leaseManager,
                typeManager,
                clock,
                graphIri,        // leaseGraphIri = same graph in your design
                leaseBaseIri,
                leaseDuration,
                leaseOwner
        );

        context.registerService(TransferProcessStore.class, store);
    }
}
