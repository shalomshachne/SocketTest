package clipc;

import java.io.File;

import org.apache.log4j.Logger;


import com.eztech.middleware.msgs.iserver.common.types.MsgType;
import com.eztech.middleware.msgs.iserver.common.types.OrdType;
import com.eztech.middleware.msgs.iserver.ezxapi.OrderRequest;
import com.eztech.util.StopWatch;
import com.eztech.util.UniqueIDGenerator;
import com.lts.ipc.IPCException;
import com.lts.ipc.semaphore.Semaphore;
import com.lts.ipc.sharedmemory.SharedMemory;

public class Initiator {

	public static final String EMPTY_COUNT = "empty";
	public static final String FULL_COUNT = "full";
	public static final String MEMORY_FILE = "ezxmemory";
	
	public static final int ITERATIONS = 1000000;
	private static final int SKIP_COUNT = 3;
	
	private static final Logger LOG = Logger.getLogger(Initiator.class);
	
	
	private static StopWatch watch = new StopWatch();
	
	private static Semaphore recvFullCount;
		
	private static byte[] messageBytes = new byte[Receiver.ACK_MESSAGE.length()];
	private static Semaphore emptyCount;
	private static Semaphore fullCount;
	
	private static long totalTime = 0;
	private static long max = 0, min = -1;
	
	/**
	 * @param args
	 * @throws IPCException 
	 */
	public static void main(String[] args) throws Exception {
		assert ITERATIONS > SKIP_COUNT;
		
		LOG.info("main(): starting up.");
		
		emptyCount = new Semaphore(EMPTY_COUNT, 1);
		fullCount = new Semaphore(FULL_COUNT, 0);
		
		// receiver
		recvFullCount = new Semaphore(Receiver.FULL_COUNT, 0);
		
		SharedMemory memory = new MySharedMemory(MEMORY_FILE, 100);
		
		LOG.info("Initiator ready");
		for (int i=0; i < ITERATIONS; i++) {
			LOG.trace("P(emptyCount): " + i);						
			emptyCount.decrement();
			writeMessage(i, memory);
//			LOG.trace("V(fullCount): " + i);				

		}
		
		String report = "iterations=%s, avg us=%s, min=%s, max=%s";
		long avg = totalTime / (ITERATIONS - SKIP_COUNT);
		LOG.info(String.format(report, ITERATIONS, avg, min, max));

	}


	


	private static void writeMessage(int i, SharedMemory memory) throws Exception {
		OrderRequest request = new OrderRequest();
		request.msgType = MsgType.NEW;
		request.routerOrderID = i;
		request.myID = UniqueIDGenerator.getNextID();
		request.side = 1;

		request.symbol = "IBM";
		request.orderQty = 1000;
		request.ordType = OrdType.LIMIT;
		request.price = 129.65;
		request.destination = "NYSE";
		byte[] payload = request.encode();
		//byte[]
		watch.reset();
		watch.start();
		memory.write(payload, 0, payload.length, 0);	
		fullCount.increment();		
		// wait for ACK		
		recvFullCount.decrement();
		memory.read(0, messageBytes, 0, messageBytes.length);
		watch.stop();		
		LOG.debug("writeMessage(): wrote message with ROID=" + i + ", wrote bytes=" + payload.length + ", elapsed micros=" + watch.getElapsedMicros()
				+ ", msg=" + new String(payload));				
		LOG.debug("writeMessage(): got ACK for i=" + i + ", msg=" + new String(messageBytes));		
		if (i > SKIP_COUNT - 1)  {// throw out first time due to start up
			long current = watch.getElapsedMicros();
			totalTime += current;
			max = Math.max(current, max);
			min = min > -1 ? Math.min(current, min) : current;
		}
	}

}
