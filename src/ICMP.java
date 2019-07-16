package ir.sharif.ce.partov.user;

import ir.sharif.ce.partov.utils.Utility;

public class ICMP {
	public final static short ICMP_PROTOCOL=0x01;
	public final static short PING_REQ=0x08;
	public final static short PING_RESP=0x00;
	public final static int DEFAULT_SIZE=8;
	private byte[] data;
	public ICMP(){
		data = new byte[DEFAULT_SIZE];
		setDefaults();
	}
	public ICMP(int size){
		data = new byte[size];
		setDefaults();
	}
	public ICMP(byte[] packet, int pos, int size) {
		if(size<DEFAULT_SIZE){
			data = new byte[DEFAULT_SIZE];
			setDefaults();
			System.err.println("UN accepatable ICMP size \nDefault Header Instanced");
		}
		else{
			data = new byte[size];
			System.arraycopy(packet, pos, data, 0, size);
		}
	}
	private void setDefaults(){
		setType(0);
		setCode(0);
		setChecksum((short)0);
		setRestData(0);
	}
	public void setType(int type){
		data[0]=(byte) (type&0xFF);
	}
	public int getType(){
		return (int)data[0];
	}
	public void setCode(int code){
		data[1]=(byte) (code&0xFF);
	}
	public int getCode(){
		return (int)data[1];
	}
	public void setChecksum(short HeaderChecksum){
		System.arraycopy(Utility.getBytes(HeaderChecksum), 0, data, 2, 2);
	}
	public short getChecksum(){
		byte[] checksum = new byte[2];
		System.arraycopy(data, 2, checksum, 0, 2);
		return Utility.convertBytesToShort(checksum);
	}
	public void setRestData(int HeaderData){
		System.arraycopy(Utility.getBytes(HeaderData), 0, data, 4, 4);
	}
	public int getRestData(){
		byte[]  HeaderData= new byte[2];
		System.arraycopy(data, 4, HeaderData, 0, 4);
		return Utility.convertBytesToShort(HeaderData);
	}
	public void setPingID(int Identifier){
		System.arraycopy(Utility.getBytes((short)Identifier), 0, data, 4, 2);
	}
	public int getPingID(){
		byte[] Identifier = new byte[4];
		System.arraycopy(data, 4, Identifier, 0, 2);
		return Utility.convertBytesToShort(Identifier);
	}
	public void setPingSeqNum(int Sequence_Number){
		System.arraycopy(Utility.getBytes((short)Sequence_Number), 0, data, 6, 2);
	}
	public int getPingSeqNum(){
		byte[] Sequence_Number = new byte[4];
		System.arraycopy(data, 6, Sequence_Number, 0, 2);
		return Utility.convertBytesToShort(Sequence_Number);
	}
	public void setPayoad(byte[] payload){
		System.arraycopy(payload, 0, data, 8, data.length-8);
	}
	public byte[] getPayoad(){
		byte[] payload = new byte[data.length-8];
		System.arraycopy(data, 8, payload, 0, data.length-8);
		return payload;
	}
	public byte[] getData(){
		return data;
	}
	public int getLen(){
		return data.length;
	}
}
