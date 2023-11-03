package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

public class Receiver implements Runnable {
	
	private RF theRF; 
	private ArrayBlockingQueue<Packet> incoming;
	private ArrayBlockingQueue<Integer> acks;
	private PrintWriter output;
	
	public Receiver(RF theRF, ArrayBlockingQueue<Packet> incoming, ArrayBlockingQueue<Integer> acks, PrintWriter output) {
		this.theRF = theRF;
		this.incoming = incoming;
		this.acks = acks;
		this.output = output;
	}
	@Override
	public void run() {
		while(true) {
			byte[] frame = theRF.receive();
			Packet packet = new Packet(frame);
			if(packet.isAck()) {
				acks.add(packet.getSequenceNumber());
				output.println("Receiver: Received Ack: " + packet.getSequenceNumber());
			}
			else {
				incoming.add(packet);
				output.println("Receiver: Received Packet: " + packet);
				try {
					Thread.sleep(RF.aSIFSTime);
				} catch (InterruptedException e) {
					System.err.println("Error while putting thread to sleep");
				}
				if(!theRF.inUse()) {
					byte[] emptyArray = new byte[0];
					Packet ack = new Packet((short)1,(short) 0, packet.getSequenceNumber(), packet.getDest(), packet.getSource(), emptyArray, 0);
					theRF.transmit(ack.getFrame());
					output.println("Receiver: Ack sent");
				}
			}
			
		}
	}

}
