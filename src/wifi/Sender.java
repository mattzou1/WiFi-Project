package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import rf.RF;

public class Sender implements Runnable {

	public enum State {
		awaitData, idleWait, busyDIFSWait, idleDIFSWait, awaitAck, slotWait
	};

	private static int timeoutTime = 2000;
	private static int DIFSTime = RF.aSIFSTime + 2 * RF.aSlotTime;
	private static long beaconSendOffset = 0;
	private int cwSize;
	private int count;
	private int retries;
	private RF theRF;
	private ArrayBlockingQueue<Packet> outgoing;
	private ArrayBlockingQueue<Integer> acks;
	private AtomicIntegerArray cmds;
	private PrintWriter output;
	private short ourMAC;
	private AtomicLong clockTime;
	private State myState;
	

	public Sender(RF theRF, ArrayBlockingQueue<Packet> outgoing, ArrayBlockingQueue<Integer> acks,
			AtomicIntegerArray cmds, PrintWriter output, short ourMAC, AtomicLong clockTime) {
		this.cwSize = RF.aCWmin;
		this.count = (int) (Math.random() * (cwSize + 1));
		this.retries = 0;
		this.theRF = theRF;
		this.outgoing = outgoing;
		this.acks = acks;
		this.cmds = cmds;
		this.output = output;
		this.ourMAC = ourMAC;
		this.clockTime = clockTime;
		this.myState = State.awaitData;
	}

	@Override
	public void run() {
		Packet packet = null;
		boolean isBroadcast = false;
		long beaconStartTime = System.currentTimeMillis();
		while (true) {
			switch (myState) {
			case awaitData:
				//check if beacon timer is over
				if(System.currentTimeMillis() - beaconStartTime > cmds.get(2) * 1000) {
					long validClockTime = Math.max(theRF.clock(), clockTime.get()) + beaconSendOffset;
					byte[] data = new byte[8];
					for(int i = 0; i < 8; i++) {
						data[i] = (byte)(validClockTime >> 56 - (8 * i));
					}
					packet = new Packet((short) 2, (short) 0, 0, ourMAC, (short) -1, data, 8);
					isBroadcast = true;
					if (cmds.get(0) != 0) {
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
				//check if outgoing queue has packets to send
				else if (outgoing.size() > 0){
					packet = outgoing.poll();
					isBroadcast = packet.getDest() == (short) -1;
					if (cmds.get(0) != 0) {
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
				else {
					sleep(20);
				}
				
				break;
			case idleDIFSWait:
				if (cmds.get(0) != 0) {
					output.println("Sender: Idle DIFS waiting");
				}
				sleep(DIFSTime);
				if (!theRF.inUse()) {
					theRF.transmit(packet.getFrame());
					if (cmds.get(0) != 0) {
						output.println("Sender: Transmited packet: " + packet);
					}
					myState = State.awaitAck;
				}
				else {
					myState = State.idleWait;
				}
				break;
			case awaitAck:
				if (isBroadcast) {
					resetCW();
					retries = 0;
					beaconStartTime = System.currentTimeMillis();
					myState = State.awaitData;
					break;
				}
				if (cmds.get(0) != 0) {
					output.println("Sender: Awaiting Ack");
				}
				long startTime = System.currentTimeMillis();
				boolean timeout = true;
				// start timer
				while (System.currentTimeMillis() - startTime < timeoutTime) {
					if (acks.size() > 0) {
						int sequenceNumber = acks.poll();
						if (sequenceNumber == packet.getSequenceNumber()) {
							// correct ack has been received
							timeout = false;
							if (cmds.get(0) != 0) {
								output.println("Sender: Ack received");
							}
							break;
						}
					}
					// Sleep to avoid busy wait
					sleep(20);
				}
				if (timeout) {
					if (cmds.get(0) != 0) {
						output.println("Sender: Ack not received, timeout");
					}
					retries++;
					if (cmds.get(0) != 0) {
						output.println("Sender: Retry number set to " + retries);
					}
					if (retries > RF.dot11RetryLimit) {
						if (cmds.get(0) != 0) {
							output.println("Sender: Retry limit reached");
						}
						resetCW();
						retries = 0;
						myState = State.awaitData;
					}
					else {
						cwSize = Math.min(RF.aCWmax, cwSize * 2);
						if(cmds.get(1) == 0) {
							count = (int) (Math.random() * (cwSize + 1));
						}
						else {
							count = cwSize;
						}
						if (cmds.get(0) != 0) {
							output.println("Sender: Collission window size doubled to " + cwSize + ", Count set to " + count);
						}
						myState = State.idleWait;
					}
				}
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
				if (cmds.get(0) != 0) {
					output.println("Sender: Busy DIFS waiting");
				}
				sleep(DIFSTime);
				if (theRF.inUse()) {
					myState = State.idleWait;
				}
				else {
					myState = State.slotWait;
				}
				break;
			case slotWait:
				if (cmds.get(0) != 0) {
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
					else {
						if (retries > 0) {
							packet.setRetryFlag(true);
						}
						theRF.transmit(packet.getFrame());
						if (cmds.get(0) != 0) {
							output.println("Sender: Transmited packet " + packet);
						}
						myState = State.awaitAck;
					}
				}
				break;
			default:
				if (cmds.get(0) != 0) {
					output.println("Unexpected state!");
				}

			}
		}
	}

	private void sleep(int time) {
		try {
			Thread.sleep(time);
		}
		catch (InterruptedException e) {
			System.err.println("Error while putting thread to sleep");
		}
	}

	private void resetCW() {
		cwSize = RF.aCWmin;
		if(cmds.get(1) == 0) {
			count = (int) (Math.random() * (cwSize + 1));
		}
		else {
			count = cwSize;
		}
		
		if (cmds.get(0) != 0) {
			output.println("Sender: Collission window size set to " + cwSize + ", Count set to " + count);
		}
	}

}
