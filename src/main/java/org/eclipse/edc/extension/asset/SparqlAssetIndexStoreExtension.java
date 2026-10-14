package org.eclipse.edc.extension.asset;


import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

@Extension("SPARQL AssetIndex")
@Provides(AssetIndex.class)
public class SparqlAssetIndexStoreExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;
    @Inject
    private TypeManager typeManager;

    private final static String ASSET_INDEX_GRAPH = "edc.triplestore.assets.index.graph.iri";
    private final static String ASSET_BASE_GRAPH = "edc.triplestore.assets.graph.base.iri";
    private final static String ASSET_DEFAULT_INDEX_GRAPH = "urn:edc:index:assets";
    private final static String ASSET_DEFAULT_BASE_GRAPH = "urn:edc:asset:";


    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();

        // Streamlined SPARQL
        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        // Other RDF parameters
        String graphIri = cfg.getString(ASSET_INDEX_GRAPH,ASSET_DEFAULT_INDEX_GRAPH);
        String assetBaseIri = cfg.getString(ASSET_BASE_GRAPH, ASSET_DEFAULT_BASE_GRAPH);
        monitor.info("Using SPARQL AssetIndex (graph=" + graphIri + ")");
        monitor.info("Using SPARQL AssetIndex (uri=" + assetBaseIri + ")");

        context.registerService(AssetIndex.class, new SparqlAssetIndexStore(sparql, typeManager, graphIri, assetBaseIri, monitor));
    }
}
