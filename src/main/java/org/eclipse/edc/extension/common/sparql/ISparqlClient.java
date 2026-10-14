package org.eclipse.edc.extension.common.sparql;

import java.time.Instant;
import java.util.stream.Stream;

public interface ISparqlClient {
    String sparqlSelect(String sparqlQuery);
    void sparqlUpdate(String sparqlUpdate);

    String literal(String value);
    String literalLongString(String value);
    String literal(long value);
    String literal(boolean value);
    String literalDateTime(Instant instant);

    String selectSingleLiteral(String query, String column);
    Stream<String> selectColumnAsStream(String query, String column);
}
