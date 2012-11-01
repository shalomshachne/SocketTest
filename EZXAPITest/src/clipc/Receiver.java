package clipc;

import org.apache.log4j.Logger;

import com.eztech.util.StopWatch;
import com.lts.ipc.IPCException;
import com.lts.ipc.semaphore.Semaphore;
import com.lts.ipc.sharedmemory.SharedMemory;

public class Receiver {

	public static final String EMPTY_COUNT = "recv-empty";
	public static final String FULL_COUNT = "recv-full";	
	
	public static final String ACK_MESSAGE = "ACK=" ;
	
	private static final byte[] ACK_BYTES = ACK_MESSAGE.getBytes();
	
	private static final Logger LOG = Logger.getLogger(Receiver.class);

	private static StopWatch watch = new StopWatch();
	
	private static byte[] messageBytes = new byte[100];
	
	private static Semaphore recvEmptyCount;
	private static Semaphore recvFullCount;
	
		
	public static void main(String[] args) throws IPCException {
		LOG.info("main(): started");
		// initiator
		Semaphore emptyCount = new Semaphore(Initiator.EMPTY_COUNT);
		Semaphore fullCount = new Semaphore(Initiator.FULL_COUNT);
		
		// receiver
		recvEmptyCount = new Semaphore(EMPTY_COUNT, 1);
		recvFullCount = new Semaphore(FULL_COUNT, 0);
		
		SharedMemory memory = new MySharedMemory(Initiator.MEMORY_FILE, messageBytes.length);	
		
		int iterations = Initiator.ITERATIONS;
		LOG.info("Receiver ready");		
		for (int i = 0; i < iterations; i++) {
			LOG.trace("P(fullCount): " + i);
			fullCount.decrement();
			readMessage(i, memory);
			LOG.trace("V(emptyCount): " + i);
			emptyCount.increment();											
		}		
				
	}

	private static void sendAck(int i, SharedMemory memory) throws IPCException {
		// write(byte[] buf, int buffOffset, int bufLength, int segmentOffset) 
		memory.write(ACK_BYTES, 0, ACK_BYTES.length, 0);
		recvFullCount.increment();		
		LOG.debug("sendACK(): ack sent");
	}

	private static int read;
	private static void readMessage(int i, SharedMemory memory) throws IPCException {
		clearBuffer();
		watch.reset();
		watch.start();
		read = 0;
		while (read == 0) read = memory.read(0, messageBytes, 0, messageBytes.length);
		sendAck(i, memory);
		watch.stop();
		LOG.debug("readMessage(): read message, iteration=" + i + ", read bytes=" + read 
				+ ", elapsed micros=" + watch.getElapsedMicros() + ", msg=" + new String(messageBytes));
	}
	
	private static void clearBuffer() {
		for (int i = 0; i < messageBytes.length; i++) {
			messageBytes[i] = (byte) 1;
		}		
	}
}
