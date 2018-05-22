package io.bdrc.budademodata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.RiotException;

public class Upload {
    public static final String RESOURCE_PREFIX = "http://purl.bdrc.io/resource/";
    public static final String CORE_PREFIX = "http://purl.bdrc.io/ontology/core/";
    public static final String CONTEXT_URL = "http://purl.bdrc.io/context.jsonld";
    public static final String ADMIN_PREFIX = "http://purl.bdrc.io/ontology/admin/";
    public static final String DATA_PREFIX = "http://purl.bdrc.io/data/";
    public static final String SKOS_PREFIX = "http://www.w3.org/2004/02/skos/core#";
    public static final String VCARD_PREFIX = "http://www.w3.org/2006/vcard/ns#";
    public static final String TBR_PREFIX = "http://purl.bdrc.io/ontology/toberemoved/";
    public static final String OWL_PREFIX = "http://www.w3.org/2002/07/owl#";
    public static final String RDF_PREFIX = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String RDFS_PREFIX = "http://www.w3.org/2000/01/rdf-schema#";
    public static final String XSD_PREFIX = "http://www.w3.org/2001/XMLSchema#";
    
    //public static final String FusekiBaseUrl = "http://buda1.bdrc.io:13180/fuseki/bdrcrw";
    public static final String FusekiBaseUrl = "http://localhost:13180/fuseki/bdrcrw";
    public static final String FusekiDataUrl = FusekiBaseUrl+"/data";
    public static final String FusekiQueryUrl = FusekiBaseUrl+"/query";
    public static RDFConnection fuConn;
    public static final String graphName = "http://purl.bdrc.io/tmp/budademodata";
    
    static {
        init();
    }
    
    public static void init() {
        //final RDFConnection fuConn = RDFConnectionFactory.connect(FusekiBaseUrl, FusekiQueryUrl, FusekiDataUrl, FusekiDataUrl);
        fuConn = RDFConnectionFuseki.create()
                .destination(FusekiBaseUrl)
                .queryEndpoint(FusekiQueryUrl)
                .gspEndpoint(FusekiDataUrl)
                .updateEndpoint(FusekiDataUrl)
                .build();
    }
    
    public static void setPrefixes(Model m) {
        m.setNsPrefix("", CORE_PREFIX);
        m.setNsPrefix("adm", ADMIN_PREFIX);
        m.setNsPrefix("bdr", RESOURCE_PREFIX);
        m.setNsPrefix("tbr", TBR_PREFIX);
        m.setNsPrefix("owl", OWL_PREFIX);
        m.setNsPrefix("rdf", RDF_PREFIX);
        m.setNsPrefix("rdfs", RDFS_PREFIX);
        m.setNsPrefix("skos", SKOS_PREFIX);
        m.setNsPrefix("xsd", XSD_PREFIX);
    }
    
    public static void addPathToDataset(final Graph g, final Path p) {
        try {
            RDFParserBuilder pb = RDFParser.create()
                     .source(p)
                     .lang(RDFLanguages.TTL);
                     //.canonicalLiterals(true);
            pb.parse(g);
            System.out.println("add "+p);
        } catch (RiotException e) {
            System.err.println("error reading "+p);
            return;
        }
    }
    
    public static void main(String [] args) throws IOException {
        System.out.println("transfer files to "+FusekiBaseUrl);
        final Dataset ds = DatasetFactory.create();
        final Model m = ModelFactory.createDefaultModel();
        setPrefixes(m);
        final Graph g = m.getGraph();
        final Stream<Path> pathStream =  Files.find(Paths.get("src/main/resources"), 5, (p, bfa) -> bfa.isRegularFile());
        pathStream.forEach(p -> addPathToDataset(g, p));
        pathStream.close();
        ds.addNamedModel(graphName, m);
        try {
            fuConn.delete(graphName);
        } catch (HttpException e) {
            System.out.println("didn't find graph "+graphName);
        }
        fuConn.putDataset(ds);
        fuConn.commit();
        fuConn.end();
        fuConn.close();
        System.out.println("things went smoothly");
    }
}
