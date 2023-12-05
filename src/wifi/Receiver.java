package wifi;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import rf.RF;

public class Receiver implements Runnable {

	private static long beaconReceiveOffset = 0;
	private RF theRF;
	private ArrayBlockingQueue<Packet> incoming;
	private ArrayBlockingQueue<Integer> acks;
	private AtomicIntegerArray cmds;
	private AtomicInteger status;
	private PrintWriter output;
	private short ourMAC;
	private AtomicLong localOffset;
	private HashMap<Short, Integer> incomingSeqNums; // contains most recently used seqNum for ever destination

	public Receiver(RF theRF, ArrayBlockingQueue<Packet> incoming, ArrayBlockingQueue<Integer> acks,
			AtomicIntegerArray cmds, PrintWriter output, short ourMAC, AtomicLong localOffset) {
		this.theRF = theRF;
		this.incoming = incoming;
		this.acks = acks;
		this.cmds = cmds;
		this.output = output;
		this.ourMAC = ourMAC;
		this.localOffset = localOffset;
		this.incomingSeqNums = new HashMap<Short, Integer>();
	}

	private long getLocalTime() {
		return theRF.clock() + localOffset.get();
	}

	@Override
	public void run() {
		while (true) {
			byte[] frame = theRF.receive();
			if (cmds.get(0) == -1 || cmds.get(0) == -2) {
				output.println("	Receiver: Received Packet at: " + theRF.clock());
			}
			Packet packet = new Packet(frame);
			short dest = packet.getDest();
			boolean isBroadcast = dest == (short) -1;

			// check if packet's destination is for us
			if ((packet.getDest() == ourMAC || isBroadcast) && packet.isValid()) {
				if (packet.isAck()) {
					// add seqNum of the ack to acks queue
					acks.add(packet.getSequenceNumber());
					if (cmds.get(0) == -1) {
						output.println("Receiver: Received Ack: " + packet);
					}
				}
				else if (isBroadcast && packet.isBeacon()) {
					long incomingClockTime = 0;
					for (int i = 0; i < 8; i++) {
						incomingClockTime |= ((long) (packet.getData()[i] & 0xFF)) << (56 - (8 * i));
					}
					if (cmds.get(0) == -1 || cmds.get(0) == -2) {
						output.println("	Receiver: Beacon received with clock time of: " + incomingClockTime);
					}
					incomingClockTime += beaconReceiveOffset;
					long timeWhenCompared = theRF.clock();
					if (incomingClockTime > getLocalTime()) {
						localOffset.set(incomingClockTime - theRF.clock());
					}
					if (cmds.get(0) == -1 || cmds.get(0) == -2) {
						output.println("	Receiver: Processed beacon at time: " + timeWhenCompared
								+ "\n	Local offset : " + localOffset.get());
					}
				}
				else if (incoming.size() <= 4) {
					if (isBroadcast) {
						incoming.add(packet);
						if (cmds.get(0) == -1) {
							output.println("	Receiver: Received Packet: " + packet);
						}
					}
					else {
						int recvSeq = packet.getSequenceNumber();
						int currSeq = -1;
						if (incomingSeqNums.containsKey(dest)) {
							currSeq = incomingSeqNums.get(dest);
						}
						// Packet is not duplicate queue it
						if (recvSeq != currSeq) {
							incoming.add(packet);
							if (cmds.get(0) == -1) {
								output.println("	Receiver: Received Packet: " + packet);
							}
						}
						// If a seqNum is skipped print err
						if (recvSeq != currSeq + 1)
							output.println("Out of Order Sequence Number");
						// place new seqNum into hashmap of all received seqNums
						incomingSeqNums.put(dest, recvSeq);
						// if packet is not a broadcast, wait SIFS and send an ack;
						try {
							Thread.sleep(RF.aSIFSTime);
						}
						catch (InterruptedException e) {
							System.err.println("	Error while putting thread to sleep");
						}
						if (!theRF.inUse()) {
							byte[] emptyArray = new byte[0];
							Packet ack = new Packet((short) 1, (short) 0, packet.getSequenceNumber(), ourMAC,
									packet.getSource(), emptyArray, 0);
							theRF.transmit(ack.getFrame());
							if (cmds.get(0) == -1) {
								output.println("	Receiver: Ack sent");
							}
						}
					}

				}
				else {
					if (cmds.get(0) == -1) {
						output.println("Receiver: Incoming Queue size limit reached");
					}
				}
			}

		}
	}

}