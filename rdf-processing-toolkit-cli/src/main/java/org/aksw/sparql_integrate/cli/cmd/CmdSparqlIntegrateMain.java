package org.aksw.sparql_integrate.cli.cmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;

import org.aksw.rdf_processing_toolkit.cli.cmd.CmdCommonBase;
import org.aksw.rdf_processing_toolkit.cli.cmd.VersionProviderRdfProcessingToolkit;
import org.aksw.sparql_integrate.cli.main.SparqlIntegrateCmdImpls;
import org.apache.jena.ext.com.google.common.base.StandardSystemProperty;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


@Command(name = "integrate",
    versionProvider = VersionProviderRdfProcessingToolkit.class,
    description = "Run sequences of SPARQL queries and stream triples, quads and bindings")
public class CmdSparqlIntegrateMain
    extends CmdCommonBase
    implements Callable<Integer>
{
//    @Option(names = { "-h", "--help" }, usageHelp = true)
//    public boolean help = false;
//
//    @Option(names = { "-v", "--version" }, versionHelp = true)
//    public boolean version = false;

    @Option(names = { "-e", "--engine" }, description="SPARQL Engine. Currently only 'mem' and 'tdb2' supported")
    public String engine = "mem";

    @Option(names = { "--db", "--db-path" }, description="Path to database directory or file (for disk-based engines)")
    public String dbPath = null;

    @Option(names = { "-T", "--temporary-directory" }, description="Temporary directory")
    public String tempPath = StandardSystemProperty.JAVA_IO_TMPDIR.value();

    @Option(names = { "--db-keep" }, description="Keep generated database files")
    public boolean dbKeep = false;


    @Option(names = { "--explain" }, description="Enable detailed ARQ log output")
    public boolean explain = false;

    @Option(names = { "--split" }, description="Create corresponding output files for each file argument with SPARQL queries")
    public String splitFolder = null;

    @Option(names = { "--set" }, description="Set ARQ options (key=value)", mapFallbackValue="true")
    public Map<String, String> arqOptions = new HashMap<>();


//    @Option(names = { "-X" }, description = "Debug output such as full stacktraces")
//    public boolean debugMode = false;

    /**
     * sparql-pattern file
     *
     */
    @Option(names = { "-a", "--algebra" }, description = "Show Algebra")
    public boolean showAlgebra = false;
    // public long numRecords = 10;

    @Option(names = { "-u", "--u" }, description = "Union default graph mode")
    public boolean unionDefaultGraph = false;

    // TODO Make port configurable
    @Option(names = { "--server" }, description = "Start a SPARQL server")
    public boolean server = false;

    @Option(names = { "--port" }, description = "Server port")
    public int serverPort = 7531;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    public OutputSpec outputSpec;

    public static class OutputSpec {
        /**
         * sparql-pattern file
         *
         */
        @Option(names = { "--o" }, description = "output file (legacy; avoid use)") // legacy option
        public String outFile;

        @Option(names = { "--io", },  description = "overwrites input file on success with output; use with care")
        public String inOutFile = null;
    }

    @Option(names = { "-d", "--used-prefixes" }, description = "Number of records by which to defer RDF output in order to analyze used prefixes; default: ${DEFAULT-VALUE}", defaultValue = "20")
    public long usedPrefixDefer;


    /**
     * If not given, the output mode (quads/bindings/json) is chosen from the remaining arguments and
     * the outFormat becomes the default format of that mode
     *
     * If given, the output mode is determined by the argument
     *
     */
    @Option(names = { "-o", "--out-format", "--w" }, description = "Output format")
    public String outFormat = null;

    // Subsume jq stuff under -w jq ?

    /**
     * jq mode transforms result sets into a lossy json representation by expanding its mentioned resources up to a given depth
     * this is convenient to process in bash pipes
     *
     */
    @Option(names = { "--jq" }, parameterConsumer = ConsumeDepthValue.class, arity="0..1", fallbackValue = "3", description = "Enable jq mode")
    public Integer jqDepth = null;



    /**
     *
     *
     */
    @Option(names = { "--flat" }, description = "Suppress JSON arrays for single valued properties")
    public boolean jqFlatMode = false;

    /**
     * --jq may be followed by an integer - picocli seems to greedily parse any argument even if it is not an integer
     *
     * @author raven
     *
     */
    static class ConsumeDepthValue implements IParameterConsumer {
        @Override
        public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
            if (!args.isEmpty()) {
                String top = args.peek();
                Integer val;
                try {
                    val = Integer.parseInt(top);
                    args.pop();
                } catch(NumberFormatException e) {
                    val = 3;
                }

                argSpec.setValue(val);
            }
        }
    }



    @Parameters(arity = "0..*", description = "File names with RDF/SPARQL content and/or SPARQL statements")
    public List<String> nonOptionArgs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {

        return SparqlIntegrateCmdImpls.sparqlIntegrate(this);
    }

}