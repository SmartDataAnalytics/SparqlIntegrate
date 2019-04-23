package org.aksw.sparql_integrate.cli;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.aksw.jena_sparql_api.core.SparqlService;
import org.aksw.jena_sparql_api.server.utils.FactoryBeanSparqlServer;
import org.aksw.jena_sparql_api.sparql.ext.fs.JenaExtensionFs;
import org.aksw.jena_sparql_api.sparql.ext.http.JenaExtensionHttp;
import org.aksw.jena_sparql_api.sparql.ext.util.JenaExtensionUtil;
import org.aksw.jena_sparql_api.stmt.SPARQLResultSink;
import org.aksw.jena_sparql_api.stmt.SPARQLResultSinkQuads;
import org.aksw.jena_sparql_api.stmt.SPARQLResultVisitor;
import org.aksw.jena_sparql_api.stmt.SparqlStmt;
import org.aksw.jena_sparql_api.stmt.SparqlStmtParserImpl;
import org.aksw.jena_sparql_api.stmt.SparqlStmtUtils;
import org.aksw.jena_sparql_api.update.FluentSparqlService;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jena.atlas.lib.Sink;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriterRegistry;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.modify.request.UpdateModify;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.update.Update;
import org.apache.jena.update.UpdateRequest;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

@SpringBootApplication
public class MainCliSparqlIntegrate {

	private static final Logger logger = LoggerFactory.getLogger(MainCliSparqlIntegrate.class);

	public static class SPARQLResultVisitorSelectJsonOutput
		implements SPARQLResultSink
	{
		protected JsonArray arr;
		protected int maxDepth = 3;
		protected Gson gson;
		
		public SPARQLResultVisitorSelectJsonOutput() {
			this(null, null, null);
		}

		public SPARQLResultVisitorSelectJsonOutput(Gson gson) {
			this(null, null, gson);
		}
		
		public SPARQLResultVisitorSelectJsonOutput(JsonArray arr, Integer maxDepth, Gson gson) {
			super();
			this.arr = arr != null ? arr : new JsonArray();
			this.maxDepth = maxDepth != null ? maxDepth : 3;
			this.gson = gson != null ? gson : new Gson();
		}

		@Override
		public void onResultSet(ResultSet value) {
			JsonElement json = toJson(value, maxDepth);
			onJson(json);
		}

		@Override
		public void onJson(JsonElement value) {
			//String str = gson.toJson(value);
			arr.add(value);
		}

		@Override
		public void onQuad(Quad value) {
			System.err.println(value);
		}

		@Override
		public void close() throws Exception {
			// Return the last item of the json array
			JsonElement tmp = arr.size() == 0
					? new JsonObject()
					: arr.get(arr.size() - 1);
//			JsonElement tmp = arr.size() != 1
//					? arr
//					: arr.get(0);

			String str = gson.toJson(tmp);
			System.out.println(str);
		}


		@Override
		public void flush() {
		}
	}
	
	
	public static JsonElement toJson(RDFNode rdfNode, int maxDepth) {
		JsonElement result = toJson(rdfNode, 0, maxDepth);
		return result;
	}

	public static JsonObject toJson(Resource rdfNode, int maxDepth) {
		JsonElement tmp = toJson(rdfNode, 0, maxDepth);
		JsonObject result = tmp.getAsJsonObject();
		return result;
	}
	
	public static JsonArray toJson(ResultSet rs, int maxDepth) {
		JsonArray result = new JsonArray();
		List<String> vars = rs.getResultVars();
		while(rs.hasNext()) {
			JsonObject row = new JsonObject();
			QuerySolution qs = rs.next();
			for(String var : vars) {
				RDFNode rdfNode = qs.get(var);
				JsonElement jsonElement = toJson(rdfNode, maxDepth);
				row.add(var, jsonElement);
			}
			result.add(row);
		}
		
		return result;
	}
	
