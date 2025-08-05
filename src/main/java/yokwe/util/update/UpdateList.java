package yokwe.util.update;

import java.util.List;

import yokwe.util.Storage;

public abstract class UpdateList<T> extends UpdateSimpleGeneric<List<T>>{
	private static final org.slf4j.Logger logger = yokwe.util.LoggerUtil.getLogger();
	
	@Override
	public void update() {
		Storage.initialize();
		
		List<T> list = downloadFile();
		logger.info("list  {}", list.size());
		updateFile(list);
	}
}
