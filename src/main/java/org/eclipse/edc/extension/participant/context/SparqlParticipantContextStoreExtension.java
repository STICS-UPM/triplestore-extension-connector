package org.eclipse.edc.extension.participant.context;

import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

// IMPORTANT: adjust import to your EDC version package:
import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;

@Extension(value = "SPARQL ParticipantContextStore")
@Provides(ParticipantContextStore.class)
public class SparqlParticipantContextStoreExtension implements ServiceExtension {

    @Inject private Monitor monitor;

    private static final String PC_INDEX_GRAPH = "edc.triplestore.participantcontext.index.graph.iri";
    private static final String PC_BASE_IRI = "edc.triplestore.participantcontext.base.iri";
    private static final String PC_DEFAULT_INDEX_GRAPH = "urn:edc:graph:participantcontext";
    private static final String PC_DEFAULT_BASE_IRI = "urn:edc:participantcontext:";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        var typeManager = context.getService(TypeManager.class);

        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        var graphIri = cfg.getString(PC_INDEX_GRAPH, PC_DEFAULT_INDEX_GRAPH);
        var baseIri = cfg.getString(PC_BASE_IRI, PC_DEFAULT_BASE_IRI);

        monitor.info("Using SPARQL ParticipantContextStore (graph=" + graphIri + ")");
        monitor.info("Using SPARQL ParticipantContextStore baseIri (uri=" + baseIri + ")");

        var store = new SparqlParticipantContextStore(
                sparql,
                typeManager,
                graphIri,
                baseIri,
                monitor
        );

        context.registerService(ParticipantContextStore.class, store);
    }
}