	public static JsonElement toJson(RDFNode rdfNode, int depth, int maxDepth) {
		JsonElement result;

		if(depth >= maxDepth) {
			// TODO We could add properties indicating that data was cut off here
			result = new JsonObject();
		} else if(rdfNode == null) { 
			result = JsonNull.INSTANCE;
		} else if(rdfNode.isLiteral()) {
			Node node = rdfNode.asNode();
			Object obj = node.getLiteralValue();
			//boolean isNumber =//NodeMapperRdfDatatype.canMapCore(node, Number.class);
			//if(isNumber) {
			if(obj instanceof Number) {
				Number value = (Number)obj; //NodeMapperRdfDatatype.toJavaCore(node, Number.class);
//				Literal literal = rdfNode.asLiteral();
				result = new JsonPrimitive(value);	
			} else if(obj instanceof String) {
				String value = (String)obj;
				result = new JsonPrimitive(value);
			} else {
				throw new RuntimeException("Unsupported literal: " + rdfNode);
			}
		} else if(rdfNode.isResource()) {
			JsonObject tmp = new JsonObject();
			Resource r = rdfNode.asResource();
			
			if(r.isURIResource()) {
				tmp.addProperty("id", r.getURI());
			} else if(r.isAnon()) {
				tmp.addProperty("id", r.getId().getLabelString());				
			}
			
			List<Statement> stmts = r.listProperties().toList();
			
			Map<Property, List<RDFNode>> pos = stmts.stream()
					.collect(Collectors.groupingBy(Statement::getPredicate,
							Collectors.mapping(Statement::getObject, Collectors.toList())));

			for(Entry<Property, List<RDFNode>> e : pos.entrySet()) {
				JsonArray arr = new JsonArray();
				Property p = e.getKey();
				String k = p.getLocalName();
				
				for(RDFNode o : e.getValue()) {
					JsonElement v = toJson(o, depth + 1, maxDepth);
					arr.add(v);
				}
				
				tmp.add(k, arr);
			}
			result = tmp;
		} else {
			throw new RuntimeException("Unknown node type: " + rdfNode);
		}
		
		return result;
	}
	
	
	public static Model parseTurtleAgainstModel(Model model, PrefixMapping prefixMapping, InputStream in) {
		// Add namespaces from the spec
		// Apparently Jena does not support parsing against
		// namespace prefixes previously declared in the target model
		// Therefore we serialize the prefix declarations and prepend them to the
		// input stream of the dataset		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Model tmp = ModelFactory.createDefaultModel();
		tmp.setNsPrefixes(prefixMapping);
		RDFDataMgr.write(baos, tmp, Lang.TURTLE);
		
		InputStream combined = new SequenceInputStream(
				new ByteArrayInputStream(baos.toByteArray()), in);
		
		RDFDataMgr.read(model, combined, Lang.TURTLE);
		
		return model;
	}

	@Configuration
	public static class ConfigSparqlIntegrate {

