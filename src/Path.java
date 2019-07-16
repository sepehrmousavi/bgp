package ir.sharif.ce.partov.user;

import ir.sharif.ce.partov.utils.Utility;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Sepehr on 5/25/2017.
 */
public class Path {
    public ArrayList<Integer> ASNumbers;
    int prefixIP, prefixMask;
    public int interfaceIndex;
    public ArrayList<Integer> advertisedInterfaces = new ArrayList<Integer>();

    public Path() {
        ASNumbers = new ArrayList<Integer>();
        interfaceIndex = 0;
    }

    public Path(int AS, int prefixIP, int prefixMask, int interfaceIndex) {
        this.interfaceIndex = interfaceIndex;
        this.prefixMask = prefixMask;
        this.prefixIP = prefixIP;
        ASNumbers = new ArrayList<Integer>();
        ASNumbers.add(AS);
    }

    public Path(byte[] AS, int ASNumber, int interfaceIndex) {
        this.interfaceIndex = interfaceIndex;
        ASNumbers = new ArrayList<Integer>();
        int len = AS.length / 2;
        ASNumbers.add(ASNumber);
        for (int i = 0 ; i < len ; i++)
            ASNumbers.add((int)Utility.convertBytesToShort(Arrays.copyOfRange(AS, 2*i, 2*i+2)));
    }

    public void addPrefixToPath(byte[] prefix) {
        prefixIP = Utility.convertBytesToInt(Arrays.copyOfRange(prefix, 0, 4));
        prefixMask = prefix[4];
    }

    public boolean isValid() {
        for (int i = 0 ; i < ASNumbers.size() ; i++) {
            int temp = ASNumbers.get(i);
            for (int j = i+1 ; j < ASNumbers.size() ; j++)
                if (temp == ASNumbers.get(j))
                    return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Path))
            return false;
        if (obj == this)
            return true;
        Path tmp = (Path) obj;
        if (tmp.interfaceIndex != this.interfaceIndex)
            return false;
        if (tmp.prefixIP != this.prefixIP)
            return false;
        if (tmp.prefixMask != this.prefixMask)
            return false;
        if (tmp.ASNumbers.size() != this.ASNumbers.size())
            return false;
        for (int i = 0 ; i < tmp.ASNumbers.size() ; i++)
            if (tmp.ASNumbers.get(i) != this.ASNumbers.get(i))
                return false;
        return true;
    }
}
