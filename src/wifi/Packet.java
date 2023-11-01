package wifi;

public class Packet {

	private byte[] frame;
	
	public Packet(byte[] frame) {
		this.frame = frame;
	}

	public Packet(short ourMac, short dest, byte[] data, int len) {
		int dataLength = Math.min(len, data.length);
		this.frame = new byte[dataLength + 10];

		short frameType = 0;
		short retryFlag = 0;
		int sequenceNumber = 0;
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
		int crc = calculateCRC(frame, 0, dataLength + 6); // Assuming 'calculateCRC' is your CRC calculation function.
		frame[dataLength + 6] = (byte) ((crc >> 24) & 0xFF);
		frame[dataLength + 7] = (byte) ((crc >> 16) & 0xFF);
		frame[dataLength + 8] = (byte) ((crc >> 8) & 0xFF);
		frame[dataLength + 9] = (byte) (crc & 0xFF);
	}
	
	public byte[] getFrame() {
		return frame;
	}
	
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

	private int calculateCRC(byte[] data, int start, int length) {
		int crc = 0xFFFFFFFF; // Initialize CRC to all 1's (32 bits)

		for (int i = start; i < start + length; i++) {
			crc ^= ((int) data[i] & 0xFF) << 24; // XOR with next byte

			for (int j = 0; j < 8; j++) {
				if ((crc & 0x80000000) != 0) {
					crc = (crc << 1) ^ 0x04C11DB7; // XOR with the IEEE 802.11 polynomial
				} else {
					crc <<= 1;
				}
			}
		}

		return crc;
	}
	
//	public static void main(String[] args) {
//		short ourMac = 319;
//	    short dest = 256;
//	    byte[] data = new byte[]{0x01, 0x02, 0x03};
//	    int len = data.length;
//
//	    Packet packet = new Packet(ourMac, dest, data, len);
//
//	}

}