		@Bean
		public ApplicationRunner applicationRunner() {
			return (args) -> {
				Gson gson = new GsonBuilder().setPrettyPrinting().create();

				if(false)
				{
					Model model = RDFDataMgr.loadModel("/home/raven/Projects/limbo/git/claus-playground-dataset/04-dcat-sparql/target/release.dcat.ttl");
					//RDFDataMgr.write(System.out, model, RDFFormat.JSONLD);
					
					try(QueryExecution qe = QueryExecutionFactory.create("SELECT ?ds ?dist { ?ds <http://www.w3.org/ns/dcat#distribution> ?dist }", model)) {
						ResultSet rs = qe.execSelect();
						JsonArray jsonElement = toJson(rs, 3);
//						while(rs.hasNext()) {
//							JsonElement jsonElement = toJson(rs.next().get("s"), 3);
//							String str = gson.toJson(jsonElement);
//							
//							System.out.println(str);
//						}
						String str = gson.toJson(jsonElement);
						
						System.out.println(str);

						//String str = ResultSetFormatter..asText(rs);
						//System.out.println(str);
//						JsonLDWriter writer = new JsonLDWriter(RDFFormat.JSONLD_PRETTY);
//						writer.wri
					}
					if(true) { return; };
				}
				
				Collection<RDFFormat> available = RDFWriterRegistry.registered();
				String tmpOutFormat = Optional.ofNullable(args.getOptionValues("w"))
						.orElse(Collections.emptyList()).stream()
						.findFirst().orElse(null);

				String optOutFormat = args.containsOption("jq")
						? "jq"
						: tmpOutFormat;
				
				// TODO Maybe show algebra is better for trace logging?
				boolean showAlgebra = args.containsOption("a");
				
				SPARQLResultSink sink;
				RDFFormat outFormat = null;
				if(optOutFormat != null) {
					if(optOutFormat.equals("jq")) {
						sink = new SPARQLResultVisitorSelectJsonOutput(gson);
					} else {
				        outFormat = available.stream()
				        		.filter(f -> f.toString().equalsIgnoreCase(optOutFormat))
				        		.findFirst()
								.orElseThrow(() -> new RuntimeException("Unknown format: " + optOutFormat + " Available: " + available));
				        
						Sink<Quad> quadSink = SparqlStmtUtils.createSink(outFormat, System.out);
						sink = new SPARQLResultSinkQuads(quadSink);
					}			        
				} else {
					Sink<Quad> quadSink = SparqlStmtUtils.createSink(outFormat, System.out);
					sink = new SPARQLResultSinkQuads(quadSink);
				}
//				System.out.println(outFormat);
				
				//SPARQLResultVisitor
				//Sink<Quad> sink = SparqlStmtUtils.createSink(outFormat, System.out);
				

				// TODO Replace with our RDFDataMgrEx 
				
				PrefixMapping pm = new PrefixMappingImpl();
				pm.setNsPrefixes(PrefixMapping.Extended);
				pm.setNsPrefix("afn", "http://jena.apache.org/ARQ/function#");
				JenaExtensionUtil.addPrefixes(pm);

				JenaExtensionHttp.addPrefixes(pm);

				// Extended SERVICE <> keyword implementation
				JenaExtensionFs.registerFileServiceHandler();
				
				// System.out.println("ARGS: " + args.getOptionNames());
				Dataset dataset = DatasetFactory.create();
				RDFConnection conn = RDFConnectionFactory.connect(dataset);

				List<String> filenames = args.getNonOptionArgs();//args.getOptionValues("sparql");
				if (filenames == null || filenames.isEmpty()) {
					throw new RuntimeException(
							"No SPARQL files specified.");
				}
				
				Stopwatch sw = Stopwatch.createStarted();

				Path cwd = null;

				String cwdKey = "cwd=";
				String cwdResetCwd = "cwd";

				for (String filename : filenames) {
					logger.info("Processing item " + filename);
					// Determine the current working directory
//					System.out.println(filename);
//					System.out.println("foo: " + SparqlStmtUtils.extractBaseIri(filename));

					// Just use class path resources for pre-configured queries
//					if(filename.equals("emit")) {
//						processSparqlStmtWrapper(conn, new SparqlStmtQuery("CONSTRUCT WHERE { ?s ?p ?o }"), sink::send);
//					} else 
					if(filename.startsWith(cwdKey)) {
						String cwdValue = filename.substring(cwdKey.length()).trim();

						if(cwd == null) {
							cwd = Paths.get(StandardSystemProperty.USER_DIR.value());
						}
						
						cwd = cwd.resolve(cwdValue);
						logger.info("Pinned working directory to " + cwd);
					} else if(filename.equals(cwdResetCwd)) {
						// If cwdValue is an empty string, reset the working directory
						logger.info("Unpinned working directory");

						cwd = null;
					} else {

						Lang rdfLang = RDFDataMgr.determineLang(filename, null, null);
						if(rdfLang != null) {
							
							Model tmp = ModelFactory.createDefaultModel();
							InputStream in = SparqlStmtUtils.openInputStream(filename);
							// FIXME Validate we are really using turtle here
							parseTurtleAgainstModel(tmp, pm, in);
							// Copy any prefixes from the parse back to our global prefix mapping
							pm.setNsPrefixes(tmp);
							
//							tmp.setNsPrefixes(pm);
//							RDFDataMgr.read(tmp, filename);
							
							// FIXME control which graph to load into - by default its the default graph
							logger.info("RDF File detected, loading into graph");
							conn.load(tmp);
						} else {
							
							String baseIri = cwd == null ? null : cwd.toUri().toString();
							SparqlStmtUtils.processFile(pm, filename, baseIri)
								.forEach(stmt -> processSparqlStmtWrapper(conn, stmt, showAlgebra, sink));
						}
					}
				}
				
				sink.flush();
				sink.close();

				
				logger.info("SPARQL overall execution finished after " + sw.stop().elapsed(TimeUnit.MILLISECONDS) + "ms");
				
				// Path path = Paths.get(args[0]);
				// //"/home/raven/Projects/Eclipse/trento-bike-racks/datasets/bikesharing/trento-bike-sharing.json");
				// String str = ""; //new String(Files.readAllBytes(path),
				// StandardCharsets.ISO_8859_1);
				// System.out.println(str);
				//

				// model.setNsPrefixes(PrefixMapping.Extended);
				// model.getResource(path.toAbsolutePath().toUri().toString()).addLiteral(RDFS.label,
				// str);

				SparqlService sparqlService = FluentSparqlService.from(conn).create();

				
				// QueryExecutionFactory qef = FluentQueryExecutionFactory.from(model)
				// .config()
				// //.withParser(sparqlStmtParser)
				// //.withPrefixes(PrefixMapping.Extended, false)
				// .end().create();

				if (args.containsOption("server")) {

					Function<String, SparqlStmt> sparqlStmtParser = SparqlStmtParserImpl.create(Syntax.syntaxSPARQL_11,
							pm, false);// .getQueryParser();

					int port = 7532;
					Server server = FactoryBeanSparqlServer.newInstance()
							.setSparqlServiceFactory((serviceUri, datasetDescription, httpClient) -> sparqlService)
							.setSparqlStmtParser(sparqlStmtParser).setPort(port).create();

					server.start();

					URI browseUri = new URI("http://localhost:" + port + "/sparql");
					if (Desktop.isDesktopSupported()) {
						Desktop.getDesktop().browse(browseUri);
					} else {
						System.err.println("SPARQL service with in-memory result dataset running at " + browseUri);
					}

					server.join();
				}
			};
		}
	}

