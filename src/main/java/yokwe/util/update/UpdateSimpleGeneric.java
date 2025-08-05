package yokwe.util.update;

import yokwe.util.Storage;

public abstract class UpdateSimpleGeneric<T> extends UpdateBase {
	protected abstract T    downloadFile ();
	protected abstract void updateFile   (T t);
	
	@Override
	public void update() {
		Storage.initialize();
		
		T t = downloadFile();
		updateFile(t);
	}
}
