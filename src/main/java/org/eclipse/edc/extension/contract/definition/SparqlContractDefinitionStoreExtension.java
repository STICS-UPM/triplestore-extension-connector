package org.eclipse.edc.extension.contract.definition;


import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.extension.common.sparql.ISparqlClient;
import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

@Extension("SPARQL ContractDefinitionStore")
@Provides(ContractDefinitionStore.class)
public class SparqlContractDefinitionStoreExtension implements ServiceExtension {

    @Inject
    private Monitor monitor;
    @Inject
    private TypeManager typeManager;

    private final static String CONTRACT_INDEX_GRAPH = "edc.triplestore.contractdefinitions.index.graph.iri";
    private final static String CONTRACT_BASE_GRAPH = "edc.triplestore.assets.graph.base.iri";
    private final static String CONTRACT_DEFAULT_INDEX_GRAPH = "edc.triplestore.contractdefinitions.graph.base.iri";
    private final static String CONTRACT_DEFAULT_BASE_GRAPH = "urn:edc:contractdefinition:";


    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        //var typeManager = context.getService(TypeManager.class);

        // Streamlined SPARQL
        ISparqlClient sparql = StreamlineClientBuilder.create(cfg, monitor);

        // Other RDF parameters
        String graphIri = cfg.getString(CONTRACT_INDEX_GRAPH,CONTRACT_DEFAULT_INDEX_GRAPH);
        String assetBaseIri = cfg.getString(CONTRACT_BASE_GRAPH, CONTRACT_DEFAULT_BASE_GRAPH);
        monitor.info("Using SPARQL ContractDefinition (graph=" + graphIri + ")");
        monitor.info("Using SPARQL ContractDefinition (uri=" + assetBaseIri + ")");

        context.registerService(ContractDefinitionStore.class, new SparqlContractDefinitionStore(sparql, typeManager, graphIri, assetBaseIri, monitor));
    }
}