package wifi;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;
import java.util.Scanner;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;



/**
 * The WiFiClient class provides a GUI for interacting with 802.11~ implementations.  It
 * can be used to drive either Java-based or C++-based link layers.  (Use a CppGUIAdapter
 * to interface this with C++ code, and a JavaGUIAdapter to drive Java code.)
 * Care is taken to ensure that all GUI-servicing operations occur in the event-dispatching
 * thread while all interactions with 802.11~ code occur in separate threads.  Thus the
 * constructor doesn't actually create the GUI &mdash; that happens in buildGUI() which is
 * called from the client's run() method.  (The run() method also starts up a thread to 
 * watch for arriving packets from the 802.11~ layer.)  The client's run method is invoked 
 * by main via SwingUtilities.invokeAndWait() to ensure it's executed on the event-dispatching 
 * thread.
 * 
 * A complete Java implementation looks like:
 * <blockquote><pre>
 * WiFiClient
 * JavaGUIAdapter
 * LinkLayer (802.11~ implementation)
 * RF
 * </pre></blockquote>
 * 
 * A complete C++ implementation is messier, due to the need for additional layers to 
 * mediate between the Java and C++ code:
 * <blockquote><pre>
 * WiFiClient (Java)
 * CppGUIAdapter (Java)
 * cppStubs (C++)
 * linklayer (802.11~ implementation in C++)
 * RF (C++ JNI layer)
 * RF (Java implementation of RF layer)
 * </pre></blockquote>
 * 
 * See the project documentation for additional information on compiling an 802.11~ project.
 * 
 * @author Brad Richards
 * @version 1.2
 *
 */

public class WiFiClient implements ActionListener, Runnable 
{
	protected JScrollPane textPane;     // Holds the message text display
	protected JTextArea inputBox;       // The message text display itself
	// Text is collected in a StringBuilder as well as the JTextArea.  This allows us to
	// continue buffering text in the StringBuilder even when the window is paused.
	protected JTextArea display;        // The output text display
	protected StringBuffer outputText = new StringBuffer(); // Holds display text
	protected JFrame frame;             // The frame that holds the display and key panels
	protected JButton[] ctrlButtons;    // Has to be field so listener can access them
	protected JButton[] sendButtons;    // Has to be field so listener can access them
	protected short[] sendAddrs;
	protected JLabel status;            // Has to be field so setter can access it
	protected boolean paused = false;   // Are we paused?

	protected short MACaddr;             // This station's address
	private GUIClientInterface theLinkLayer;// The link layer we're "driving"

	protected static int NUM_CTRL_BUTTONS = 4;
	protected static int NUM_SEND_BUTTONS = 10;

	protected static final int COMMAND = 0;
	protected static final int CLEAR = 1;
	protected static final int PAUSE = 2;
	protected static final int SAVE = 3;



	/**
	 * The constructor builds the GUI and prepares it for use.
	 */
	public WiFiClient(short MACaddr, GUIClientInterface theLinkLayer) {
		this.MACaddr = MACaddr;
		this.theLinkLayer = theLinkLayer;
	}

