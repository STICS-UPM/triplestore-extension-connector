package org.eclipse.edc.extension.common.store;

import org.apache.jena.vocabulary.RDF;

import java.util.Objects;

public class QueryBuilder {

    protected static final String EDC_NS = "https://w3id.org/edc/v0.0.1/ns/";
    protected static final String EDC_NS_ID = "https://w3id.org/edc/v0.0.1/ns/id";

    protected static final String VOCABULAY_SEDC_JSON11SERILIZATION_PROPERTY = "edc:json11Serialization";
    protected static final String VOCABULAY_SEDC_UNFOLDED_PROPERTY = "edc:unfoldedArtefact";

    protected final StoreEntity storeEntity;

    public QueryBuilder(StoreEntity storeEntity) {
       this.storeEntity = Objects.requireNonNull(storeEntity);
    }

    public String rdfType(){
        return "<" + EDC_NS + storeEntity.toString() + ">";
    }

    public StoreEntity getStoreEntity(){
        return this.storeEntity;
    }

    public static String findByIdQuery(String graphIri, String subject) {
        StringBuilder query = new StringBuilder();
        query.append("PREFIX edc: <").append(EDC_NS).append(">");
        query.append( "SELECT ?json WHERE {\n GRAPH <").append(graphIri).append("> {");
        query.append(" <").append(subject).append("> edc:json11Serialization ?json . ");
        query.append(" } } LIMIT 1 ");
        return query.toString();
    }


    public String deleteById(String graphIri, String subject) {
        StringBuilder query = new StringBuilder();
        query.append("PREFIX edc: <").append(EDC_NS).append(">");
        query.append(" WITH <").append(graphIri).append("> ");
        query.append(" DELETE { <").append(subject).append("> ?p ?o . } ");
        query.append(" WHERE  { <").append(subject).append("> ?p ?o . } ");
        return query.toString();
    }


    public String findAllJsonLd( String graphIri, Integer offset, Integer limit) {
        StringBuilder query = new StringBuilder();
        query.append("PREFIX edc: <").append(EDC_NS).append(">\n");
        query.append(" SELECT ?json WHERE { GRAPH <").append(graphIri).append("> { ?s edc:json11Serialization ?json .\n }");
        query.append(" } ");
        if (offset!=null)
            query.append(" OFFSET ").append(offset);
        if(limit!=null)
            query.append(" LIMIT ").append(limit);
        return query.toString();
    }

    /** Review methods **/

    public String buildUpdateQuery(String namedGraphIri, String subjectIri, String jsonEscaped) {
        StringBuilder queryUpdate = new StringBuilder();
        String metaPolicyIri = namedGraphIri+":"+subjectIri;

        queryUpdate.append("PREFIX edc: <").append(EDC_NS).append(">\n");
        queryUpdate.append("PREFIX rdf: <").append(RDF.uri).append(">\n");
        queryUpdate.append("WITH <").append(namedGraphIri).append(">\n");
        queryUpdate.append("DELETE {\n\t<").append(metaPolicyIri).append("> ?p ?o .\n}");
        queryUpdate.append("INSERT {  <").append(metaPolicyIri).append("> rdf:type ").append(rdfType()).append("; \n");
        //DONE: The id must be an IRI to link the meta-graph (control) with actual triples
        queryUpdate.append("\tedc:unfoldedArtefact  <").append(subjectIri).append("> ;\n");
        queryUpdate.append("\tedc:json11Serialization \"").append(jsonEscaped).append("\" .\n");

        queryUpdate.append("}  WHERE {  OPTIONAL { <").append(subjectIri).append("> ?p ?o . }  }");

        return queryUpdate.toString();
    }

    public String findById(String nameGraphIri, String entitySubject) {
        StringBuilder query = new StringBuilder();

        query.append("PREFIX edc: <").append(EDC_NS).append(">\n");
        query.append("PREFIX rdf: <").append(RDF.uri).append(">\n");
        query.append("SELECT ?json WHERE {\n");
        query.append("  GRAPH <").append(nameGraphIri).append("> {\n" );
        query.append("    <").append(entitySubject).append("> ").append(VOCABULAY_SEDC_JSON11SERILIZATION_PROPERTY).append(" ?json .\n");
        query.append("  }\n");
        query.append("} LIMIT 1");

        return query.toString();
    }

    public String findByIdASK(String nameGraphIri, String entitySubject) {
        StringBuilder query = new StringBuilder();

        query.append("PREFIX edc: <").append(EDC_NS).append(">\n");
        query.append("PREFIX rdf: <").append(RDF.uri).append(">\n");
        query.append("ASK {\n");
        query.append("  GRAPH <").append(nameGraphIri).append("> {\n" );
        query.append("    <").append(entitySubject).append("> ").append(VOCABULAY_SEDC_JSON11SERILIZATION_PROPERTY).append(" ?json .\n");
        query.append("  }\n");
        query.append("} LIMIT 1");

        return query.toString();
    }



}