	public static void processSparqlStmtWrapper(RDFConnection conn, SparqlStmt stmt, boolean showAlgebra, SPARQLResultVisitor sink) {
		Stopwatch sw2 = Stopwatch.createStarted();
		processSparqlStmt(conn, stmt, showAlgebra, sink);
		logger.info("SPARQL stmt execution finished after " + sw2.stop().elapsed(TimeUnit.MILLISECONDS) + "ms");
	}

	public static Op toAlgebra(SparqlStmt stmt) {
		Op result = null;

		if(stmt.isQuery()) {
			Query q = stmt.getAsQueryStmt().getQuery();
			result = Algebra.compile(q);
		} else if(stmt.isUpdateRequest()) {
			UpdateRequest ur = stmt.getAsUpdateStmt().getUpdateRequest();
			for(Update u : ur) {
				if(u instanceof UpdateModify) {
					Element e = ((UpdateModify)u).getWherePattern();
					result = Algebra.compile(e);
				}
			}
		}

		return result;
	}
	
	public static void processSparqlStmt(RDFConnection conn, SparqlStmt stmt, boolean showAlgebra, SPARQLResultVisitor sink) {
		logger.info("Processing SPARQL Statement: " + stmt);
		
		if(showAlgebra) {
			Op op = toAlgebra(stmt);
			logger.info("Algebra: " + op);
		}
		SparqlStmtUtils.process(conn, stmt, sink);
	}


	public static void main(String[] args) throws URISyntaxException {
//		if(true) {
//			System.out.println(new URI("http://foo.bar/baz/bak//").resolve(".."));//.normalize());
//			return;
//		}
		
		JenaExtensionHttp.register(() -> HttpClientBuilder.create().build());

		// RDFConnection conn = RDFConnectionFactory.connect(DatasetFactory.create());
		// System.out.println(ResultSetFormatter.asText(conn.query("SELECT * {
		// BIND('test' AS ?s) }").execSelect()));
		// System.out.println(ResultSetFormatter.asText(conn.query("SELECT * { {}
		// BIND('test' AS ?s) }").execSelect()));

		try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder().sources(ConfigSparqlIntegrate.class)
				.bannerMode(Banner.Mode.OFF)
				// If true, Desktop.isDesktopSupported() will return false, meaning we can't
				// launch a browser
				.headless(false).web(false).run(args)) {

		}
	}

}
