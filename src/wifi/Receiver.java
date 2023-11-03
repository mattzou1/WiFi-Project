package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

import rf.RF;

public class Receiver implements Runnable {

	private RF theRF;
	private ArrayBlockingQueue<Packet> incoming;
	private ArrayBlockingQueue<Integer> acks;
	private AtomicIntegerArray cmds;
	private PrintWriter output;
	private short ourMAC;

	public Receiver(RF theRF, ArrayBlockingQueue<Packet> incoming, ArrayBlockingQueue<Integer> acks,
			AtomicIntegerArray cmds, PrintWriter output, short ourMAC) {
		this.theRF = theRF;
		this.incoming = incoming;
		this.acks = acks;
		this.cmds = cmds;
		this.output = output;
		this.ourMAC = ourMAC;
	}

	@Override
	public void run() {
		while (true) {
			byte[] frame = theRF.receive();
			Packet packet = new Packet(frame);
			boolean isBroadcast = packet.getDest() == (short) -1;

			// check if packet's destination is for us
			if (packet.getDest() == ourMAC || isBroadcast) {
				if (packet.isAck()) {
					acks.add(packet.getSequenceNumber());
					if (cmds.get(0) != 0) {
						output.println("Receiver: Received Ack: " + packet);
					}
				}
				else {
					incoming.add(packet);
					if (cmds.get(0) != 0) {
						output.println("Receiver: Received Packet: " + packet);
					}
					if (!isBroadcast) {
						try {
							Thread.sleep(RF.aSIFSTime);
						}
						catch (InterruptedException e) {
							System.err.println("Error while putting thread to sleep");
						}
						if (!theRF.inUse()) {
							byte[] emptyArray = new byte[0];
							Packet ack = new Packet((short) 1, (short) 0, packet.getSequenceNumber(), ourMAC,
									packet.getSource(), emptyArray, 0);
							theRF.transmit(ack.getFrame());
							if (cmds.get(0) != 0) {
								output.println("Receiver: Ack sent");
							}

						}
					}

				}
			}

		}
	}

}
