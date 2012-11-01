package test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eztech.middleware.msgs.MsgBase;
import com.eztech.middleware.msgs.iserver.common.types.MsgType;
import com.eztech.middleware.msgs.iserver.common.types.OrdType;
import com.eztech.middleware.msgs.iserver.ezxapi.OrderRequest;
import com.eztech.util.UniqueIDGenerator;

public class MemoryMappedFileTest {
	private static final Logger LOG = Logger.getLogger(MemoryMappedFileTest.class);
	private static final String FILE_NAME = "memtest.mem";
	private static final long FILE_SIZE = 500;
	private static final int TEST_ITERATIONS = 10000;

	private FileChannel fileChannel;
	private MappedByteBuffer writeBuffer;
	private MappedByteBuffer readBuffer;
	private File file;
	private RandomAccessFile accessFile;
	private byte[] payload;
	private byte[] readArea;

	private long[] startTimes;
	private long[] endTimes;
	private byte[][] readMessages;
	private int counter;

	private Object monitor = new Object();
	@Before
	public void setUp() throws Exception {
		setUpFile();
		accessFile = new RandomAccessFile(file, "rw");
		fileChannel = accessFile.getChannel();
		writeBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, FILE_SIZE);
		readBuffer = fileChannel.map(MapMode.READ_ONLY, 0, FILE_SIZE);
		createPayload();
		readArea = new byte[payload.length];

		startTimes = new long[TEST_ITERATIONS];
		endTimes = new long[TEST_ITERATIONS];
		readMessages = new byte[TEST_ITERATIONS][];
	}


	@After
	public void tearDown() throws Exception {
		fileChannel.close();
	}

	private void setUpFile() throws IOException {
		file = File.createTempFile(FILE_NAME, null);
	}

	private void createPayload() {
		OrderRequest request = new OrderRequest();
		request.msgType = MsgType.NEW;
		request.routerOrderID = counter++;
		request.myID = UniqueIDGenerator.getNextID();
		request.side = 1;

		request.symbol = "IBM";
		request.orderQty = 1000;
		request.ordType = OrdType.LIMIT;
		request.price = 129.65;
		request.destination = "NYSE";
		payload = request.encode();
	}

	@Test
	public void testWriteToMM() throws Exception {
		WriteThread writeThread = new WriteThread();
		ReadThread readThread = new ReadThread();
		writeThread.start();
		readThread.start();
		writeThread.join();
		synchronized(monitor) {
			monitor.notifyAll();
		}
		reportLatencies();
		verifyReadMessages();
	}

	private static final String ROID_TAG = "ROID=";
	private void verifyReadMessages() {
		int lastROID = -1, currentROID = -1;
		for (byte[] msg : readMessages) {
			String message = new String(msg);
			int start = message.indexOf(ROID_TAG);
			int end = message.indexOf(MsgBase.TAG_VALUE_PAIRS_DELIMITER, start);
			String roid = message.substring(start + ROID_TAG.length(), end);
			currentROID = Integer.parseInt(roid);
			if (lastROID > -1) {
				assertEquals("got expected message", lastROID + 1, currentROID);
			}
			lastROID = currentROID;
		}

	}

	private void reportLatencies() {
		LOG.debug("Reporting latency for each message:");
		int validEntries = 0;
		long totalTime = 0, min = Long.MAX_VALUE, max = 0;
		for (int i = 0; i < TEST_ITERATIONS; i++) {
			long latencyNanos = endTimes[i] - startTimes[i];						
			long time = TimeUnit.NANOSECONDS.toMicros(latencyNanos);
			if (time > 0) {
				totalTime += time;
				validEntries++;
				min = Math.min(min, time);
				max = Math.max(max, time);
			}
			LOG.debug("message #" + i + ": latency= " + time + " micros.");
		}

		String report = "%s iterations - latency in micros to send %s bytes via TCP/IP: avg=%s, min=%s, max=%s";
		LOG.info(String.format(report, TEST_ITERATIONS, payload.length, totalTime / validEntries, min, max));

	}	


	public void writePayload(int i) throws Exception {
		writeBuffer.clear();
		createPayload();
		synchronized (monitor) {	
			startTimes[i] = System.nanoTime();
			writeBuffer.put(payload);	
			//			LOG.info("wrote " + new String(payload));		
			monitor.notify();
			monitor.wait();
		}

	}

	int currentROID;

	public void readPayload(int i) throws InterruptedException {		
		readBuffer.clear();
		synchronized (monitor) {
			readBuffer.get(readArea);
			endTimes[i] = System.nanoTime();			
			//			LOG.info("Read " + new String(readArea));		
			readMessages[i] = new byte[readArea.length];
			System.arraycopy(readArea, 0, readMessages[i], 0, readArea.length);
			monitor.notify();			
			monitor.wait();

		}
	}

	class WriteThread extends Thread {
		public WriteThread() {
			super(WriteThread.class.getSimpleName());
		}

		@Override
		public void run() {
			for (int i = 0; i < TEST_ITERATIONS; i++) {
				try {
					writePayload(i);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	class ReadThread extends Thread {
		private int lastROID = -1;

		public ReadThread() {
			super(ReadThread.class.getSimpleName());
		}

		@Override
		public void run() {
			synchronized(monitor) {
				try {
					monitor.wait();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			for (int i = 0; i < TEST_ITERATIONS; i++) {
				try {
					readPayload(i);
					if (lastROID > -1) {

					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args) {
		MemoryMappedFileTest test = new MemoryMappedFileTest();
		try {
			test.setUp();
			test.testWriteToMM();
			test.tearDown();
		} catch (Exception e) {			
			e.printStackTrace();
		}
	}

}
