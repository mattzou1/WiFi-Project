package wifi;

import java.util.zip.CRC32;

import rf.RF;

/**
 * Stores data surrounded by a frame used out our 802.11 implementation. Contains bitwise operations to extract and edit data within the packet
 * 
 * @version 23.12.6
 * @author Matthew Zou, David Lybeck
 */
public class Packet {

	private byte[] frame;
	private int dataLength;

	/**
	 * Creates a packet fiven a byte[] frame
	 * @param frame byte[]
	 */
	public Packet(byte[] frame) {
		this.frame = frame;
		this.dataLength = frame.length - 10;
	}

	/**
	 * Packet created with all given parameters
	 * @param frameType short
	 * @param retryFlag short
	 * @param sequenceNumber int
	 * @param ourMac short
	 * @param dest short
	 * @param data byte[]
	 * @param len int
	 */
	public Packet(short frameType, short retryFlag, int sequenceNumber, short ourMac, short dest, byte[] data,
			int len) {
		//use len bytes or f len exceeds the size of the byte array, send as many bytes as data contains
		this.dataLength = Math.min(len, data.length);
		//use a max of 2038 bytes of data
		this.dataLength = Math.min(this.dataLength, RF.aMPDUMaximumLength - 10);
		this.frame = new byte[dataLength + 10];

		short control = 0;

		control |= (frameType & 0x07) << 13; // 3 bits for frame type, shifted to the left
		control |= (retryFlag & 0x01) << 12; // 1 bit for retry, shifted to the left
		control |= (sequenceNumber & 0xFFF); // 12 bits for sequence number

		// two control bytes
		frame[0] = (byte) (control >> 8);
		frame[1] = (byte) control;
		// two destination address bytes
		frame[2] = (byte) (dest >> 8);
		frame[3] = (byte) dest;
		// two source address bytes
		frame[4] = (byte) (ourMac >> 8);
		frame[5] = (byte) ourMac;
		// data bytes
		for (int i = 0; i < dataLength; i++) {
			frame[i + 6] = data[i];
		}
		// CRC bytes
		int crc = calculateCRC(frame, 0, dataLength + 6); 
		frame[dataLength + 6] = (byte) ((crc >> 24) & 0xFF);
		frame[dataLength + 7] = (byte) ((crc >> 16) & 0xFF);
		frame[dataLength + 8] = (byte) ((crc >> 8) & 0xFF);
		frame[dataLength + 9] = (byte) (crc & 0xFF);
	}

	/**
	 * Gets the destination of the packet as a short
	 * @return destination short
	 */
	public short getDest() {
		return (short) (((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF));
	}

	/**
	 * gets the source from the packet as a short
	 * @return source short
	 */
	public short getSource() {
		return (short) (((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF));
	}

	/**
	 * gets the data from the packet as a byte[]
	 * @return data byte[]
	 */
	public byte[] getData() {
		byte[] data = new byte[dataLength];
		for (int i = 0; i < dataLength; i++) {
			data[i] = frame[i + 6];
		}
		return data;
	}

	/**
	 * gets the length of the data from the packet
	 * @return length int
	 */
	public int getDataLength() {
		return dataLength;
	}

	/**
	 * gets the everything from the packet
	 * @return frame byte[]
	 */
	public byte[] getFrame() {
		return frame;
	}

	/**
	 * checks if the packet is an ack packet
	 * @return true if it is an ack
	 */
	public boolean isAck() {
		short frameType = (short) ((frame[0] >> 5) & 0x07);
		return frameType == 1;
	}
	
	/**
	 * checks if the packet is a beacon
	 * @return true if the packet is a beacon
	 */
	public boolean isBeacon() {
		short frameType = (short) ((frame[0] >> 5) & 0x07);
		return frameType == 2;
	}

	/**
	 * gets the sequence number of the packet
	 * @return sequence number int
	 */
	public int getSequenceNumber() {
		int controlBytes = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
		int sequenceNumber = controlBytes & 0x0FFF;
		return sequenceNumber;
	}
	
	/**
	 * sets the retry flag for this packet
	 * @param isRetry boolean
	 */
	public void setRetryFlag(boolean isRetry) {
		if (isRetry) {
	        frame[0] |= (1 << 4);
	    }
	}
	
	/**
	 * checks the packet using checksums
	 * @return true if it is a valid packet
	 */
	public boolean isValid() {
		int crc = calculateCRC(frame, 0, dataLength + 6); 
		int frameCrc =
			    ((frame[dataLength + 6] & 0xFF) << 24) |
			    ((frame[dataLength + 7] & 0xFF) << 16) |
			    ((frame[dataLength + 8] & 0xFF) << 8) |
			    (frame[dataLength + 9] & 0xFF);
		if(crc == frameCrc) {
			return true;
		}
		return false;
	}

	/**
	 * toString method for the packet class
	 * @return String
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		for (byte b : frame) {
			int unsigned = b & 0xFF;
			sb.append(" ").append(unsigned);
		}
		sb.append(" ]");
		return sb.toString();
	}
	
	/**
	 * Calculates the checksum for the packet
	 * @param data byte[]
	 * @param start int
	 * @param length int
	 * @return checksum int
	 */
	private int calculateCRC(byte[] data, int start, int length) {
		CRC32 crc32 = new CRC32();
        crc32.update(data, start, length);
        return (int) crc32.getValue();
	}

}
