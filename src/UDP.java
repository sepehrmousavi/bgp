package ir.sharif.ce.partov.user;

import ir.sharif.ce.partov.utils.Utility;

public class UDP {
	public final static int Defualt_len=8;
	public final static short UDP_PROTOCOL=0x11;
	private byte[] data;
	
	public UDP(int size) {
		data = new byte[size];
		setDefaults();
	}
	
	public UDP() {
		data = new byte[Defualt_len];
		setDefaults();
	}
	public UDP(byte[] packet, int pos, int size) {
		if(size<Defualt_len){
			data = new byte[Defualt_len];
			setDefaults();
			System.err.println("UN accepatable size for UDP \nDefault Header Instanced");
		}
		else{
			data = new byte[size];
			setLen(size);
			System.arraycopy(packet, pos, data, 0, size);
		}
	}
	private void setDefaults(){
		setLen(data.length);
		setSrcPort(0);
		setDestPort(0);
		setChecksum(0);
	}
	
	
	public void setSrcPort(int Source_Port){
		System.arraycopy(Utility.getBytes((short)Source_Port), 0, data, 0, 2);
	}
	public int getSrcPort(){
		byte[] port= new byte[2];
		System.arraycopy(data, 0, port, 0, 2);
		return Utility.convertBytesToShort(port);
	}
	public void setDestPort(int Destination_Port){
		System.arraycopy(Utility.getBytes((short)Destination_Port), 0, data, 2, 2);
	}
	public int getDestPort(){
		byte[] port= new byte[2];
		System.arraycopy(data, 2, port, 0, 2);
		return Utility.convertBytesToShort(port);
	}
	public void setLen(int Length){
		System.arraycopy(Utility.getBytes((short)Length), 0, data, 4, 2);
	}
	public int getLen(){
		byte[] length= new byte[2];
		System.arraycopy(data, 4, length, 0, 2);
		return Utility.convertBytesToShort(length);
	}
	public void setChecksum(int Packet_Checksum){
		System.arraycopy(Utility.getBytes((short)Packet_Checksum), 0, data, 6, 2);
	}
	public int getChecksum(){
		byte[] Packet_Checksum= new byte[2];
		System.arraycopy(data, 6, Packet_Checksum, 0, 2);
		return Utility.convertBytesToShort(Packet_Checksum);
	}
	
	public void setPayload(byte[] payload, int pos){
		System.arraycopy(payload, 0, data, Defualt_len+pos, payload.length);
	}
	public byte[] getPayload(int pos, int len){
		byte[] payload=new byte[len];
		System.arraycopy(data, Defualt_len+pos, payload, 0, len);
		return payload;
	}
	
	public int getSize(){
		return data.length;
	}
	public byte[] getData(){
		return data;
	}
	
}
