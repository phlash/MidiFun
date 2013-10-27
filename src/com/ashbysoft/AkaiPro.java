package com.ashbysoft;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;
import javax.sound.midi.SysexMessage;

public class AkaiPro implements SysexModule {

	private int devId = 0;
	private int dpsSize = 12;

	byte[] empty = new byte[0];
	
	String[] chanMap = {
		null, // Various master channels based on 'kind' value, see below
		"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",  // TODO: DPS16+
		"T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8"
	};
	String[] masterMap = {
		"MASTER", "A Master", "B Master", "C Master", "D Master"
	};
	String[] inputMap = {
		"INP 1","INP 2","INP 3","INP 4","INP 5","INP 6",                // TODO: DPS16+
		"DIGITAL-L", "DIGITAL-R", "MASTER-L", "MASTER-R",
		"AUX A", "AUX B", "bus-l", "bus-r"
	};
	String [] outputNames = {
		"MASTER-L", "MASTER-R", "MONITOR-L", "MONITOR-R",
		"SEND A", "SEND B", "SEND C", "SEND D"
	};
	String[] outputMap = {
		"MASTER-L", "MASTER-R", "MONITOR-L", "MONITOR-R",
		"BUS-L", "BUS-R", "SEND A", "SEND B", "SEND C", "SEND D",
		"TRK1", "TRK2", "TRK3", "TRK4", "TRK5", "TRK6", "TRK7", "TRK8",
		"TRK9", "TRK10", "TRK11", "TRK12", "TRK13", "TRK14", "TRK15", "TRK16"
	};
	
	public AkaiPro() {
		System.err.println("AkaiPro/DPS MIDI SysEx module");
		System.err.println("-----------------------------");
	}

	public String[] getCommands() {
		return new String[] { "dev[ice] <id>",
			"[un]lock k[eys]|f[aders]",
			"tr[ansport] s[top]|p[lay]|REC|r[ew]|f[wd]",
			"mix[er] l[evels]|a|b|c|d|i[nputs]|o[utputs]|ef[fects]|eq[l|m|h] [<begin channel> <end channel>]",
			"oth[er]"
		};
	}

	public String command(String cmd, Receiver rcv) {
		// TODO: implement commands...
		String res = null;
		if (cmd.startsWith("dev")) {
			// Select device ID
			String id = arg(cmd);
			if (id != null) {
				devId = Integer.parseInt(id);
				res = "Device: " + devId;
			}
		} else if (cmd.startsWith("lock") || cmd.startsWith("un")) {
			// [Un]Lock something...
			int u = cmd.startsWith("un") ? 1 : 0;
			String p = u > 0 ? "Un-" : "";
			String t = arg(cmd);
			if (t != null && t.startsWith("k")) {
				rcv.send(sysEx(devId, 0x07 + u, empty), -1);
				res = p + "Locked keys";
			} else if (t != null && t.startsWith("f")) {
				rcv.send(sysEx(devId, 0x09 + u, empty), -1);
				res = p + "Locked faders";
			}
		} else if (cmd.startsWith("tr")) {
			// Transport controls
			String s = arg(cmd);
			if (s != null) {
				byte[] b = new byte[1];
				if (s.startsWith("s")) {
					b[0] = 0x00;                     // Stop
				} else if (s.startsWith("p")) {
					b[0] = 0x09;                     // Play
				} else if (s.equals("REC")) {
					b[0] = 0x08;                     // Record
				} else if (s.startsWith("r")) {
					b[0] = 0x03;                     // Rewind
				} else if (s.startsWith("f")) {
					b[0] = 0x04;                     // Fast Forward
				} else {
					s = null;
				}
				if (s != null) {
					rcv.send(sysEx(devId, 0x10, b), -1);
				}
			} else {
				res = "Invalid transport command";
			}
		} else if (cmd.startsWith("mix")) {
			res = mixCommand(cmd, rcv);
		} else if (cmd.startsWith("oth")) {
			res = "DPS16 other data requests not implemented - sorry!";
		}
		return res;
	}

