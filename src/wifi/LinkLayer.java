package wifi;
import java.io.PrintWriter;
import java.util.Stack;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 */
public class LinkLayer implements Dot11Interface 
{
	private RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to
	private ArrayBlockingQueue<Packet> outgoing;
	private Stack<Packet> incoming;
	private Sender sender;
	private Receiver receiver;

	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 * @param ourMAC  MAC address
	 * @param output  Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		this.ourMAC = ourMAC;
		this.output = output;      
		this.outgoing =  new ArrayBlockingQueue<Packet>(10);
		this.incoming = new Stack<Packet>();
		theRF = new RF(null, null);
		this.sender = new Sender(theRF, outgoing, output);
		this.receiver = new Receiver(theRF, incoming, output);
		(new Thread(sender)).start();
		(new Thread(receiver)).start();
		output.println("LinkLayer: Constructor ran.");
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		Packet packet = new Packet(ourMAC, dest, data, len);
		outgoing.add(packet);
		output.println("LinkLayer: Sending "+len+" bytes to "+dest);
		return len;
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 */
	public int recv(Transmission t) {
		output.println("LinkLayer: Waiting for data...");
	    while (incoming.isEmpty()) {
	        // Sleep for a short period to avoid busy-waiting
	        try {
	            Thread.sleep(100); // Sleep for 100 milliseconds
	        } catch (InterruptedException e) {
	        	System.err.println("Error while putting thread to sleep");
	        }
	    }

	    Packet packet = incoming.pop(); // Retrieve the first packet from the stack
	    t.setDestAddr(packet.getDest());
	    t.setSourceAddr(packet.getSource());
	    t.setBuf(packet.getData());
	    output.println("LinkLayer: Packet written to Transmission object");
		return packet.getDataLength();
	}

	/**
	 * Returns a current status code.  See docs for full description.
	 */
	public int status() {
		output.println("LinkLayer: Faking a status() return value of 0");
		return 0;
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 */
	public int command(int cmd, int val) {
		output.println("LinkLayer: Sending command "+cmd+" with value "+val);
		return 0;
	}
}
