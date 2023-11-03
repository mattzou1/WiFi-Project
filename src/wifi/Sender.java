package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

public class Sender implements Runnable {

	private static int DIFSTime = RF.aSIFSTime + 2 * RF.aSlotTime;
	private int cwSize;
	private int count;
	private RF theRF;
	private ArrayBlockingQueue<Packet> outgoing;
	private ArrayBlockingQueue<Integer> acks;
	private PrintWriter output;

	public Sender(RF theRF, ArrayBlockingQueue<Packet> outgoing, ArrayBlockingQueue<Integer> acks, PrintWriter output) {
		this.theRF = theRF;
		this.outgoing = outgoing;
		this.acks = acks;
		this.output = output;
	}

	@Override
	public void run() {
		while (true) {
			Packet packet = null;
			if (outgoing.size() > 0) {
				if (!theRF.inUse()) {
					// Channel is idle wait DIFS before sending
					try {
						Thread.sleep(DIFSTime);
					} catch (InterruptedException e) {
						System.err.println("Error while putting thread to sleep");
					}
					if (!theRF.inUse()) {
						// transmit if channel is still idle
						packet = outgoing.poll();
						theRF.transmit(packet.getFrame());
						output.println("Sender: Transmited packet: " + packet);
						long startTime = System.currentTimeMillis();
						long timeoutMillis = 5000;
						boolean timeout = true;
						//start timer
						while (System.currentTimeMillis() - startTime < timeoutMillis) {
							if (acks.size() > 0) {
								int sequenceNumber = acks.poll();
								if (sequenceNumber == packet.getSequenceNumber()) {
									//correct ack has been received
									timeout = false;
									output.println("Sender: Ack received");
									break;
								}
							}
							//Sleep to avoid busy wait
							try {
								Thread.sleep(20);
							} catch (InterruptedException e) {
								System.err.println("Error while putting thread to sleep");
							}
						}
						if (timeout) {
							output.println("Sender: Ack not received");
							if(cwSize < RF.aCWmax) {
								cwSize = Math.min(RF.aCWmax, cwSize * 2);
							}
							count = 1 + (int) (Math.random() * cwSize);
						} else {
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
			} else {
				// No packets on outgoing queue sleep for a bit
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					System.err.println("Error while putting thread to sleep");
				}
			}
		}

	}

}
