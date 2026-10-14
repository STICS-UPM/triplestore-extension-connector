package org.eclipse.edc.extension.dataplane;

import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.extension.common.lease.SparqlLeaseManager;
import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.extension.common.store.Vocabullary;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

import java.time.Clock;
import java.time.Duration;

@Extension(value = "SPARQL DataPlaneInstanceStore")
@Provides(DataPlaneInstanceStore.class)
public class SparqlDataplaneInstanceStoreExtension implements ServiceExtension {

    @Inject private Monitor monitor;

    private static final String DPI_INDEX_GRAPH = "edc.triplestore.dataplane.instance.index.graph.iri";
    private static final String DPI_BASE_IRI = "edc.triplestore.dataplane.instance.base.iri";

    private static final String DPI_LEASE_DURATION = "edc.triplestore.dataplane.instance.lease.duration";
    private static final String DPI_LEASE_OWNER = "edc.triplestore.dataplane.instance.lease.owner";

    private static final String DPI_DEFAULT_INDEX_GRAPH = "urn:edc:graph:dataplane:instances";
    private static final String DPI_DEFAULT_BASE_IRI = "urn:edc:dataplane:instance:";

    private static final String DPI_DEFAULT_LEASE_DURATION = "PT60S"; // ISO-8601 duration string
    private static final String DPI_DEFAULT_LEASE_OWNER = "edc-runtime";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        var typeManager = context.getService(TypeManager.class);

        // SPARQL client (tu builder)
        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        // Graph IRIs
        var graphIri = cfg.getString(DPI_INDEX_GRAPH, DPI_DEFAULT_INDEX_GRAPH);
        var baseIri = cfg.getString(DPI_BASE_IRI, DPI_DEFAULT_BASE_IRI);

        // Lease config
        var leaseDuration = Duration.parse(cfg.getString(DPI_LEASE_DURATION, DPI_DEFAULT_LEASE_DURATION));
        var leaseOwner = cfg.getString(DPI_LEASE_OWNER, DPI_DEFAULT_LEASE_OWNER);

        monitor.info("Using SPARQL DataPlaneInstanceStore (graph=" + graphIri + ")");
        monitor.info("Using SPARQL DataPlaneInstanceStore baseIri (uri=" + baseIri + ")");
        monitor.info("Using SPARQL DataPlaneInstanceStore leaseDuration=" + leaseDuration);
        monitor.info("Using SPARQL DataPlaneInstanceStore leaseOwner=" + leaseOwner);

        // (No es estrictamente necesario aquí, pero lo mantengo por consistencia con tu estilo)
        Vocabullary vocabullary = new Vocabullary();

        // Lease manager
        var leaseManager = new SparqlLeaseManager(sparql, Clock.systemUTC());

        // Store (tu constructor)
        var store = new SparqlDataPlaneInstanceStore(
                sparql,
                typeManager,
                monitor,
                graphIri,
                baseIri,
                leaseManager,
                leaseDuration,
                leaseOwner
        );

        context.registerService(DataPlaneInstanceStore.class, store);
    }
}



