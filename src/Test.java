import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;


public class Test {
	
	private DatagramSocket socket;
	private InetSocketAddress serverAddress;
	
	public static void main(String[]args) {
		Test t = new Test();
		
		t.process();
		
		
	}
	
	public void process() {

		try {
			socket = new DatagramSocket(4200);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		serverAddress = new InetSocketAddress("192.168.1.132", 3478);
		
		try {
			Client client = new Client(serverAddress, socket);
			client.start();
			InetSocketAddress address = client.getMappedAddress();
			System.out.println("Final address:" + address.toString());
		} catch (IOException e) {

			e.printStackTrace();
		}
		
		
	}
}
