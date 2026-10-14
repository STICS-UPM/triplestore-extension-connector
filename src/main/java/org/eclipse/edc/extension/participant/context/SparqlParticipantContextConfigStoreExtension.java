package org.eclipse.edc.extension.participant.context;

import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.participantcontext.spi.config.store.ParticipantContextConfigStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;


@Extension(value = "SPARQL ParticipantContextConfigStore")
@Provides(ParticipantContextConfigStore.class)
public class SparqlParticipantContextConfigStoreExtension implements ServiceExtension {

    @Inject private Monitor monitor;

    private static final String PCC_INDEX_GRAPH = "edc.triplestore.participantcontext.config.index.graph.iri";
    private static final String PCC_BASE_IRI = "edc.triplestore.participantcontext.config.base.iri";
    private static final String PCC_DEFAULT_INDEX_GRAPH = "urn:edc:graph:participantcontext:config";
    private static final String PCC_DEFAULT_BASE_IRI = "urn:edc:participantcontext:config:";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        var typeManager = context.getService(TypeManager.class);

        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        var graphIri = cfg.getString(PCC_INDEX_GRAPH, PCC_DEFAULT_INDEX_GRAPH);
        var baseIri = cfg.getString(PCC_BASE_IRI, PCC_DEFAULT_BASE_IRI);

        monitor.info("Using SPARQL ParticipantContextConfigStore (graph=" + graphIri + ")");
        monitor.info("Using SPARQL ParticipantContextConfigStore baseIri (uri=" + baseIri + ")");

        var store = new SparqlParticipantContextConfigStore(
                sparql,
                typeManager,
                graphIri,
                baseIri,
                monitor
        );

        context.registerService(ParticipantContextConfigStore.class, store);
    }
}
