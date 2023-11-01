package wifi;

/**
 * This class contains the information available from an incoming transmission.  In C, the
 * recvfrom() call returns information about the source and destination address through
 * parameters to the call, along with the incoming data itself.  We can't do that in Java,
 * so we'll use the same approach that the Java sockets libraries do:  Our recv() call will
 * take an object reference &mdash; a pointer to a Transmission instance &mdash; and will
 * write the data and address information into the Transmission object.
 * 
 * @author richards
 */

public class Transmission 
{
	private short sourceAddr;
	private short destAddr;
	private byte[] buf;


	/**
	 * Constructor for Transmission.
	 * @param sourceAddr  The transmission's source address
	 * @param destAddr    The transmission's destination address
	 * @param buf         The data carried by the transmission
	 */
	public Transmission(short sourceAddr, short destAddr, byte[] buf) {
		this.sourceAddr = sourceAddr;
		this.destAddr = destAddr;
		this.buf = buf;
	}

	/**
	 * Returns a reference to the buffer held by the Transmission instance.  Note that
	 * it does <i>not</i> return a copy of the buffer, it returns a link to the existing
	 * buffer.
	 * @return a reference to the data buffer
	 */
	public synchronized byte[] getBuf() {
		return buf;
	}

	/**
	 * Takes a reference to an array of bytes and sets it to be the buffer used by the
	 * Transmission instance.  Note that it does <i>not</i> copy <code>buf</code>, it
	 * links to it (shares it).
	 * @param buf the data buffer to set
	 */
	public synchronized void setBuf(byte[] buf) {
		this.buf = buf;
	}

	/**
	 * @return the destination address
	 */
	public synchronized short getDestAddr() {
		return destAddr;
	}

	/**
	 * @param destAddr the destination address to set
	 */
	public synchronized void setDestAddr(short destAddr) {
		this.destAddr = destAddr;
	}

	/**
	 * @return the source address
	 */
	public synchronized short getSourceAddr() {
		return sourceAddr;
	}

	/**
	 * @param sourceAddr the source address to set
	 */
	public synchronized void setSourceAddr(short sourceAddr) {
		this.sourceAddr = sourceAddr;
	}
}
