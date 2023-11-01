package wifi;

import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

public class Sender implements Runnable {
	
	private RF theRF; 
	private ArrayBlockingQueue<Packet> outgoing;
	
	public Sender(RF theRF, ArrayBlockingQueue<Packet> outgoing) {
		this.theRF = theRF;
		this.outgoing = outgoing;
	}

	@Override
	public void run() {
		while(true) {
			if(!theRF.inUse() && outgoing.size() > 0) {
				Packet packet = outgoing.poll();
				theRF.transmit(packet.getFrame());
				System.out.println("Transmited packet: " + packet);
			}
			else {
				try {
					Thread.sleep(1000);
				} 
				catch (InterruptedException e) {
					System.err.println("Error while putting thread to sleep");
				}
			}
		}
		
	}

}
