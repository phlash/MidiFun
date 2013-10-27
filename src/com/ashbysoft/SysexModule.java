package com.ashbysoft;

import javax.sound.midi.Receiver;

public interface SysexModule {
	public void idInfo(int id, int family, int product, int version);
	public void decodeSysex(byte[] b, int o, StringBuffer sb);
	public String[] getCommands();
	public String command(String cmd, Receiver rcv);
}
