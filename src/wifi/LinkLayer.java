package wifi;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import rf.RF;

/**
 * Use this layer as a starting point for your project code. See
 * {@link Dot11Interface} for more details on these routines.
 * 
 * @author richards
 */
public class LinkLayer implements Dot11Interface {
	private RF theRF; // You'll need one of these eventually
	private short ourMAC; // Our MAC address
	private PrintWriter output; // The output stream we'll write to
	private AtomicLong localOffset;
	private ArrayBlockingQueue<Packet> outgoing;
	private ArrayBlockingQueue<Packet> incoming;
	private ArrayBlockingQueue<Integer> acks;
	private AtomicIntegerArray cmds;
	private HashMap<Short, Integer> seqNums; //contains most recently used seqNum for ever destination
	private Sender sender;
	private Receiver receiver;
	

	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 * 
	 * @param ourMAC MAC address
	 * @param output Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		theRF = new RF(null, null);
		this.ourMAC = ourMAC;
		this.output = output;
		this.localOffset = new AtomicLong(0);
		this.outgoing = new ArrayBlockingQueue<Packet>(10);
		this.incoming = new ArrayBlockingQueue<Packet>(10);
		this.acks = new ArrayBlockingQueue<Integer>(10);
		this.cmds = new AtomicIntegerArray(3);
		
		
		this.cmds.set(2, 5); //Set beacon offset
		this.cmds.set(0, 0); //set default debug setting
		
		
		
		this.seqNums = new HashMap<Short, Integer>();
		this.sender = new Sender(theRF, outgoing, acks, cmds, output, ourMAC, localOffset);
		this.receiver = new Receiver(theRF, incoming, acks, cmds, output, ourMAC, localOffset);
		(new Thread(sender)).start();
		(new Thread(receiver)).start();
		if (cmds.get(0) != 0) {
			output.println("LinkLayer: Constructor ran.");
		}

	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number of
	 * bytes to send. See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		int seqNum = 0;
		if(seqNums.containsKey(dest)) {
			//dest is in hashmap, increment most recently used seqNum
			seqNum = (seqNums.get(dest) + 1) & 0xFFF;
			seqNums.put(dest, seqNum);
		}
		else {
			//dest is not in hashmap, use seqNum 0
			seqNums.put(dest, 0);
		}	
		Packet packet = new Packet((short) 0, (short) 0, seqNum, ourMAC, dest, data, len);
		outgoing.add(packet);
		if (cmds.get(0) != 0) {
			output.println("LinkLayer: Sending " + len + " bytes to " + dest);
		}
		return len;
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object. See docs for full description.
	 */
	public int recv(Transmission t) {
		if (cmds.get(0) != 0) {
			output.println("LinkLayer: Waiting for data...");
		}

		while (incoming.isEmpty()) {
			// Sleep for a short period to avoid busy-waiting
			try {
				Thread.sleep(20); // Sleep for 20 milliseconds
			}
			catch (InterruptedException e) {
				System.err.println("Error while putting thread to sleep");
			}
		}

		Packet packet = incoming.poll();
		t.setDestAddr(packet.getDest());
		t.setSourceAddr(packet.getSource());
		t.setBuf(packet.getData());
		if (cmds.get(0) != 0) {
			output.println("LinkLayer: Packet written to Transmission object");
		}
		return packet.getDataLength();
	}

	/**
	 * Returns a current status code. See docs for full description.
	 */
	public int status() {
		output.println("LinkLayer: Faking a status() return value of 0");
		return 0;
	}

	/**
	 * Passes command info to your link layer. See docs for full description.
	 */
	public int command(int cmd, int val) {
		if (cmd == 0) {
			output.println("-------------- Commands and Settings -----------------");
			output.println("Debug level: A value of 0 disables all, -1 enables all and -2 enables beacon debug only");
			output.println("Current value: " + cmds.get(0) + "\n");
			output.println(
					"Slot selection: A value of 0 makes the link layer select slots randomly, any other value makes the link layer always select maxCW");
			output.println("Current value: " + cmds.get(1) + "\n");
			output.println(
					"Beacon interval: Value specifies the desired number of seconds between the start of beacon transmissions, A value of -1 disables the sending of beacon frames");
			output.println("Current value: " + cmds.get(2) + "\n");
			output.println("------------------------------------------------------");
		}
		else if (cmd == 1) {
			cmds.set(0, val);
			output.println("Debug level value: " + val);
		}
		else if (cmd == 2) {
			cmds.set(1, val);
			output.println("Slot selection value: " + val);
		}
		else if (cmd == 3) {
			cmds.set(2, val);
			output.println("Beacon interval value: " + val);
		}
		return 0;
	}
}