	private String mixCommand(String cmd, Receiver rcv) {
		String res = null;
		// Parse optional channel numbers
		String s = arg(cmd);
		String b = s != null ? arg(s) : null;
		String e = b != null ? arg(b) : null;
		int cb = 1;
		int ce = dpsSize;
		if (b != null && e != null) {
			try {
				cb = Integer.parseInt(b.substring(0, b.indexOf(' ')));
				ce = Integer.parseInt(e);
			} catch (NumberFormatException nfe) {
				nfe.printStackTrace();
			}
		}
		String inf = " (" + cb + "->" + ce + ") requested";
		if (s != null && s.startsWith("l")) {
			// Mixer level request, get level & pan for selected channels
			rcv.send(sysEx(devId, 0x7A, mix(0x01, 0, cb, ce-cb+1)), -1);
			res = "Mixer levels" + inf;
		} else if (s != null &&
			(s.startsWith("a") ||
			 s.startsWith("b") ||
			 s.startsWith("c") ||
			 s.startsWith("d"))) {
			// Mixer aux send request
			int kind = s.charAt(0)-'a'+1;
			rcv.send(sysEx(devId, 0x7A, mix(0x01, kind, cb, ce-cb+1)), -1);
			res = "Mixer aux send ("+(char)(kind-1+'A')+")" + inf;
		} else if (s != null && s.startsWith("i")) {
			// Mixer input assign request
			rcv.send(sysEx(devId, 0x7A, mix(0x02, 0, cb, ce-cb+1)), -1);
			res = "Mixer input assign" + inf;
		} else if (s != null && s.startsWith("o")) {
			// Mixer output assign request, only for 16+
			if (dpsSize < 16) {
				res = "Mixer output assign only possible on DPS16 and above";
			} else {
				rcv.send(sysEx(devId, 0x7A, mix(0x03, 0, 0, outputNames.length)), -1);
				res = "Mixer output assign requested";
			}
		} else if (s != null && s.startsWith("ef")) {
			// Mixer effects insert request, only for 16+
			if (dpsSize < 16) {
				res = "Mixer effect inserts only possible on DPS16 and above";
			} else {
				rcv.send(sysEx(devId, 0x7A, mix(0x04, 0, cb, ce-cb+1)), -1);
				res = "Mixer effect inserts" + inf;
			}
		} else if (s != null && s.startsWith("eq")) {
			char c = s.length() > 2 ? s.charAt(2) : ' ';
			int band = (c == 'h') ? 2 : (c == 'm') ? 1 : 0;
			rcv.send(sysEx(devId, 0x7A, mix(0x11, band, cb, ce-cb+1)), -1);
			res = "Mixer eq (" +c+") requested";
		}
		return res;
		// TODO:
		// 0x11 - EQ
		// 0x21 - Channel/Bus switch
		// 0x22 - EQ switch
		// 0x23 - AUX pre/post switch
		// 0x24 - SOLO/MUTE switch (DPS16 only?)
		// 0x31 - Thru mix switching
		// 0x61 - Effect type
		// 0x62 - Effect param
		// 0x63 - Effect on/off
		// 0x71 - EQ setting
		// 0x72 - AUX setting
		// 0x73 - Extra bus setting
		// 0x74 - Control via MIDI
		// 0x75 - Master out setting
		// 0x76 - V.Track name
	}
	
	private String arg(String cmdtail) {
		int o = cmdtail.indexOf(' ');
		if (o > 0) {
			String arg = cmdtail.substring(o + 1).trim();
			return arg;
		}
		return null;
	}

	private byte[] mix(int mc, int kind, int beg, int nch) {
		int size = 2 + (kind < 0 ? 0 : 1) + (nch < 0 ? 0 : nch);
		byte[] r = new byte[size];
		r[0] = (byte) (size - 1);
		r[1] = (byte) mc;
		int o = 2;
		if (kind >= 0) {
			r[o++] = (byte) kind;
		}
		if (nch > 0) {
			for (int ch = 0; ch < nch; ch++) {
				r[o + ch] = (byte) (beg + ch);
			}
		}
		return r;
	}

