package org.eclipse.edc.extension.edr.cache;

import org.eclipse.edc.edr.spi.store.EndpointDataReferenceCache;
import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

@Extension(value = "SPARQL EndpointDataReferenceCache")
@Provides(EndpointDataReferenceCache.class)
public class SparqlEndpointDataReferenceCacheStoreExtension implements ServiceExtension {

    @Inject private Monitor monitor;

    private static final String EDR_CACHE_INDEX_GRAPH = "edc.triplestore.edr.cache.graph.iri";
    private static final String EDR_CACHE_BASE_IRI    = "edc.triplestore.edr.cache.graph.base.iri";

    private static final String DEFAULT_EDR_CACHE_GRAPH = "urn:edc:graph:edr:cache";
    private static final String DEFAULT_EDR_CACHE_BASE  = "urn:edc:edr:cache:";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        var typeManager = context.getService(TypeManager.class);

        // Streamlined SPARQL
        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        // RDF parameters
        String graphIri = cfg.getString(EDR_CACHE_INDEX_GRAPH, DEFAULT_EDR_CACHE_GRAPH);
        String baseIri  = cfg.getString(EDR_CACHE_BASE_IRI, DEFAULT_EDR_CACHE_BASE);

        monitor.info("Using SPARQL EndpointDataReferenceCache (graph=" + graphIri + ")");
        monitor.info("Using SPARQL EndpointDataReferenceCache baseIri (uri=" + baseIri + ")");

        var store = new SparqlEndpointDataReferenceCacheStore(
                sparql,
                typeManager,
                graphIri,
                baseIri,
                monitor
        );

        context.registerService(EndpointDataReferenceCache.class, store);
    }
}