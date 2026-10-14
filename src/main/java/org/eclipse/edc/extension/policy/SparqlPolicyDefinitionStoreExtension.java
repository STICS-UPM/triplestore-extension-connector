package org.eclipse.edc.extension.policy;


import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.extension.common.store.Vocabullary;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;


@Extension(value = "SPARQL PolicyDefinitionStore")
@Provides(PolicyDefinitionStore.class)
public class SparqlPolicyDefinitionStoreExtension implements ServiceExtension {

    @Inject private Monitor monitor;
    @Inject private TypeManager typeManager;
    private final static String POLICY_INDEX_GRAPH = "edc.triplestore.policy.index.graph.iri";
    private final static String POLICY_BASE_GRAPH = "edc.triplestore.policy.graph.base.iri";
    private final static String POLICY_DEFAULT_INDEX_GRAPH = "urn:edc:graph:policies";
    private final static String POLICY_DEFAULT_BASE_GRAPH = "urn:edc:policy:";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        //var typeManager = context.getService(TypeManager.class);

        // Streamlined SPARQL
        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        // Other RDF parameters
        String graphIri = cfg.getString(POLICY_INDEX_GRAPH,POLICY_DEFAULT_INDEX_GRAPH);
        String policyBaseIri = cfg.getString(POLICY_BASE_GRAPH, POLICY_DEFAULT_BASE_GRAPH);
        monitor.info("Using SPARQL PolicyDefinitionStore (graph=" + graphIri + ")");
        monitor.info("Using SPARQL policyBaseIri (uri=" + policyBaseIri + ")");
        Vocabullary vocabullary = new Vocabullary();
        // Store
        var store  = new SparqlPolicyDefinitionStore(
                sparql,
                typeManager,
                graphIri,
                policyBaseIri,
                monitor
        );

        context.registerService(PolicyDefinitionStore.class, store);
    }
}
