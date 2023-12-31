package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import rf.RF;

/**
 * A tread class that takes the packet off the outgoing queue and transmits it
 * to the RF layer. Based off of 802.11 rules
 * 
 * @version 23.12.6
 * @author Matthew Zou, David Lybeck
 */
public class Sender implements Runnable {

	/**
	 * creates all the states sender can be in
	 */
	public enum State {
		awaitData, idleWait, busyDIFSWait, idleDIFSWait, awaitAck, slotWait
	};

	private static int timeoutTime = RF.aSlotTime * 15;
	private static int DIFSTime = RF.aSIFSTime + 2 * RF.aSlotTime;
	private static long beaconSendOffset = 2308; // time to create and send packet
	private int cwSize;
	private int count;
	private int retries;
	private RF theRF;
	private ArrayBlockingQueue<Packet> outgoing;
	private ArrayBlockingQueue<Integer> acks;
	private AtomicIntegerArray cmds;
	private PrintWriter output;
	private short ourMAC;
	private AtomicLong localOffset;
	private AtomicInteger status;
	private State myState;

	/**
	 * Constructor for sender
	 * 
	 * @param theRF       RF
	 * @param outgoing    ArrayBlockingQueue<Packet>
	 * @param acks        ArrayBlockingQueue<Integer>
	 * @param cmds        AtomicIntegerArray
	 * @param output      PrintWriter
	 * @param ourMAC      short
	 * @param localOffset AtomicLong
	 * @param status      AtomicIntege
	 */
	public Sender(RF theRF, ArrayBlockingQueue<Packet> outgoing, ArrayBlockingQueue<Integer> acks,
			AtomicIntegerArray cmds, PrintWriter output, short ourMAC, AtomicLong localOffset, AtomicInteger status) {
		this.cwSize = RF.aCWmin;
		this.count = (int) (Math.random() * (cwSize + 1));
		this.retries = 0;
		this.theRF = theRF;
		this.outgoing = outgoing;
		this.acks = acks;
		this.cmds = cmds;
		this.output = output;
		this.ourMAC = ourMAC;
		this.localOffset = localOffset;
		this.status = status;
		this.myState = State.awaitData;
	}

