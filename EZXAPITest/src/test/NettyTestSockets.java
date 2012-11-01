package test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.Before;
import org.junit.Test;

import com.eztech.middleware.msgs.iserver.common.types.MsgType;
import com.eztech.middleware.msgs.iserver.common.types.OrdType;
import com.eztech.middleware.msgs.iserver.ezxapi.OrderRequest;
import com.eztech.util.UniqueIDGenerator;

public class NettyTestSockets {

	private static final Logger LOG = Logger.getLogger(NettyTestSockets.class);

	private static final int DEFAULT_LISTEN_PORT = 7000;
	// # of messages to send
	private static final int TEST_ITERATIONS = 1000;
	// port that sockets connect on (localhost)
	private static int listenPort = DEFAULT_LISTEN_PORT;

	// Sockets stuff - NIO
	private SocketChannel sender;
	// message to send
	private byte[] payload;
	// test thread
	private Thread senderThread;
	// send/receive buffers
	private ByteBuffer sendBuffer;
	// for tracking start and stop times in arrays
	private int sendCount;
	private int recvCount;
	// start/stop time value for each message
	private long[] startTimeNanos;
	private long[] endTimeNanos;

	private ServerBootstrap bootstrap;

	private Channel listenerChannel;

	public static void main(String[] args) {
		try {
			if (args.length > 0) {
				listenPort = Integer.parseInt(args[0]);
				LOG.info("overriding default listenPort to " + listenPort);
			}
			NettyTestSockets testSockets = new NettyTestSockets();
			testSockets.setUp();
			testSockets.testTimeToSendMessages();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Before
	public void setUp() throws Exception {
		createPayload();
		setUpReceiver();

		startTimeNanos = new long[TEST_ITERATIONS];
		endTimeNanos = new long[TEST_ITERATIONS];

	}

	private void createPayload() {
		OrderRequest request = new OrderRequest();
		request.msgType = MsgType.NEW;
		request.myID = UniqueIDGenerator.getNextID();
		request.side = 1;
		request.symbol = "IBM";
		request.orderQty = 1000;
		request.ordType = OrdType.LIMIT;
		request.price = 129.65;
		request.destination = "NYSE";
		payload = request.encode();
	}

	private void connectSender() throws IOException {
		sender = SocketChannel.open();
		sender.configureBlocking(true);
		sender.socket().setTcpNoDelay(true);
		senderThread = new SenderThread();
		senderThread.start();

	}

	private void setUpReceiver() throws IOException {
		ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
		bootstrap = new ServerBootstrap(factory);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

			@Override
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(new SimpleChannelHandler() {
					@Override
					public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
						readMessage(e);
					}

					@Override
					public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
						LOG.error("error caught!", e.getCause());
					}
				});
			}
		});

		//		listener = ServerSocketChannel.open();
		//		listener.socket().bind(new InetSocketAddress(listenPort));
		listenerChannel = bootstrap.bind(new InetSocketAddress(listenPort));
		//		new ListenerThread().start();
	}

	@Test
	public void testTimeToSendMessages() throws Exception {
		connectSender();
		senderThread.join();
		reportLatencies();
		listenerChannel.close();
		listenerChannel.unbind();
		bootstrap.releaseExternalResources();
	}

	private void reportLatencies() {
		LOG.debug("Reporting latency for each message:");
		int validEntries = 0;
		long totalTime = 0, min = Long.MAX_VALUE, max = 0;
		for (int i = 0; i < TEST_ITERATIONS; i++) {
			long latencyNanos = endTimeNanos[i] - startTimeNanos[i];
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

	private void loadSendBuffer() {
		sendBuffer = ByteBuffer.allocateDirect(payload.length);
		sendBuffer.put(payload);
	}

	private void writeMessage() {
		sendBuffer.rewind();
		try {
			int written;
			startTimeNanos[sendCount++] = System.nanoTime();
			written = sender.write(sendBuffer);

			// LOG.debug("writeMessage(): message sent");
			if (written < payload.length) {
				LOG.error("wrote less than payload. sent=" + written);
			}
		} catch (Exception e) {
			LOG.error("writeMessage(): error sending payload, e=" + e, e);
		}
	}

	private void readMessage(MessageEvent me) {
		me.getMessage();
		endTimeNanos[recvCount++] = System.nanoTime();
		// LOG.debug("msg received, count=" + recvCount + ", time=" +
	}

	//	class ListenerThread extends Thread {
	//		public ListenerThread() {
	//			super(ListenerThread.class.getSimpleName());
	//			setDaemon(true);
	//		}
	//
	//		@Override
	//		public void run() {
	//			try {
	//				recvBuffer = ByteBuffer.allocateDirect(payload.length);
	//				LOG.info("Listening socket waiting for connection on port: " + listener.socket().getLocalPort());
	//				receiver = listener.accept();
	//				receiver.socket().setTcpNoDelay(true);
	//				LOG.info("connection accepted!");
	//				while (true) {
	//					readMessage();
	//				}
	//			} catch (IOException e) {
	//				LOG.error("run(): error accepting connection, e=" + e, e);
	//			}
	//		}
	//	}

	class SenderThread extends Thread {
		// time to sleep between transmissions
		private static final long PULSE_TIME = 5;

		@Override
		public void run() {
			try {
				loadSendBuffer();
				sender.connect(new InetSocketAddress("localhost", listenPort));
				LOG.info("Sending socket connected and preparing to send " + TEST_ITERATIONS + " messages");
				for (int i = 0; i < TEST_ITERATIONS; i++) {
					writeMessage();
					// delay a little between messages to prevent too much CPU caching, and simulate more real conditions
					// if there is no sleep, it definitely runs faster, about 13-20 micros faster
					Thread.sleep(PULSE_TIME);
				}
			} catch (Exception e) {
				LOG.error("run(): error accepting connection, e=" + e);
				e.printStackTrace();
			}
		}
	}

}
