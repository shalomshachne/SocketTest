package clipc;

import org.apache.log4j.Logger;

import com.lts.ipc.IPCException;
import com.lts.ipc.sharedmemory.SharedMemory;

public class MySharedMemory extends SharedMemory{
	
	private static final Logger LOG = Logger.getLogger("MySharedMemory");

	public MySharedMemory(String arg0, int arg1) throws IPCException {
		super(arg0, arg1);
		LOG.info("ctor(): constructed shared memory to: " + arg0 + ", connected flag=" + connected);
		LOG.info("ctor(): calling Buffer.load");
		byteBuffer.load();
	}

	
	
	
}
