package cn.weforward.gateway.util;

import java.util.concurrent.Executor;

/**
 * 在当前线程执行任务的Executor
 * 
 * @author zhangpengji
 *
 */
public class DirectExecutor implements Executor {

	@Override
	public void execute(Runnable command) {
		command.run();
	}

}
