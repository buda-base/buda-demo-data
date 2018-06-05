package io.bdrc.budademodata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.graph.Graph;
import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.Restriction;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RiotException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL2;

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
    public static final String FusekiBaseUrl = "http://buda1.bdrc.io:13180/fuseki/bdrcrw";
    public static final String FusekiDataUrl = FusekiBaseUrl+"/data";
    public static final String FusekiQueryUrl = FusekiBaseUrl+"/query";
    public static RDFConnection fuConn;
    public static final String graphName = "http://purl.bdrc.io/tmp/budademodata";
    public static Reasoner bdrcReasoner = null;
    
    static {
        init();
    }
    
    public static Model getOntologyBaseModel() {
        Model res;
        try {
            ClassLoader classLoader = Upload.class.getClassLoader();
            InputStream inputStream = classLoader.getResourceAsStream("owl-schema/bdrc.owl");
            res = ModelFactory.createDefaultModel();
            res.read(inputStream, "", "RDF/XML");
            inputStream.close();
        } catch (Exception e) {
            System.err.println("Error reading ontology file");
            return null;
        }
        return res;
    }
    
    // change Range Datatypes from rdf:PlainLitteral to rdf:langString
    public static void rdf10tordf11(OntModel o) {
        Resource RDFPL = o.getResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#PlainLiteral");
        Resource RDFLS = o.getResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString");
        ExtendedIterator<DatatypeProperty> it = o.listDatatypeProperties();
        while(it.hasNext()) {
            DatatypeProperty p = it.next();
            if (p.hasRange(RDFPL)) {
                p.removeRange(RDFPL);
                p.addRange(RDFLS);
            }
        }
        ExtendedIterator<Restriction> it2 = o.listRestrictions();
        while(it2.hasNext()) {
            Restriction r = it2.next();
            Statement s = r.getProperty(OWL2.onDataRange); // is that code obvious? no
            if (s != null && s.getObject().asResource().equals(RDFPL)) {
                s.changeObject(RDFLS);

            }
        }
    }
    
    public static OntModel getOntologyModel(Model baseModel) {
        OntModel ontoModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, baseModel);
        rdf10tordf11(ontoModel);
        return ontoModel;
    }
    
    public static void init() {
        //final RDFConnection fuConn = RDFConnectionFactory.connect(FusekiBaseUrl, FusekiQueryUrl, FusekiDataUrl, FusekiDataUrl);
        fuConn = RDFConnectionFuseki.create()
                .destination(FusekiBaseUrl)
                .queryEndpoint(FusekiQueryUrl)
                .gspEndpoint(FusekiDataUrl)
                .updateEndpoint(FusekiDataUrl)
                .build();
        Model baseModel = getOntologyBaseModel(); 
        OntModel ontModel = getOntologyModel(baseModel);
        bdrcReasoner = BDRCReasoner.getReasoner(ontModel);
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
    
    public static void addPathToGraph(final Graph g, final Path p) {
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
        final Stream<Path> pathStream =  Files.find(Paths.get("src/main/resources/files/"), 5, (p, bfa) -> bfa.isRegularFile());
        pathStream.forEach(p -> addPathToGraph(g, p));
        pathStream.close();
        final Model infModel = ModelFactory.createInfModel(bdrcReasoner, m);
        //RDFWriter.create().source(infModel.getGraph()).lang(Lang.TTL).build().output(System.out);
        ds.addNamedModel(graphName, infModel);
        RDFWriter.create().source(ds).lang(Lang.TRIG).build().output(System.out);
        try {
            fuConn.delete(graphName);
        } catch (HttpException e) {
            System.out.println("didn't find graph "+graphName);
        }
        fuConn.put(graphName, infModel);
        //fuConn.putDataset(ds);
        fuConn.commit();
        fuConn.end();
        fuConn.close();
        System.out.println("things went smoothly");
    }
}