	/**
	 * Creates all of the GUI components and registers the event handlers.
	 * This method should be invoked on the event-dispatching thread.
	 */
	private void buildGUI() {
		ctrlButtons = new JButton[NUM_CTRL_BUTTONS];
		sendButtons = new JButton[NUM_SEND_BUTTONS];
		sendAddrs = new short[NUM_SEND_BUTTONS];

		// Build the overall frame
		frame = new JFrame("802.11~ Client [MAC "+MACaddr+"]");
		frame.setBackground(Color.LIGHT_GRAY);
		frame.setSize(700,500);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		// Text input is in the NORTH panel of border layout

		JPanel sendingPanel = new JPanel();
		sendingPanel.setLayout(new GridLayout(3,0));
		sendingPanel.add(new JLabel(" Enter text below.  Press a button to transmit text to specified destination."));  // Label's on top

		inputBox = new JTextArea(2, 0);
		inputBox.setEditable(true);
		inputBox.setLineWrap(true);
		inputBox.setWrapStyleWord(true);
		inputBox.setFont(new Font("Courier", Font.PLAIN, 14));
		JPanel inputBoxPanel = new JPanel();
		inputBoxPanel.add(inputBox);
		inputBox.setText("This is a sample text message.");
		sendingPanel.add(inputBox);               // Text field's next

		JPanel sendButtonPanel = new JPanel();
		sendButtonPanel.setLayout(new GridLayout(0, 5));
		sendButtons[0] = new JButton("Bcast");
		sendButtonPanel.add(sendButtons[0]);
		sendButtons[0].addActionListener(this);
		sendAddrs[0] = (short)-1;
		short[] defaults = theLinkLayer.getDefaultAddrs();
		for(int i=1; i<NUM_SEND_BUTTONS; i++) {
			if (i > defaults.length || defaults[i-1] == Short.MIN_VALUE) { // Leave "blank"
				sendButtons[i] = new JButton("MAC "+i);
				sendAddrs[i] = Short.MIN_VALUE;
			}
			else {  // Take from defaults
				sendButtons[i] = new JButton("MAC "+defaults[i-1]);
				sendAddrs[i] = defaults[i-1];
			}
			sendButtonPanel.add(sendButtons[i]);
			sendButtons[i].addActionListener(this);
		}
		sendingPanel.add(sendButtonPanel);        // And, finally, the sending buttons
		frame.add(sendingPanel, BorderLayout.NORTH);

		// Text is displayed in a scrolling pane in the CENTER of the border layout
		display = new JTextArea();
		display.setEditable(false);
		display.setLineWrap(true);
		display.setWrapStyleWord(true);
		display.setFont(new Font("Courier", Font.PLAIN, 14));
		textPane = new JScrollPane(display); // Wrap TextArea in a ScrollPane

		frame.add(textPane, BorderLayout.CENTER);

		// Build a panel to house the three ctrlButtons
		JPanel controls = new JPanel();
		controls.setLayout(new GridLayout(0, NUM_CTRL_BUTTONS));

		// Create all of the ctrlButtons and register this object as listener
		// for each.
		ctrlButtons[COMMAND] = new JButton("Command"); 
		ctrlButtons[CLEAR] = new JButton("Clear");       
		if (paused)
			ctrlButtons[PAUSE] = new JButton("Resume"); 
		else 
			ctrlButtons[PAUSE] = new JButton("Pause"); 
		ctrlButtons[SAVE] = new JButton("Save");
		for(int i=0; i<ctrlButtons.length; i++) {
			controls.add(ctrlButtons[i]);
			ctrlButtons[i].addActionListener(this);
		}

		frame.add(controls, BorderLayout.SOUTH);
		frame.setVisible(true);

		// Now that frame's set up, go ahead and initialize link layer
		theLinkLayer.initializeLinkLayer(MACaddr);
	}



	/** 
	 * Handler for button-press events.  Users should never need to call
	 * this directly.
	 */
	public void actionPerformed(ActionEvent e) {
		for(int i=0; i<NUM_SEND_BUTTONS; i++) {
			if (e.getSource() == sendButtons[i]) {
				// If shift was held, it means we redo the button
				if ((e.getModifiers() & ActionEvent.SHIFT_MASK) > 0) {
					String inputString = 
							JOptionPane.showInputDialog(null, 
									"Please enter MAC address for this button:",
									"Set Address",
									JOptionPane.QUESTION_MESSAGE);  
					if (inputString != null) {
						Scanner s = new Scanner(inputString);
						if (s.hasNextShort()) {
							sendAddrs[i] = s.nextShort();
							this.addText("Mapping button to MAC address "+sendAddrs[i]+"\n");
							sendButtons[i].setText("MAC "+sendAddrs[i]);
						}
						else {
							this.addText("That wasn't a valid address.\n");
						}
					}
				}
				else {
					byte[] msg = inputBox.getText().getBytes();
					this.addText("Sending text message to MAC "+sendAddrs[i]+": \"");
					this.addText(inputBox.getText()+"\"\n");
					int result = theLinkLayer.sendOutgoingData(sendAddrs[i], msg);
					if (result != msg.length)
					{
						this.addText("Sent "+result+" bytes, but message was "+msg.length+"!\n");
					}
				}
				return;  // We found the button
			}
		}

		// Wasn't a send button.  Check the control buttons.

		// If it's the COMMAND button, prompt the user for a command integer
		// and value.  (The value defaults to 0.)  Pass the pair of integers
		// to the 802.11~ layer and report the result to the output panel.
		if (e.getSource() == ctrlButtons[COMMAND]) {
			String inputString = 
					JOptionPane.showInputDialog(null, 
							"Enter command and value, separated by spaces",
							"Enter Command",
							JOptionPane.QUESTION_MESSAGE);  
			if (inputString != null) {
				Scanner s = new Scanner(inputString);
				int value = 0;
				if (s.hasNextInt()) {
					int command = s.nextInt();
					if (s.hasNextInt())
						value = s.nextInt();
					int result = theLinkLayer.sendCommand(command, value);
					this.addText("Command to 802.11~ layer ("+command+", "+value+") returned "+result+"\n");
				}
				else {
					this.addText("Not a valid command.");
				}
			}
		}
		// Clear deletes the text from the buffer and resets the display
		else if (e.getSource() == ctrlButtons[CLEAR]) { // CLEAR
			outputText.delete(0, outputText.length());   
			display.setText("");
			textPane.getVerticalScrollBar().setValue(Integer.MAX_VALUE);
		}
		// Pause negates the paused flag, which controls whether text added
		// to the display is "posted" immediately.  It also changes the text
		// on the button.
		else if (e.getSource() == ctrlButtons[PAUSE]) {// PAUSE
			paused = !paused;
			if (paused) {
				ctrlButtons[PAUSE].setText("Resume");
			}
			else {
				ctrlButtons[PAUSE].setText("Pause");
				display.setText(outputText.toString());
				textPane.getVerticalScrollBar().setValue(Integer.MAX_VALUE);
			}
		}
		else if (e.getSource() == ctrlButtons[SAVE])
		{
			saveToFile();
		}
	}


