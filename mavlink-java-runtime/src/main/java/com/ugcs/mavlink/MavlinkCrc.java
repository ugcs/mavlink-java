package com.ugcs.mavlink;

// CRC-16-CCITT
 
public class MavlinkCrc {

	private int checksum = 0xffff;
	
	public void append(int value) {
		int tmp = (value & 0xff) ^ (checksum & 0xff);
		tmp = (tmp ^ tmp << 4) & 0xff;
		checksum = (checksum >>> 8 & 0xffff) ^ (tmp << 8) ^ (tmp << 3) ^ (tmp >>> 4);
	}
	
	public void append(byte[] b) {
		append(b, 0, b.length);
	}
	
	public void append(byte[] b, int off, int len) {
		if (b == null)
			throw new IllegalArgumentException("b");
		if (off < 0 || len < 0 || off + len > b.length)
			throw new IndexOutOfBoundsException();
		
		int last = off + len;
		for (int i = off; i < last; ++i)
			append(b[i] & 0xff);
	}
	
	public int getChecksum() {
		return checksum;
	}	
}
