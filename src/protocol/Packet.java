package protocol;

public class Packet {
	
	private int header;
	private Integer[] data;
	
	public Packet(int header, Integer[] data) {
		this.header = header;
		this.data = data;
	}
	
	public Integer[] getPacket() {
		Integer[] pkt = new Integer[data.length+1];
		pkt[0] = header;
		System.arraycopy(data, 0, pkt, 1, data.length);
		return pkt;
	}

}
