package wifi;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import rf.RF;

/**
 * A thread class that looks for incoming packets on the RF layer and handles
 * them accordingly. Sends ACKS for those received packets.
 * 
 * @version 23.12.6
 * @author Matthew Zou, David Lybeck
 */
public class Receiver implements Runnable {

	private static long beaconReceiveOffset = 0;
	private RF theRF;
	private ArrayBlockingQueue<Packet> incoming;
	private ArrayBlockingQueue<Integer> acks;
	private AtomicIntegerArray cmds;
	private PrintWriter output;
	private short ourMAC;
	private AtomicLong localOffset;
	private AtomicInteger status;
	private HashMap<Short, Integer> incomingSeqNums; // contains most recently used seqNum for ever destination

	/**
	 * Creates a receiver
	 * 
	 * @param theRF       RF
	 * @param incoming    ArrayBlockingQueue<Packet>
	 * @param acks        ArrayBlockingQueue<Integer>
	 * @param cmds        AtomicIntegerArray cmds
	 * @param output      PrintWriter
	 * @param ourMAC      short
	 * @param localOffset AtomicLong
	 * @param status      AtomicInteger
	 */
	public Receiver(RF theRF, ArrayBlockingQueue<Packet> incoming, ArrayBlockingQueue<Integer> acks,
			AtomicIntegerArray cmds, PrintWriter output, short ourMAC, AtomicLong localOffset, AtomicInteger status) {
		this.theRF = theRF;
		this.incoming = incoming;
		this.acks = acks;
		this.cmds = cmds;
		this.output = output;
		this.ourMAC = ourMAC;
		this.localOffset = localOffset;
		this.status = status;
		this.incomingSeqNums = new HashMap<Short, Integer>();
	}

	/**
	 * gets the local time (including the offset from beacons)
	 * 
	 * @return
	 */
	private long getLocalTime() {
		return theRF.clock() + localOffset.get();
	}

	/**
	 * starts the reciever
	 */
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

			// check if packet's destination is for us and checksum
			if ((packet.getDest() == ourMAC || isBroadcast) && packet.isValid()) {
				// check if packet is ack
				if (packet.isAck()) {
					// add seqNum of the ack to acks queue
					acks.add(packet.getSequenceNumber());
					if (cmds.get(0) == -1) {
						output.println("Receiver: Received Ack: " + packet);
					}
				}
				// check if packet is a beacon
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
						output.println("	Receiver: Local Time: " + getLocalTime());
					}
				}
				// check if incoming queue is full
				else if (incoming.size() <= 4) {
					// if its a broadcast add to incoming queue
					if (isBroadcast) {
						incoming.add(packet);
						if (cmds.get(0) == -1) {
							output.println("	Receiver: Received Broadcast: " + packet);
						}
					}
					// not a broadcast
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
								output.println("	Receiver: Received Message: " + packet);
							}
							// If a seqNum is skipped print err
							if (recvSeq != currSeq + 1){
								output.println("Out of Order Sequence Number");
							}
						}
						
						// place new seqNum into hashmap of all received seqNums
						incomingSeqNums.put(dest, recvSeq);
						// if packet is not a broadcast, wait SIFS and send an ack;
						try {
							Thread.sleep(RF.aSIFSTime);
						}
						catch (InterruptedException e) {
							status.set(2);
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
						else {
							if (cmds.get(0) == -1) {
								output.println("	Receiver: Couldn't send Ack!!");
							}
						}
					}

				}
				else {
					if (cmds.get(0) == -1) {
						output.println("	Receiver: Incoming Queue size limit reached");
					}
				}
			}
			else {
				if(!packet.isValid()) {
					if (cmds.get(0) == -1) {
						output.println("	Receiver: Checksum failed");
					}
				}
				if(!(packet.getDest() == ourMAC || isBroadcast)) {
					if (cmds.get(0) == -1) {
						output.println("	Receiver: Wrong Destination");
					}
				}
			}

		}
	}

}