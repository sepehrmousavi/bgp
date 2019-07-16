package ir.sharif.ce.partov.user;

/**
 * Created by Sepehr on 5/24/2017.
 */

import ir.sharif.ce.partov.utils.Utility;

import java.util.Arrays;

public class BGP {

    private byte[] data;
    public final static int WORD_SIZE = 4;


    public BGP(int AS, int Identifier) {            // OPEN

        data = new byte[29];

        // Set marker
        for (int i = 0; i < 16; i++)
            data[i] = (byte) 0xFF;

        // Set length
        data[16] = 0;
        data[17] = 29;

        // Set type
        data[18] = 1;

        // Set version
        data[19] = 4;

        // Set AS Number
        System.arraycopy(Utility.getBytes((short) AS), 0, data, 20, 2);

        // Set Hold Time
        data[22] = 0;
        data[23] = 0;

        // Set BGP Identifier
        System.arraycopy(Utility.getBytes(Identifier), 0, data, 24, 4);

        // Set Opt Parm Length
        data[28] = 0;
    }

    public BGP(int ErrorCode) {         // NOTIFICATION

        data = new byte[21];

        // Set marker
        for (int i = 0; i < 16; i++)
            data[i] = (byte) 0xFF;

        // Set length
        data[16] = 0;
        data[17] = 20;

        // Set type
        data[18] = 3;

        // Set Error Code
        data[19] = (byte) ErrorCode;

        // Set Error subcode
        data[20] = 0;
    }

    public BGP() {                      // KEEP ALIVE

        data = new byte[19];

        // Set marker
        for (int i = 0; i < 16; i++)
            data[i] = (byte) 0xFF;

        // Set length
        data[16] = 0;
        data[17] = 19;

        // Set type
        data[18] = 4;
    }

    public BGP(int withdrawnRoutesLength, int prefixIP, int prefixMask) {                  // UPDATE - WITHDRAW
        data = new byte[28];

        // Set marker
        for (int i = 0; i < 16; i++)
            data[i] = (byte) 0xFF;

        // Set length
        data[16] = 0;
        data[17] = 28;

        // Set type
        data[18] = 2;

        // Set Withdrawn Routes Length
        data[19] = 0;
        data[20] = 1;

        // Set IP
        System.arraycopy(Utility.getBytes(prefixIP), 0, data, 21, 4);

        // Set Mask
        data[25] = (byte)prefixMask;

        // Total Path Attribute Length
        data[26] = 0;
        data[27] = 0;
    }

    public BGP(int totalPathAttributeLength, byte[] pathAttributes, byte[] networkLayer) {          // UPDATE - ADVERTISE

        data = new byte[23 + pathAttributes.length + networkLayer.length];

        // Set marker
        for (int i = 0; i < 16; i++)
            data[i] = (byte) 0xFF;

        // Set length
        System.arraycopy(Utility.getBytes((short) data.length), 0, data, 16, 2);

        // Set type
        data[18] = 2;

        // Set Withdrawn Routes Length
        data[19] = 0;
        data[20] = 0;

        // Set Total Path Attribute Length
        System.arraycopy(Utility.getBytes((short) totalPathAttributeLength), 0, data, 21, 2);

        // Set Path Attributes
        System.arraycopy(pathAttributes, 0, data, 23, pathAttributes.length);

        // Set Network Layer
        System.arraycopy(networkLayer, 0, data, 23 + pathAttributes.length, networkLayer.length);
    }

    public BGP(byte[] data) {                       // BGP Copy
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    public byte[] getData() {
        return data;
    }

    public int getType() {
        return data[18];
    }

    public int getLength() {
        return (int)Utility.convertBytesToShort(Arrays.copyOfRange(data, 16, 18));
    }

}
