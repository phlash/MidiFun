package com.ashbysoft;

import java.util.HashMap;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

public class MidiDecoder {
	private SysexLoader mfun;
	
	// Voice channel messages
	private String[] vNames = { "NOff", "NtOn", "ATch", "Ctrl", "Prog", "CPres",
			"Ptch" };

	// Common messages
	private String[] cNames = { "Sysex", "MTC/QF", "SPP", "SSel", "?F4", "?F5",
			"TuneReq", "Eox", "Clock", "Tick", "Start", "Cont", "Stop", "?FD",
			"Sense", "Reset" };

	// Sysex manufacturer codes
	private HashMap<Integer, String> mCodes = new HashMap<Integer, String>();
	/*
	 * Sequential Circuits 1 Big Briar 2 Octave / Plateau 3 Moog 4 Passport
	 * Designs 5 Lexicon 6 Kurzweil 7 Fender 8 Gulbransen 9 Delta Labs 0x0A
	 * Sound Comp. 0x0B General Electro 0x0C Techmar 0x0D Matthews Research 0x0E
	 * Oberheim 0x10 PAIA 0x11 Simmons 0x12 DigiDesign 0x13 Fairlight 0x14
	 * Peavey 0x1B JL Cooper 0x15 Lowery 0x16 Lin 0x17 Emu 0x18 Bon Tempi 0x20
	 * S.I.E.L. 0x21 SyntheAxe 0x23 Hohner 0x24 Crumar 0x25 Solton 0x26
	 * Jellinghaus Ms 0x27 CTS 0x28 PPG 0x29 Elka 0x2F Cheetah 0x36 Waldorf 0x3E
	 * Kawai 0x40 Roland 0x41 Korg 0x42 Yamaha 0x43 Casio 0x44 Akai 0x45
	 * Kamia Studio 0x46 AkaiPro 0x47
	 */
	
	public MidiDecoder(SysexLoader mfun) {
		this.mfun = mfun;
		// TODO: lots of other manufacturers?
		mCodes.put(new Integer(0x43), "Yamaha");
		mCodes.put(new Integer(0x44), "Casio");
		mCodes.put(new Integer(0x45), "Akai");
		mCodes.put(new Integer(0x47), "AkaiPro");
	}

	public String hex(int v) {
		String s = Integer.toHexString(v);
		return (v < 16) ? "0"+s : s;
	}
	
	public String decodeMsg(MidiMessage msg, boolean verb) {
		// See what we have.. channel/voice message or common/realtime?
		int st, ch = -1;
		String cmd;
		if (msg.getStatus() >= 0x80 && msg.getStatus() < 0xf0) {
			// channel message - mask out channel ID
			st = (msg.getStatus() & 0xf0);
			ch = msg.getStatus() & 0xf;
			cmd = vNames[st / 16 - 8];
		} else {
			st = msg.getStatus();
			cmd = cNames[st & 0xf];
		}
		StringBuffer sb = new StringBuffer("  ");
		sb.append(cmd);
		if (ch != -1)
			sb.append('[').append(ch).append(']');

		// Extra decoding for certain messages...
		switch (st) {
		case SysexMessage.SYSTEM_EXCLUSIVE:
			decodeSysex(msg.getMessage(), sb);
			break;
		case ShortMessage.NOTE_ON:
		case ShortMessage.NOTE_OFF:
			sb.append(": ").append(msg.getMessage()[1]).append(" V:")
					.append(msg.getMessage()[2]);
			break;
		case ShortMessage.CONTROL_CHANGE:
			sb.append(": ID:").append(msg.getMessage()[1]).append(" V:")
					.append(msg.getMessage()[2]);
			break;
		case ShortMessage.SONG_POSITION_POINTER:
			int pos = (((int) msg.getMessage()[2]) & 0xff) << 7;
			pos |= ((int) msg.getMessage()[1] & 0x7f);
			sb.append(": Pos:").append(pos);
			break;
		case ShortMessage.SONG_SELECT:
			sb.append(" Song:").append(msg.getMessage()[1]);
			break;
		case ShortMessage.ACTIVE_SENSING:
			// Skip unless we are being verbose
			if (!verb)
				return null;
		}
		return sb.toString();
	}

	private void decodeSysex(byte[] b, StringBuffer sb) {
		// check manufacturer ID
		int off = 1;
		int mid = ((int) b[off++]) & 0xff;
		if (0 == mid) {
			// extended ID for late-comers :)
			mid = (((int) b[off++]) & 0xff) + 1;
			mid <<= 8;
			mid |= ((int) b[off++] & 0xff);
		}
		if (0x7d == mid) {
			// educational
			sb.append(": EDU");
		} else if (0x7e == mid) {
			// system non-realtime
			sb.append(": SysNR");
			decodeSysNR(b, off, sb);
		} else if (0x7f == mid) {
			// system realtime
			sb.append(": SysRT");
			decodeSysRT(b, off, sb);
		} else {
			String mf = (String) mCodes.get(new Integer(mid));
			if (mf != null) {
				sb.append(": ").append(mf);
				SysexModule mod = mfun.loadModule(mf);
				if (mod != null) {
					mod.decodeSysex(b, off, sb);
				} else {
					sb.append(": unable to load decoder");
				}
			} else {
				sb.append(": ??");
			}
		}
	}
	
	private void decodeSysNR(byte[] b, int o, StringBuffer sb) {
		// ignore channel ID...
		o++;
		// grab sub-ID/ID2
		byte sid = b[o++];
		byte si2 = b[o++];
		// check what we have
		if (0x06 == sid && 0x02 == si2) {
			// Identity response
			int  mf = ((int)b[o++]) & 0xff;		// XXXX: Should this use extended mechanism??
			String id = mCodes.get(new Integer(mf));
			if (id == null) {
				id = "Unknown";
			}
			byte f1 = b[o++];
			byte f2 = b[o++];
			byte p1 = b[o++];
			byte p2 = b[o++];
			byte v1 = b[o++];
			byte v2 = b[o++];
			byte v3 = b[o++];
			byte v4 = b[o++];
			sb.append(": Identity: ").append(id).append('(').append(hex(mf)).append(')');
			sb.append(": family:").append(hex(f1)).append(hex(f2));
			sb.append(": product:").append(hex(p1)).append(hex(p2));
			sb.append(": version:").append(hex(v1)).append(hex(v2)).append(hex(v3)).append(hex(v4));
			SysexModule sx = mfun.loadModule(id);
			if (sx != null) {
				// Pass on ID response
				sx.idInfo(mf, (int)f1 << 8 | (int)f2, (int)p1 << 8 | (int)p2,
					(int)v1 << 24 | (int)v2 << 16 | (int)v3 << 8 | (int)v4);
			}
		}
	}
	
	private void decodeSysRT(byte[] b, int o, StringBuffer sb) {
		// TODO: MTC decoder
		sb.append(": TODO");
	}
}
