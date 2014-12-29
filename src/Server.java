import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Server {
	private static Logger logger = Logger.getLogger(Server.class.getName());
	private static int serverPort = 3478;
	private static ConsoleHandler consoleHandler = new ConsoleHandler();

	public void startServer() throws IOException {
		
		UDPListener ul1 = new UDPListener(serverPort);
		ul1.start();

	}
	
	public static void connectConsoleHandler() {
		logger.addHandler(consoleHandler);
	}

	public static void setLogLevel(Level newLevel) {
		logger.setLevel(newLevel);
	}

	public static void setConsoleHandlerLevel(Level newLevel) {
		consoleHandler.setLevel(newLevel);
	}
	
	class UDPListener extends Thread {

		private DatagramSocket socket;
		private int serverPort;

		public UDPListener(int port) throws IOException {
			logger.log(Level.FINE, "Starting udp listener");
			this.serverPort = port;

			try {
				socket = new DatagramSocket(serverPort);
			} catch (SocketException e) {
				throw new IOException("Can't create DatagramSocket: " + e.getMessage());
			}

		}
		public void run() {
			while (true) {
				try {
					byte[] buffer = new byte[1024];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					logger.log(Level.FINE, "Waiting for request on address "+ socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() +" in run");
					socket.receive(packet);
					logger.log(Level.FINE, "Packet recieved.");
					processRequest(socket, packet);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}
	public void printResponse(byte[] request) {

		StringBuilder sb = new StringBuilder();
		sb.append("Message recieved:\n");


		int messageType = (int) (((request[0] << 8) & 0xff00) | (request[1] & 0xff));
		int length = request.length;

		sb.append("Message type:" + messageType + "\n"
				+ "Packet Lenght: " + length + "\n");


		System.out.println(sb.toString());


	}
	public void processRequest(DatagramSocket socket, DatagramPacket packet) {
		logger.log(Level.FINE, "Processing request.");
		byte[] request = packet.getData();
		int length = packet.getLength();

		if (checkMagicCookieFor(request)) {

			InetSocketAddress isa = (InetSocketAddress) packet.getSocketAddress();

			logger.log(Level.FINE, "Got UDP Stun request on socket "
					+ socket.getLocalAddress() + ":" + socket.getLocalPort()
					+ " length " + length + " bytes " + " from " + isa);

			byte[] response = buildResponse(isa, request, length);
			printResponse(response);

			/*
			 * ChangeRequest - For alternating between servers in order to validate if the user
			 * is behind a Symmetric NAT. Will try implementing this. 
			 */
			int changeRequest = Header.getChangeRequest(request);

			if (!changeIP(changeRequest)) {

				DatagramSocket responseSocket = setSocket(socket, changeRequest, request, response);
				packAndSendData(responseSocket, packet, response);

			}
		} else {
			logger.log(Level.FINE, "magic cookie not ok, Probably not a STUN request. Not much to do");
		}
	}

	private boolean checkMagicCookieFor(byte[] request) {

		int magicCookie = 0x2112A442; //0x2112A442 = 10x554869826
		int extractedCookie = (int) ((request[4] << 24 & 0xff000000) | (request[5] << 16 & 0xff0000) 
				| (request[6] << 8 & 0xff00) | (request[7] & 0xff));

		logger.log(Level.FINE, "magic cookie = incoming cookie "+ magicCookie + " = " + extractedCookie);

		return (magicCookie == extractedCookie) ? true : false;
	}

	private boolean changeIP(int changeRequest) {

		if ((changeRequest & Header.CHANGE_IP_MASK) != 0) {

			logger.warning("Exit because we were requested to change ip. Can't do that (yet)");
			return true;

		} else {

			return false;
		}
	}

	private DatagramSocket setSocket(DatagramSocket socket, int changeRequest, byte[] request, byte[] response) {

		if ((changeRequest & Header.CHANGE_PORT_MASK) != 0) {
			try {
				DatagramSocket responseSocket = new DatagramSocket();
				return responseSocket;

			} catch (SocketException e) {
				e.printStackTrace();
				String s = "CHANGE_PORT set but can't create new socket! " + e.getMessage();
				response = buildErrorResponse(request, Header.GLOBAL_ERROR, s);
			}
		}
		return socket;
	}

	private void packAndSendData(DatagramSocket socket, DatagramPacket packet, byte[] response) {
		try {
			packet.setData(response);
			socket.send(packet);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * checks if the STUN Header is okay
	 * @param request
	 * @param length
	 * @return 1 if okay, otherwise STUN error code
	 */
	private int checkHeaderErrors(byte[] request, int length) {

		if (length < Header.LENGTH) {

			return Header.BAD_REQUEST;
		}
		int messageType = (int) (((request[0] << 8) & 0xff00) | (request[1] & 0xff));

		if (messageType != Header.BINDING_REQUEST) {

			return Header.GLOBAL_ERROR;
		}

		return 1;
	}

	private byte[] buildResponse(InetSocketAddress isa, byte[] request, int length) {

		int messageType = checkHeaderErrors(request, length);

		if (messageType == 1) {
			return buildBindingResponse(isa, request, length);
		} else {
			if (messageType == 400) 
				return buildErrorResponse(request, messageType, "BAD REQUEST - Header to small");
			else 
				return buildErrorResponse(request, messageType, "GLOBAL ERROR - Only Binding Requests accepted");
		}
	}

	private byte[] buildBindingResponse(InetSocketAddress isa, byte[] request, int length) {
		logger.log(Level.INFO, "Building Binding Response");

		byte[] response = new byte[Header.LENGTH + Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS_LENGTH
		                           + Header.TYPE_LENGTH_VALUE + Header.CHANGED_ADDRESS_LENGTH];

		System.arraycopy(request, 0, response, 0, Header.LENGTH);

		setHeaderTo(response);
		setMappedTypeAndValueTo(response);

		logger.log(Level.FINE, "responding with " + isa);

		addPortTo(response, isa);
		addSourceAddressTo(response, isa);

		return response;
	}
	
	private void setHeaderTo(byte[] response) {
		response[0] = 1;

		response[3] = (byte) Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS +
				Header.TYPE_LENGTH_VALUE + Header.CHANGED_ADDRESS_LENGTH;
	}

	private void setMappedTypeAndValueTo(byte[] response) {
		response[Header.LENGTH + 1] = Header.MAPPED_ADDRESS;

		response[Header.LENGTH + 3] = Header.MAPPED_ADDRESS_LENGTH;

		response[Header.LENGTH + 5] = 1; //Address family - only IPv4

	}

	private void addPortTo(byte[] response, InetSocketAddress isa) {

		int sourcePort = isa.getPort();

		response[Header.LENGTH + 6] = (byte) (sourcePort >> 8);
		response[Header.LENGTH + 7] = (byte) (sourcePort & 0xff);

	}

	private void addSourceAddressTo(byte[] response, InetSocketAddress isa) {
		byte[] sourceAddress = isa.getAddress().getAddress();

		response[Header.LENGTH + 8] = sourceAddress[0];
		response[Header.LENGTH + 9] = sourceAddress[1];
		response[Header.LENGTH + 10] = sourceAddress[2];
		response[Header.LENGTH + 11] = sourceAddress[3];
	}

	private byte[] buildErrorResponse(byte[] request, int responseCode, String reason) {

		byte[] response = new byte[Header.LENGTH + Header.ERROR_CODE_LENGTH + reason.length()];

		System.arraycopy(request, 0, response, 0, Header.LENGTH);

		response[0] = 1;
		response[1] = 0x11;

		response[Header.LENGTH + 2] = (byte) (responseCode >> 8);

		response[Header.LENGTH + 3] = (byte) (responseCode & 0xff);

		byte[] reasonBytes = reason.getBytes();

		System.arraycopy(reasonBytes, 0, response, 24, reasonBytes.length);

		int length = Header.LENGTH + Header.ERROR_CODE_LENGTH + reasonBytes.length;

		response[2] = (byte) (length >> 8);
		response[3] = (byte) (length & 0xff);

		return response;
	}


	public static void main(String[]args) {
		setLogLevel(Level.FINE);
		connectConsoleHandler();
		setConsoleHandlerLevel(Level.FINE);
		
		Server server = new Server();

		try {
			server.startServer();
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
			System.exit(1);
		}
		System.out.println("Server started");
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//server.test();

	}

	public void test() {
		System.out.println("Starting test");
		DatagramSocket socket = null;

		try { 
			socket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}

		byte[] request = new byte[Header.LENGTH];

		request[1] = 1;

		for (int i = 0; i < 16; i++) {
			request[4 + i] = (byte) i;
		}
		DatagramPacket packet = null;

		try {

			packet = new DatagramPacket(request, request.length, InetAddress.getLocalHost(), serverPort);
		} catch (UnknownHostException e) {
			System.out.println("Can't get localhost");
			System.exit(1);
		}

		try {
			socket.send(packet);
			System.out.println("packet sent");
			if (logger.isLoggable(Level.FINEST)) {
				Header.dump("Sent stun binding request to " + packet.getAddress() + ": " + packet.getPort(), request, 0, request.length);
			}
		} catch (IOException e) {
			System.out.println("Unable to send STUN binding request! " + e.getMessage());
		}
	}

}
