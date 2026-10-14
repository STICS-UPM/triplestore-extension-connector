package org.eclipse.edc.extension.common.sparql;

import org.eclipse.edc.spi.monitor.Monitor;
import sparql.streamline.core.SparqlEndpointConfiguration;
import org.eclipse.edc.spi.system.configuration.Config;

public class StreamlineClientBuilder {

    private final static String QUERY_ENDPOINT = "edc.triplestore.sparql.query.endpoint";
    private final static String UPDATE_ENDPOINT = "edc.triplestore.sparql.update.endpoint";
    private final static String USERNAME_ENDPOINT = "edc.triplestore.auth.username";
    private final static String PASSWORD_ENDPOINT = "edc.triplestore.auth.password";


    private StreamlineClientBuilder(){
        super();
    }


    public static ISparqlClient create(Config globalConfiguration, Monitor monitor){
        // Extract configuration values
        String queryEndpoint  = globalConfiguration.getString(QUERY_ENDPOINT);
        String updateEndpoint = globalConfiguration.getString(UPDATE_ENDPOINT);
        // TODO: check that both endpoints are valid or throw exception
        String username = globalConfiguration.getString(USERNAME_ENDPOINT, null);
        String password = globalConfiguration.getString(PASSWORD_ENDPOINT, null);

        // Log
        monitor.info("Using SPARQL queryEndpoint (endpoint=" + queryEndpoint + ")");
        monitor.info("Using SPARQL updateEndpoint (endpoint=" + updateEndpoint + ")");

        // Build Sparql client configuration
        SparqlEndpointConfiguration configuration = new SparqlEndpointConfiguration();
        configuration.setEndpointQuery(queryEndpoint);
        configuration.setEndpointUpdate(updateEndpoint);
        configuration.setUsername(username);
        configuration.setPassword(password);
        // Build Sparql Client
        return new StreamlineSparqlClient(configuration, monitor);
    }

}
