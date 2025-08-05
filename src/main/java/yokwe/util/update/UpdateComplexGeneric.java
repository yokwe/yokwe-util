package yokwe.util.update;

import java.util.List;

import yokwe.util.Storage;
import yokwe.util.ThreadUtil;

public abstract class UpdateComplexGeneric<T, U> extends UpdateBase {
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();

	protected abstract List<T>    getList          ();
	protected abstract void       delistUnknownFile(List<T> list);
	protected abstract List<U>    getTaskList      (List<T> list);
	protected abstract void       downloadFile     (List<U> taskList);
	protected abstract void       updateFile       (List<T> list);
	
	@Override
	public void update() {
		Storage.initialize();
		
		logger.info("gracePerios  {} hours", gracePeriod.toHours());
		
		var list = getList();
		logger.info("list  {}", list.size());

		delistUnknownFile(list);
		
		for(int retry = 1; retry < 10; retry++) {
			logger.info("  retry  {}", retry);
			var taskList = getTaskList(list);
			logger.info("  task   {}", taskList.size());
			if (taskList.isEmpty()) break;
			
			downloadFile(taskList);
			
			ThreadUtil.sleep(1000);
		}
		
		updateFile(list);

	}
}
