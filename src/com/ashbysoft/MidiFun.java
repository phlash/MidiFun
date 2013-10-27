package com.ashbysoft;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;

public class MidiFun implements Receiver, SysexLoader {

	private MidiDevice hwIn = null, hwOut = null;

	private Receiver rOut = null;

	private boolean verb = false;

	private MidiDecoder dec = new MidiDecoder(this);

	private HashMap<String, SysexModule> mods = new HashMap<String, SysexModule>();

	public static void main(String[] args) {
		MidiFun me = new MidiFun();
		File dotrc = new File(System.getProperty("user.home") + "/.midifunrc");
		ArrayList<String> allargs = new ArrayList<String>();
		for (int i=0; i<args.length; i++) {
			allargs.add(args[i]);
		}
		if (dotrc.canRead()) {
			try {
				BufferedReader r = new BufferedReader(new FileReader(dotrc));
				String line;
				while ((line = r.readLine()) != null) {
					line = line.trim();
					if (!line.startsWith("#") && line.length()>0) {
						allargs.add(line);
					}
				}
				r.close();
			} catch (IOException e) {
			}
		}
		me.run(allargs);
	}

	private void run(List<String> args) {
		if (!findDevices()) {
			System.err.println("Cannot find any usable MIDI devices..");
			System.exit(0);
		}
		if (openHardwareDevices()) {
			System.out.println("Press <Return> to quit, i<Return> to send ID request, ? for help.");
			try {
				BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
				String cmd;
				do {
					if (args.size()>0) {
						cmd = args.remove(0);
					} else {
						cmd = r.readLine();
					}
					handleCommand(cmd);
				} while (cmd.length() > 0);
				System.out.println("Done.");
			} catch (IOException e) {
			}
			stop();
		} else {
			System.err.println("Unable to open hardware MIDI port");
			System.exit(0);
		}
	}
	
	private boolean findDevices() {
		MidiDevice.Info[] devs = MidiSystem.getMidiDeviceInfo();
		for (int i = 0; devs != null && i < devs.length; i++) {
			System.out.println("DEV[" + i + "]: " + devs[i].getName() + '/' + devs[i].getDescription() + '/' + devs[i].getVendor()
					+ '/' + devs[i].getVersion());
			try {
				MidiDevice dev = MidiSystem.getMidiDevice(devs[i]);
				System.out.println(" max tx: " + dev.getMaxTransmitters() + " max rx: " +
					dev.getMaxReceivers());
				// Detect a hardware interface..
				if (!(dev instanceof Synthesizer) && !(dev instanceof Sequencer)) {
					if (hwIn==null && dev.getMaxTransmitters()!=0) {
						hwIn = dev;
						System.out.println("  INPUT");
					}
					if (hwOut==null && dev.getMaxReceivers()!=0) {
						hwOut = dev;
						System.out.println("  OUTPUT");
					}
				}
			} catch (Exception e) {
				System.out.println(" unopenable: "+e);
			}
		}
		return hwIn!=null;
	}

	private boolean openHardwareDevices() {
		try {
			hwIn.open();
			Transmitter tx = hwIn.getTransmitter();
			tx.setReceiver(this);
			if (hwOut!=null) {
				hwOut.open();
				rOut = hwOut.getReceiver();
			}
			return true;
		} catch (MidiUnavailableException e) {
			e.printStackTrace();
		}
		return false;
	}

	private void stop() {
		if (hwIn != null)
			hwIn.close();
		if (hwOut != null)
			hwOut.close();
	}

	private void handleCommand(String cmd) {
		if (cmd.length() == 0) {
			// quit...
		} else if ("i".equals(cmd)) {
			// ID command
			sendID();
		} else if ("v".equals(cmd)) {
			verb = !verb;
			System.out.println("verbose="+verb);
		} else if (cmd.startsWith("load")) {
			// Load command
			int o = cmd.indexOf(' ');
			if (o > 0) {
				loadModule(cmd.substring(o + 1).trim());
			} else {
				System.out.println("invalid load <module> command");
			}
		} else if (cmd.startsWith("?")) {
			// Help command
			System.out.println("Builtins: i[dentify], v[erbosity], load <sysex module>");
			Iterator<String> it = mods.keySet().iterator();
			while (it.hasNext()) {
				String mod = it.next();
				String[] cmds = mods.get(mod).getCommands();
				for (int i = 0; i < cmds.length; i++) {
					System.out.println(mod + ": " + cmds[i]);
				}
			}
		} else {
			// Try modules
			Receiver rcv = new Receiver() {
				public void close() {
				}

				public void send(MidiMessage msg, long ts) {
					dumpMsg("TX: ", msg);
					rOut.send(msg, ts);
				}			
			};
			Iterator<String> it = mods.keySet().iterator();
			while (it.hasNext()) {
				String mod = it.next();
				String res;
				if ((res = mods.get(mod).command(cmd, rcv)) != null) {
					System.out.println(mod + ": " + res);
				}
			}
		}
	}

	private void sendID() {
		SysexMessage sx = new SysexMessage();
		byte[] id = { 0x7e, 0x7f, 0x06, 0x01, (byte) 0xf7 };
		try {
			sx.setMessage(SysexMessage.SYSTEM_EXCLUSIVE, id, id.length);
			dumpMsg("TX: ", sx);
			rOut.send(sx, -1);
		} catch (InvalidMidiDataException e) {
		}
	}

	public SysexModule loadModule(String id) {
		SysexModule mod = mods.get(id);
		if (mod == null) {
			try {
				mod = (SysexModule) Class.forName("com.ashbysoft." + id).newInstance();
				mods.put(id, mod);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
		return mod;
	}

	/* Receiver interface */

	public void close() {
	}

	public void send(MidiMessage msg, long ts) {
		dumpMsg("RX: ", msg);
		String txt = dec.decodeMsg(msg, verb);
		if (txt!=null)
			System.out.println(txt);
	}

	private void dumpMsg(String pfx, MidiMessage msg) {
		if (verb) {
			StringBuffer sb = new StringBuffer(pfx);
			for (int i = 0; i < msg.getLength(); i++) {
				int v = ((int) msg.getMessage()[i]) & 0xff;
				if (v < 16)
					sb.append('0');
				sb.append(Integer.toHexString(v));
				sb.append(' ');
			}
			System.out.println(sb.toString());
		}
	}
}
