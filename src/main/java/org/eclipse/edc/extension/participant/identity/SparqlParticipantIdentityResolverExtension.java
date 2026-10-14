package org.eclipse.edc.extension.participant.identity;

import org.eclipse.edc.extension.common.sparql.StreamlineClientBuilder;
import org.eclipse.edc.participantcontext.spi.identity.ParticipantIdentityResolver;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;

@Extension(value = "SPARQL ParticipantIdentityResolver")
@Provides(ParticipantIdentityResolver.class)
public class SparqlParticipantIdentityResolverExtension implements ServiceExtension {

    @Inject private Monitor monitor;

    private static final String PID_GRAPH = "edc.triplestore.participant.identity.graph.iri";
    private static final String PID_BASE = "edc.triplestore.participant.identity.base.iri";

    private static final String PID_DEFAULT_GRAPH = "urn:edc:graph:participant:identity";
    private static final String PID_DEFAULT_BASE = "urn:edc:participant:identity:";

    @Override
    public void initialize(ServiceExtensionContext context) {
        var cfg = context.getConfig();
        var typeManager = context.getService(TypeManager.class);

        var sparql = StreamlineClientBuilder.create(cfg, monitor);

        var graphIri = cfg.getString(PID_GRAPH, PID_DEFAULT_GRAPH);
        var baseIri = cfg.getString(PID_BASE, PID_DEFAULT_BASE);

        monitor.info("Using SPARQL ParticipantIdentityMapping (graph=" + graphIri + ")");
        monitor.info("Using SPARQL ParticipantIdentityMapping baseIri (uri=" + baseIri + ")");

        var mappingStore = new SparqlParticipantIdentityMappingStore(
                sparql,
                typeManager,
                graphIri,
                baseIri,
                monitor
        );

        var resolver = new SparqlParticipantIdentityResolver(mappingStore);

        context.registerService(ParticipantIdentityResolver.class, resolver);

        // Opcional: exponer también el store como servicio interno si te interesa
        // context.registerService(SparqlParticipantIdentityMappingStore.class, mappingStore);
    }
}