	/**
	 * Starts the sender class
	 */
	@Override
	public void run() {
		Packet packet = null;
		boolean isBroadcast = false;
		long beaconStartTime = System.currentTimeMillis();
		while (true) {
			switch (myState) {
			case awaitData:
				// check if beacon timer is over
				if ((System.currentTimeMillis() - beaconStartTime > cmds.get(2) * 1000) && cmds.get(2) > 0) {
					long validClockTime = getLocalTime() + beaconSendOffset;
					byte[] data = new byte[8];
					for (int i = 0; i < 8; i++) {
						data[i] = (byte) (validClockTime >> 56 - (8 * i));
					}
					packet = new Packet((short) 2, (short) 0, 0, ourMAC, (short) -1, data, 8);
					isBroadcast = true;
					if (cmds.get(0) == -1 || cmds.get(0) == -2) {
						output.println("Sender: Starting to send Beacon with time: " + validClockTime);
					}
					if (!theRF.inUse()) {
						myState = State.idleDIFSWait;
					}
					else {
						resetCW();
						myState = State.idleWait;
					}
				}
				// check if outgoing queue has packets to send
				else if (outgoing.size() > 0) {
					packet = outgoing.poll();
					isBroadcast = packet.getDest() == (short) -1;
					if (cmds.get(0) == -1) {
						output.println("Sender: Starting to send Data");
					}
					if (!theRF.inUse()) {
						myState = State.idleDIFSWait;
					}
					else {
						resetCW();
						myState = State.idleWait;
					}
				}
				// sleep to avoid busy wait
				else {
					sleep(20);
				}

				break;
			case idleDIFSWait:
				if (cmds.get(0) == -1) {
					output.println("Sender: Idle DIFS waiting starting at " + getLocalTime());
				}
				waitDIFS();
				if (cmds.get(0) == -1) {
					output.println("Sender: Idle DIFS waiting finished at " + getLocalTime());
				}
				if (!theRF.inUse()) {
					theRF.transmit(packet.getFrame());

					if (cmds.get(0) == -1 || cmds.get(0) == -2) {
						output.println("Sender: Transmited packet " + packet);
						output.println("Sender: Finished transmitting packet at time " + getLocalTime());
					}
					myState = State.awaitAck;
				}
				else {
					myState = State.idleWait;
				}
				break;
			case awaitAck:
				// if its a broadcast we don't wait for ack
				if (isBroadcast) {
					resetCW();
					retries = 0;
					if (packet.isBeacon()) {
						beaconStartTime = System.currentTimeMillis();
					}
					myState = State.awaitData;
					break;
				}
				if (cmds.get(0) == -1) {
					output.println("Sender: Awaiting Ack");
				}
				long startTime = System.currentTimeMillis();
				boolean timeout = true;
				// loop until timer expires or ack is received
				while (System.currentTimeMillis() - startTime < timeoutTime) {
					if (acks.size() > 0) {
						int sequenceNumber = acks.poll();
						if (sequenceNumber == packet.getSequenceNumber()) {
							// correct ack has been received
							timeout = false;
							status.set(4);
							if (cmds.get(0) == -1) {
								output.println("Sender: Ack received");
							}
							break;
						}
					}
					// Sleep to avoid busy wait
					sleep(20);
				}
				// ack not received
				if (timeout) {
					if (cmds.get(0) == -1) {
						output.println("Sender: Ack not received, timeout");
					}
					retries++;
					if (cmds.get(0) == -1) {
						output.println("Sender: Retry number set to " + retries);
					}
					// retry limit reached
					if (retries > RF.dot11RetryLimit) {
						if (cmds.get(0) == -1) {
							output.println("Sender: Retry limit reached");
						}
						status.set(5);
						resetCW();
						retries = 0;
						myState = State.awaitData;
					}
					else {
						// expand collision window
						cwSize = Math.min(RF.aCWmax, cwSize * 2);
						if (cmds.get(1) == 0) {
							count = (int) (Math.random() * (cwSize + 1));
						}
						else {
							count = cwSize;
						}
						if (cmds.get(0) == -1) {
							output.println(
									"Sender: Collission window size doubled to " + cwSize + ", Count set to " + count);
						}
						myState = State.idleWait;
					}
				}
				// ack received
				else {
					resetCW();
					retries = 0;
					myState = State.awaitData;
				}
				break;
			case idleWait:
				while (theRF.inUse()) {
					sleep(20);
				}
				myState = State.busyDIFSWait;
				break;
			case busyDIFSWait:
				if (cmds.get(0) == -1) {
					output.println("Sender: Busy DIFS waiting starting at " + getLocalTime());
				}
				waitDIFS();
				if (cmds.get(0) == -1) {
					output.println("Sender: Busy DIFS waiting finished at " + getLocalTime());
				}
				if (theRF.inUse()) {
					myState = State.idleWait;
				}
				else {
					myState = State.slotWait;
				}
				break;
			case slotWait:
				if (cmds.get(0) == -1) {
					output.println("Sender: Slot waiting with count " + count);
				}
				sleep(RF.aSlotTime);
				if (theRF.inUse()) {
					myState = State.idleWait;
				}
				else {
					if (count > 1) {
						count--;
					}
					// slot count finished
					else {
						if (retries > 0) {
							packet.setRetryFlag(true);
						}
						theRF.transmit(packet.getFrame());
						if (cmds.get(0) == -1 || cmds.get(0) == -2) {
							output.println("Sender: Transmited packet " + packet);
							output.println("Sender: Finished transmitting packet at time " + getLocalTime());
						}
						myState = State.awaitAck;
					}
				}
				break;
			default:
				if (cmds.get(0) == -1) {
					output.println("Unexpected state!");
				}

			}
		}
	}

	/**
	 * puts the sender thread to sleep
	 * 
	 * @param time int
	 */
	private void sleep(int time) {
		try {
			Thread.sleep(time);
		}
		catch (InterruptedException e) {
			System.err.println("Error while putting thread to sleep");
		}
	}

	/**
	 * Resets the collision window in the sender
	 */
	private void resetCW() {
		cwSize = RF.aCWmin;
		if (cmds.get(1) == 0) {
			count = (int) (Math.random() * (cwSize + 1));
		}
		else {
			count = cwSize;
		}

		if (cmds.get(0) == -1) {
			output.println("Sender: Collission window size set to " + cwSize + ", Count set to " + count);
		}
	}

	/**
	 * Waits DIFS time plus rounding according to local time
	 */
	private void waitDIFS() {
		long roundTime = 50 - getLocalTime() % 50;
		sleep(DIFSTime + (int) roundTime);
	}

	/**
	 * gets the local time including the offset from beacons
	 * 
	 * @return time long
	 */
	private long getLocalTime() {
		return theRF.clock() + localOffset.get();
	}

}
