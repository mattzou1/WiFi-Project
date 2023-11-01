package wifi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

/**
 * This class acts as a thin layer between the GUI client code and the Java-based
 * 802.11~ layer.  (There's a similar layer that mediates between the Java GUI code
 * and a C++ implementation of the 802.11~ project.)  See {@link GUIClientInterface} 
 * for full descriptions of these routines.
 * 
 * @author richards
 */

public class JavaGUIAdapter implements GUIClientInterface 
{
	private static Dot11Interface theDot11Layer;
	private static CircularByteBuffer cbb;
	private static BufferedReader reader;

	/**
	 * An array of addresses to use for the "send" buttons in the GUI.
	 * @return An array of MAC addresses assigned to buttons by the GUI.
	 */
	public short[] getDefaultAddrs() {
		short[] temp = {101, 201, 301, 401, 501, 601, 701, 801, 901};
		return temp;
	}

	/**
	 * Create an instance of the 802.11~ layer.  It wraps a PrintWriter around a
	 * BufferedReader that's wrapped around a CircularByteBuffer (whew!) so that
	 * we can read the text that the 802.11~ layer writes to the stream and 
	 * display it in the GUI's window.
	 * 
	 * @param MACaddr  The MAC address passed to the 802.11~ constructor.
	 * @return Returns 0 on success, -1 if an error occurs.
	 */
	public int initializeLinkLayer(short MACaddr) {

		try {
			cbb = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE);
			reader = new BufferedReader(new InputStreamReader(cbb.getInputStream()));
			theDot11Layer = new LinkLayer(MACaddr, new PrintWriter(cbb.getOutputStream(), true));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			return -1;
		}
		return 0;
	}

	/**
	 * This method calls the 802.11~ layer's recv() method, which should block until
	 * data arrives.  It then builds an array of bytes consisting of the the sender's
	 * MAC address followed by the data from the recv() call.  (This may seem odd, but
	 * the approach is easy to support on both the C++ and Java side.)
	 * @return An array of bytes containing MAC addresses and data
	 */
	public byte[] watchForIncomingData() {
		// Create a Transmission object, and pass it to the recv() call
		byte[] buf = new byte[2048];
		Transmission t = new Transmission((short)0, (short)0, buf);
		int result = theDot11Layer.recv(t); 

		// See if there was any data in the transmission
		int dataLen = 0;
		if (result > 0) {
			dataLen = result;
		}

		// Build a byte array, fill it with the source address and data,
		// and return the whole shebang.
		byte[] data = new byte[dataLen + 2]; 
		data[0] = (byte) ((t.getSourceAddr() >>> 8) & 0xFF);
		data[1] = (byte) (t.getSourceAddr() & 0xFF);
		System.arraycopy(t.getBuf(), 0, data, 2, dataLen);
		return data;
	}

	/**
	 * Wrapper around the 802.11~ layer's send routine.
	 * @param dest  The destination MAC address
	 * @param payload  The data to send
	 * @return Returns the value returned by the linklayer's <code>send()</code> method.
	 */
	public int sendOutgoingData(short dest, byte[] payload) {
		return theDot11Layer.send(dest, payload, payload.length);
	}


	/**
	 * This routine pulls text from the stream to which the 802.11~ layer is writing
	 * and returns any new text as an array of bytes.
	 * @return An array of bytes representing characters sent to output stream since last call.
	 */
	public byte[] pollForStreamOutput() {
		String msg = "";
		try {
			while (reader.ready()) {
				msg += reader.readLine() + "\n";
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return msg.getBytes();
	}


	/**
	 * The GUI calls this when the user asks to pass command info to the 802.11~ layer.
	 * @param command  Specifies the command to send
	 * @param value    The value passed with the command
	 * @return Returns the value returned by the linklayer's <code>command()</code> method.
	 */
	public int sendCommand(int command, int value) {
		return theDot11Layer.command(command, value);
	}

}
