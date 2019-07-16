package ir.sharif.ce.partov.user;

/**
 * Created by Sepehr on 4/20/2017.
 */

import ir.sharif.ce.partov.utils.Utility;

public class TCP {
    public final static int Default_Offset = 5;
    public final static int WORD_SIZE = 4;
    public final static short TCP_PROTOCOL = 0x6;
    private byte[] data;

    public TCP(int size) {
        data = new byte[size * WORD_SIZE];
        setDefaults();
    }

    public TCP() {
        data = new byte[Default_Offset * WORD_SIZE];
        setDefaults();
    }

    public TCP(int srcPort, int destPort, int seqNumber, int ackNumber, int windowsize) {
        data = new byte[Default_Offset * WORD_SIZE];
        setDefaults();
        setSrcPort(srcPort);
        setDestPort(destPort);
        setSeqNumber(seqNumber);
        setAckNumber(ackNumber);
        setWindowSize(windowsize);
    }

    public TCP(byte[] packet, int pos, int size) {
        if(size<Default_Offset){
            data = new byte[Default_Offset * WORD_SIZE];
            setDefaults();
            System.err.println("UN accepatable size for TCP \nDefault Header Instanced");
        }
        else{
            data = new byte[size * WORD_SIZE];
            System.arraycopy(packet, pos, data, 0, size * WORD_SIZE);
            setOffset();
        }
    }

    private void setDefaults() {
        setSrcPort(0);
        setDestPort(0);
        setSeqNumber(0);
        setAckNumber(0);
        setOffset();
        setReservedNS();
        setFlags();
        setWindowSize(0);
        setChecksum(0);
        setUrg(0);
    }

    public void setSrcPort(int Source_Port) {
        System.arraycopy(Utility.getBytes((short)Source_Port), 0, data, 0, 2);
    }

    public int getSrcPort() {
        byte[] port = new byte[2];
        System.arraycopy(data, 0, port, 0, 2);
        return Utility.convertBytesToShort(port);
    }

    public void setDestPort(int Destination_Port) {
        System.arraycopy(Utility.getBytes((short)Destination_Port), 0, data, 2, 2);
    }

    public int getDestPort() {
        byte[] port = new byte[2];
        System.arraycopy(data, 2, port, 0, 2);
        return Utility.convertBytesToShort(port);
    }

    public void setSeqNumber(int Sequence_Number) {
        System.arraycopy(Utility.getBytes(Sequence_Number), 0, data, 4, 4);
    }

    public int getSeqNumber() {
        byte[] seqNumber = new byte[4];
        System.arraycopy(data, 4, seqNumber, 0, 4);
        return Utility.convertBytesToInt(seqNumber);
    }

    public void setAckNumber(int Ack_Number) {
        System.arraycopy(Utility.getBytes(Ack_Number), 0, data, 8, 4);
    }

    public int getAckNumber() {
        byte[] AckNumber = new byte[4];
        System.arraycopy(data, 8, AckNumber, 0, 4);
        return Utility.convertBytesToInt(AckNumber);
    }

    public void setOffset() {
        byte Offset = (byte)(data.length / WORD_SIZE);
        Offset = (byte)(Offset << 4);
        data[12] = (byte)(data[12] & 0x0F);
        data[12] = (byte)(data[12] | Offset);
    }

    public int getOffset() {
        return data.length / WORD_SIZE;
    }

    public void setReservedNS() {
        data[12] = (byte)(data[12] & 0xF0);
    }

    public void setFlags() {
        data[13] = 0;
    }

    public void setACK(boolean val) {
        data[13] = (byte)((data[13] & 0xEF) | (val == true ? 0x10 : 0x00));
    }

    public boolean getAck() {
        return (data[13] & 0x10) > 0;
    }

    public void setSYN(boolean val) {
        data[13] = (byte)((data[13] & 0xFD) | (val == true ? 0x02 : 0x00));
    }

    public boolean getSYN() {
        return (data[13] & 0x02) > 0;
    }

    public void setFIN(boolean val) {
        data[13] = (byte)((data[13] & 0xFE) | (val == true ? 0x01 : 0x00));
    }

    public boolean getFIN() {
        return (data[13] & 0x01) > 0;
    }


    public void setWindowSize(int windowSize) {
        System.arraycopy(Utility.getBytes((short)windowSize), 0, data, 14, 2);
    }

    public int getWindowSize() {
        byte[] windowSize = new byte[2];
        System.arraycopy(data, 14, windowSize, 0, 2);
        return Utility.convertBytesToShort(windowSize);
    }

    public void setChecksum(int checksum) {
        System.arraycopy(Utility.getBytes((short)checksum), 0, data, 16, 2);
    }

    public int getChecksum() {
        byte[] checksum = new byte[2];
        System.arraycopy(data, 16, checksum, 0, 2);
        return Utility.convertBytesToShort(checksum);
    }

    public void setUrg(int urgent) {
        System.arraycopy(Utility.getBytes((short)urgent), 0, data, 18, 2);
    }

    public void setPayload(byte[] payload){
        byte[] data1 = new byte[24];
        System.arraycopy(data, 0, data1, 0, 20);
        System.arraycopy(payload, 0, data1, Default_Offset * WORD_SIZE, payload.length);
        data = new byte[24];
        System.arraycopy(data1, 0, data, 0, 24);
        setOffset();
    }
    public byte[] getPayload(int len){
        byte[] payload=new byte[len];
        System.arraycopy(data, Default_Offset * WORD_SIZE, payload, 0, len);
        return payload;
    }

    public byte[] getData(){
        return data;
    }

}