	private SysexMessage sysEx(int id, int fn, byte[] data) {
		SysexMessage sx = new SysexMessage();
		byte[] b = new byte[5 + data.length];
		b[0] = 0x47;
		b[1] = (byte) id;
		b[2] = (byte) fn;
		b[3] = 0x5A;
		System.arraycopy(data, 0, b, 4, data.length);
		b[4 + data.length] = (byte) 0xf7;
		try {
			sx.setMessage(SysexMessage.SYSTEM_EXCLUSIVE, b, b.length);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
		return sx;
	}

	public void idInfo(int mf, int family, int product, int version) {
		// Check for DPS12/16/24
		if (product == 0x0c00)
			dpsSize = 12;
		else if (product == 0x1000)
			dpsSize = 16;
		else if (product == 0x1800)
			dpsSize = 24;
		System.err.println("Identified DPS" + dpsSize);
	}
	
	public void decodeSysex(byte[] b, int o, StringBuffer sb) {
		// decode AkaiPro SysEx messages
		// skip dev id
		o++;
		// read function code
		byte fn = b[o++];
		if (0x07 == fn) {
			sb.append(": lock keys");
		} else if (0x08 == fn) {
			sb.append(": unlock keys");
		} else if (0x09 == fn) {
			sb.append(": lock faders");
		} else if (0x0a == fn) {
			sb.append(": unlock faders");
		} else if (0x0e == fn) {
			sb.append(": MTC mode");
		} else if (0x0f == fn) {
			sb.append(": MIDI Clock mode");
		} else if (0x10 == fn) {
			sb.append(": Transport: TODO");
		} else if (0x51 == fn) {
			sb.append(": Temop map: TODO");
		} else if (0x52 == fn) {
			sb.append(": Tempo map request?");
		} else if (0x7a == fn) {
			sb.append(": Mixer request?");
		} else if (0x7b == fn) {
			sb.append(": Mixer");
			decodeMix(b, o, sb);
		} else if (0x7c == fn) {
			sb.append(": Other request?");
		} else if (0x7d == fn) {
			sb.append(": Other: TODO");
		}
	}
	
	private void decodeMix(byte[] b, int o, StringBuffer sb) {
		// decode mixer settings
		// skip product number
		o++;
		// read byte count and mix command
		byte n = b[o++];
		byte c = b[o++];
		if (0x00 == c) {
			sb.append(": NOP");
		} else if (0x01 == c) {
			sb.append(": levels: ");
			// read level kind
			byte k = b[o++];
			// calculate number of channels from byte count
			int nch = ((int)n - 2)/5;
			for (int ch = 0; ch < nch && o < b.length; ch++) {
				byte cv = b[o++];
				String nm = (cv != 0) ? chanMap[cv % chanMap.length] : masterMap[k % masterMap.length];
				sb.append(nm).append('=').append(b[o++]).append('/');
				o++;
				sb.append(b[o++]).append(' ');
				o++;
			}
		} else if (0x02 == c) {
		sb.append(": input map: ");
			// skip reserved byte
			o++;
			// calculate number of channels from byte count
			int nch = ((int)n - 2)/2;
			for (int ch = 0; ch < nch && o < b.length; ch++) {
				sb.append(chanMap[b[o++] % chanMap.length]).append("<-");
				sb.append(inputMap[b[o++] % inputMap.length]).append(' ');
			}
		} else if (0x03 == c) {
			sb.append(": output map: ");
			// skip reserved byte
			o++;
			// calculate number of channels from byte count
			int nch = ((int)n - 2)/2;
			for (int ch = 0; ch < nch && o < b.length; ch++) {
				sb.append(outputNames[b[o++] % outputNames.length]).append("->");
				sb.append(outputMap[b[o++] % outputMap.length]).append(' ');
			}
			
		} else if (0x04 == c) {
			sb.append(": effect insert: TODO");
		} else if (0x11 == c) {
			byte bnd = b[o++];
			String band = (bnd == 2) ? "High" : (bnd == 1) ? "Mid" : "Low";
			sb.append(": EQ (").append(band).append("): ");
			int nch = ((int)n - 2)/7;
			for (int ch = 0; ch < nch && o < b.length; ch++) {
				sb.append(chanMap[b[o++] % chanMap.length]).append('=');
				sb.append(b[o++]).append('/');
				o++;
				sb.append(b[o++]).append('/');
				o++;
				sb.append(b[o++]).append("/ ");
				o++;
			}
		} else {
			sb.append(": unimplemented command: TODO!");
		}
	}
}
