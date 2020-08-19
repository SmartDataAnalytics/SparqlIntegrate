package org.aksw.sparql_integrate.ngs.cli.cmd;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.aksw.sparql_integrate.ngs.cli.main.NgsCmdImpls;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * List the top n named graphs
 *
 * @author raven
 *
 */
@Command(name = "merge", description = "Not implemented; merge graphs from multiple input file based on a sort key")
public class CmdNgsMerge implements Callable<Integer> {

    @Option(names = { "-h", "--help" }, usageHelp = true)
    public boolean help = false;

    @Option(names = { "-o", "--out-format" })
    public String outFormat = "trig/pretty";

    @Parameters(arity = "0..*", description = "Input files")
    public List<String> nonOptionArgs = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        return NgsCmdImpls.merge(this);
    }
}