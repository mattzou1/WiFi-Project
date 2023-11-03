package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

import rf.RF;

public class Sender implements Runnable {

	private static int timeoutTime = 5000;
	private static int DIFSTime = RF.aSIFSTime + 2 * RF.aSlotTime;
	private int cwSize;
	private int count;
	private RF theRF;
	private ArrayBlockingQueue<Packet> outgoing;
	private ArrayBlockingQueue<Integer> acks;
	private AtomicIntegerArray cmds;
	private PrintWriter output;

	public Sender(RF theRF, ArrayBlockingQueue<Packet> outgoing, ArrayBlockingQueue<Integer> acks,
			AtomicIntegerArray cmds, PrintWriter output) {
		this.theRF = theRF;
		this.outgoing = outgoing;
		this.acks = acks;
		this.cmds = cmds;
		this.output = output;
	}
	
	private void sleep(int time) {
		try {
			Thread.sleep(time);
		}
		catch (InterruptedException e) {
			System.err.println("Error while putting thread to sleep");
		}
	}

	@Override
	public void run() {
		while (true) {
			Packet packet = null;
			if (outgoing.size() > 0) {
				if (!theRF.inUse()) {
					// Channel is idle wait DIFS before sending
					if (cmds.get(0) != 0) {
						output.println("Sender: Idle DIFS waiting");
					}
					sleep(DIFSTime);
					if (!theRF.inUse()) {
						// transmit if channel is still idle
						packet = outgoing.poll();
						theRF.transmit(packet.getFrame());
						if (cmds.get(0) != 0) {
							output.println("Sender: Transmited packet: " + packet);
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
							if (cwSize < RF.aCWmax) {
								cwSize = Math.min(RF.aCWmax, cwSize * 2);
							}
							count = 1 + (int) (Math.random() * cwSize);
						}
						else {
							cwSize = RF.aCWmin;
						}
					}

				}
//				if(theRF.inUse()) {
//					// Channel is busy, set cwSize to min CW size
//					cwSize = RF.aCWmin;
//					// Set count to random number between 1 and cwSize
//					count = 1 + (int) (Math.random() * cwSize);
//
//					boolean packetSent = false;
//					while (!packetSent) {
//						// Wait for channel to become idle
//						while (theRF.inUse()) {
//							// Sleep for a short period to avoid busy-waiting
//							try {
//								Thread.sleep(20); // Sleep for 100 milliseconds
//							} catch (InterruptedException e) {
//								System.err.println("Error while putting thread to sleep");
//							}
//						}
//						// Busy DIFS wait
//						try {
//							Thread.sleep(DIFSTime); // Sleep for 100 milliseconds
//						} catch (InterruptedException e) {
//							System.err.println("Error while putting thread to sleep");
//						}
//						if(theRF.inUse()) {
//							continue;
//						}
//					}
//				}
			}
			else {
				// No packets on outgoing queue sleep for a bit
				sleep(20);
			}
		}

	}

}
