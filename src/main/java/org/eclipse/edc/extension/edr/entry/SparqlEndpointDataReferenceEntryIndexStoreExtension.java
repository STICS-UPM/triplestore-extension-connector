package org.eclipse.edc.extension.edr.entry;

import org.eclipse.edc.edr.spi.store.EndpointDataReferenceEntryIndex;
import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

@Extension(value = "SPARQL EndpointDataReferenceEntryIndex")
@Provides(EndpointDataReferenceEntryIndex.class)
public class SparqlEndpointDataReferenceEntryIndexStoreExtension implements ServiceExtension {

    @Inject private Monitor monitor;

    private static final String EDR_ENTRY_INDEX_GRAPH = "edc.triplestore.edr.entry.index.graph.iri";
    private static final String EDR_ENTRY_BASE_IRI    = "edc.triplestore.edr.entry.index.graph.base.iri";

    private static final String DEFAULT_EDR_ENTRY_GRAPH = "urn:edc:graph:edr:entry:index";
    private static final String DEFAULT_EDR_ENTRY_BASE  = "urn:edc:edr:entry:";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        var typeManager = context.getService(TypeManager.class);

        // Streamlined SPARQL
        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        // RDF parameters
        String graphIri = cfg.getString(EDR_ENTRY_INDEX_GRAPH, DEFAULT_EDR_ENTRY_GRAPH);
        String baseIri  = cfg.getString(EDR_ENTRY_BASE_IRI, DEFAULT_EDR_ENTRY_BASE);

        monitor.info("Using SPARQL EndpointDataReferenceEntryIndex (graph=" + graphIri + ")");
        monitor.info("Using SPARQL EndpointDataReferenceEntryIndex baseIri (uri=" + baseIri + ")");

        var store = new SparqlEndpointDataReferenceEntryIndexStore(
                sparql,
                typeManager,
                graphIri,
                baseIri,
                monitor
        );

        context.registerService(EndpointDataReferenceEntryIndex.class, store);
    }
}