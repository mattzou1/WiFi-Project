package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

import rf.RF;

public class Sender implements Runnable {

	public enum State {
		awaitData, idleWait, busyDIFSWait, idleDIFSWait, awaitAck, slotWait
	};

	private static int timeoutTime = 2000;
	private static int DIFSTime = RF.aSIFSTime + 2 * RF.aSlotTime;
	private int cwSize;
	private int count;
	private int retries;
	private RF theRF;
	private ArrayBlockingQueue<Packet> outgoing;
	private ArrayBlockingQueue<Integer> acks;
	private AtomicIntegerArray cmds;
	private PrintWriter output;
	private State myState;

	public Sender(RF theRF, ArrayBlockingQueue<Packet> outgoing, ArrayBlockingQueue<Integer> acks,
			AtomicIntegerArray cmds, PrintWriter output) {
		resetCW();
		this.retries = 0;
		this.theRF = theRF;
		this.outgoing = outgoing;
		this.acks = acks;
		this.cmds = cmds;
		this.output = output;
		this.myState = State.awaitData;
	}

	@Override
	public void run() {
		Packet packet = null;
		boolean isBroadcast = false;
		while (true) {
			switch (myState) {
			case awaitData:
				if (outgoing.size() > 0) {
					packet = outgoing.poll();
					isBroadcast = packet.getDest() == (short) -1;
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
					myState = State.awaitData;
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
					cwSize = Math.min(RF.aCWmax, cwSize * 2);
					count = (int) (Math.random() * (cwSize + 1));
					if (cmds.get(0) != 0) {
						output.println("Sender: Collission window size set to " + cwSize + ", Count set to " + count);
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
						myState = State.busyDIFSWait;
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
		count = (int) (Math.random() * (cwSize + 1));
		if (cmds.get(0) != 0) {
			output.println("Sender: Collission window size set to " + cwSize + ", Count set to " + count);
		}
	}

}
