package org.aksw.sparql_integrate.ngs.cli.main;

import java.util.List;

import org.aksw.jena_sparql_api.io.utils.SimpleProcessExecutor;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.FlowableTransformer;

public class FlowableOps {

	/**
	 * Use a system call for line-based processing.
	 * 
	 * The strings supplied by the upstream flowable are passed as lines
	 * to stdin of the created process, whereas lines emitted on STDOUT are
	 * forwarded to the downstream flowable. 
	 * 
	 * The directly spawned process is cancellable, however, due to Java limitations,
	 * child processes may not terminate. Wrapping commands in scripts can be used
	 * to mitigate this issue.
	 * 
	 * 
	 * @param args
	 * @return
	 */
	public static FlowableTransformer<String, String> sysCall(List<String> args) {		
		return upstream -> {
			return Flowable.create(new FlowableOnSubscribe<String>() {
				@Override
				public void subscribe(FlowableEmitter<String> e) throws Exception {
					SimpleProcessExecutor.wrap(new ProcessBuilder(args))
						.executeReadLines(upstream, e);
				}
			}, BackpressureStrategy.BUFFER);
		};
	}

}