	/**
	 * Call this to append text to the scrolling output pane.  No newlines are added,
	 * so be sure to include a "\n" where desired.  Text is collected in a StringBuffer
	 * (outputText) as well as the JTextArea (display).  This allows us to continue 
	 * buffering text even when the window output is paused.
	 * 
	 * @param msg  Text to add to the scrolling pane
	 */
	public synchronized void addText(String msg) {

		outputText.append(msg);   
		// Setting the scroll bar's position sometimes causes a mysterious exception
		// to be thrown.  If it happens, pause while the output collects in outputText.
		// When the user resumes, all of the outputText will be dumped into the display
		// pane again.
		try {
			if (!paused) { 
				display.append(msg); 
				// textPane.getVerticalScrollBar().setValue(Integer.MAX_VALUE);
			}
		} catch (RuntimeException e) {
			System.err.println("Exception in addText() -- pausing output");
			paused = true;
			ctrlButtons[PAUSE].setText("Resume");
		}
	}   

	/**
	 * Prompts user to select an output file, then writes all text from the
	 * scrolling pane to the file.
	 */
	private void saveToFile() {
		File outputFile = null;
		JFileChooser chooser = new JFileChooser();
		int returnVal = chooser.showSaveDialog(null);
		if(returnVal == JFileChooser.APPROVE_OPTION) {
			outputFile = chooser.getSelectedFile();
			PrintWriter writer;
			try {
				writer = new PrintWriter(new FileWriter(outputFile));
				writer.println(display.getText());
				writer.close();
			} catch (IOException e) {
				outputText.append("Error writing to file!!");
			}
		} 
	}


	/**
	 * The run method should be executed by the event-dispatching thread.  it creates
	 * the GUI and starts a thread to watch for packets arriving for this station.
	 */
	public void run() { 
		buildGUI();
		(new Thread(new StreamWatcher(this))).start();
	}


	/**
	 * We need this inner class to wrap up the code that watches for arriving packets.
	 * We can't run it in the event-dispatching thread, so this loop can't just go in
	 * the run() method.
	 * 
	 * @author Brad Richards
	 */
	class StreamWatcher implements Runnable {
		WiFiClient display;

		public StreamWatcher(WiFiClient display) { this.display = display; }

		/**
		 * Block and wait for incoming transmissions.  Repeat.
		 */
		public void run() {
			for(;;) {
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					// Do nothing if awakened early
				}   
				byte[] bytes = theLinkLayer.watchForIncomingData();
				if (bytes != null && bytes.length >= 2) {
					int tmp = ((int)bytes[0]) & 0xFF;
					tmp = (tmp << 8) | (((int)bytes[1]) & 0xFF);
					short srcAddr = (short)tmp;
					String payload = new String(bytes, 2, bytes.length-2);
					display.addText("From "+srcAddr+": \""+payload+"\"\n");
				}
			}
		}

	}



	/**
	 * The main method selects a MAC address, creates a WiFiClient GUI and associates
	 * it with the link layer implementation, then waits in an infinite loop watching
	 * for stream output from the link layer and routing into the GUI display.
	 */

	public static void main(String[] args) {
		Random rand = new Random();
		short mac;

		// Take MAC on command-line if it's available, or create a random MAC

		if (args.length > 0) {
			mac = (new Scanner(args[0]).nextShort());
			System.out.println("Using MAC address of "+mac+" as requested.");
		}
		else {
			mac = (short)(rand.nextInt(100)+701);
			System.out.println("Using a random MAC address: "+mac);
		}

		// Use the CppGUIAdapter for a C++-based project, JavaGUIAdapter for Java-based project

		//    GUIClientInterface linkLayer = new CppGUIAdapter();
		GUIClientInterface linkLayer = new JavaGUIAdapter();
		WiFiClient display = new WiFiClient(mac, linkLayer);

		// This mechanism causes the GUI to be created by the event-dispatching thread

		try {
			SwingUtilities.invokeAndWait(display);
		} catch (Exception e1) {
			System.err.println("Yikes!  Something went wrong when invoking WiFiClient's run() method:");
			e1.printStackTrace();
		}

		// Remind the user how to modify buttons

		display.addText("Shift-click a button to change its MAC address.\n");

		// Run forever, watching for input from the link layer and adding it to the GUI's
		// text display window.

		for(;;) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// Do nothing if awakened early
			}   
			byte[] bytes = linkLayer.pollForStreamOutput();
			if (bytes != null) {
				String output = new String(bytes, 0, bytes.length);
				display.addText(output);
			}
		}
	}

}


