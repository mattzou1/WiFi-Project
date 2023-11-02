package wifi;

import java.io.PrintWriter;
import java.util.Stack;

import rf.RF;

public class Receiver implements Runnable {
	
	private RF theRF; 
	private Stack<Packet> incoming;
	private PrintWriter output;
	
	public Receiver(RF theRF, Stack<Packet> incoming, PrintWriter output) {
		this.theRF = theRF;
		this.incoming = incoming;
		this.output = output;
	}
	@Override
	public void run() {
		while(true) {
			byte[] frame = theRF.receive();
			Packet packet = new Packet(frame);
			incoming.push(packet);
			output.println("Received Packet: " + packet);
		}
	}

}
