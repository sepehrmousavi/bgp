package ir.sharif.ce.partov.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;

import ir.sharif.ce.partov.base.ClientFramework;
import ir.sharif.ce.partov.base.Frame;
import ir.sharif.ce.partov.base.Machine;
import ir.sharif.ce.partov.utils.Utility;

import javax.rmi.CORBA.Util;

public class SimulateMachine extends Machine {

	public enum State {IDLE, CONNECT, ACTIVESTATE, OPENSTATE, OPENCONFIRM, ESTABLISHED}
	public enum Relationship {Customer, Provider, Peer}

	public final int ConnectRetryTime = 30;
	public final int HoldTime = 240;

	int numberOfInterfaces;
	boolean[] getStart;
	boolean[] timeChange;
	State[] states;
	Thread[] timer;
	Relationship[] neighbours;
	int ASNumber;
	int[] neighbourIP;
	int[] priority;
	ArrayList<Path> paths;

	public void findCustomInformation() {
		neighbours = new Relationship[numberOfInterfaces];
		neighbourIP = new int[numberOfInterfaces];
		priority = new int[numberOfInterfaces];
		paths = new ArrayList<Path>();

		String[] lines = getCustomInformation().split("\n");
		lines = Arrays.copyOfRange(lines, 1, lines.length);

		ASNumber = Integer.parseInt(lines[0].substring(2));

		for (int i = 1 ; i <= numberOfInterfaces ; i++) {
			String s = lines[i];
			int lastIndexOfColon = s.lastIndexOf(':');
			neighbourIP[i-1] = Utility.getIP(s.substring(lastIndexOfColon+2));
			if (s.contains("customer")) {
				neighbours[i - 1] = Relationship.Customer;
				priority[i - 1] = 100;
			}
			else if (s.contains("peer")) {
				neighbours[i - 1] = Relationship.Peer;
				priority[i - 1] = 90;
			}
			else if (s.contains("provider")) {
				neighbours[i - 1] = Relationship.Provider;
				priority[i - 1] = 80;
			}
		}
		for (int i = numberOfInterfaces+1 ; i < lines.length ; i++) {
			String s = lines[i];
			int lastIndexOfColon = s.lastIndexOf(':');
			s = s.substring(lastIndexOfColon+2);
			int indexOfSlash = s.indexOf('/');
			int prefixIP = Utility.getIP(s.substring(0, indexOfSlash));
			int prefixMask = Integer.parseInt(s.substring(indexOfSlash+1));
			Path path = new Path(ASNumber, prefixIP, prefixMask, -1);
			paths.add(path);
		}
	}

	public SimulateMachine(ClientFramework clientFramework, int count) {
		super(clientFramework, count);
	}

	public void initialize() {

		numberOfInterfaces = getCountOfInterfaces();

		findCustomInformation();

		timeChange = new boolean[numberOfInterfaces];
		getStart = new boolean[numberOfInterfaces];
		states = new State[numberOfInterfaces];
		timer = new Thread[numberOfInterfaces];

		for (int i = 0 ; i < numberOfInterfaces ; i++) {
			getStart[i] = false;
			timeChange[i] = false;
			states[i] = State.IDLE;
		}

		for (int i = 0 ; i < numberOfInterfaces ; i++) {
			final int j = i;
			timer[j] = new Thread(new Runnable() {
				public long beginTime = System.currentTimeMillis();
				@Override
				public void run() {
					while (true) {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						long curTime = System.currentTimeMillis();
						if (timeChange[j] == true) {
							timeChange[j] = false;
							beginTime = System.currentTimeMillis();
						}
						else if (states[j] == State.IDLE) {				// IDLE
							sendSynAckMessage(j, 2);
							changeState(j, State.IDLE, State.CONNECT);
						}
						else if (states[j] == State.CONNECT) { 			// CONNECT
							if (curTime - beginTime > ConnectRetryTime * 1000) {
								changeState(j, State.CONNECT, State.ACTIVESTATE);
							}
						}
						else if (states[j] == State.ACTIVESTATE) {		// ACTIVESTATE
							if (curTime - beginTime > ConnectRetryTime * 1000) {
								sendSynAckMessage(j, 2);
								changeState(j, State.ACTIVESTATE, State.CONNECT);
							}

						}
						else if (states[j] == State.OPENSTATE) {			// OPENSTATE
							if (curTime - beginTime > HoldTime * 1000) {
								sendNotificationMessage(j, 4);
								errorIdle();
								changeState(j, State.OPENSTATE, State.IDLE);
							}
						}
						else if (states[j] == State.OPENCONFIRM) {		// OPENCONFIRM
							if (curTime - beginTime > HoldTime * 1000) {
								sendNotificationMessage(j, 4);
								errorIdle();
								changeState(j, State.OPENCONFIRM, State.IDLE);
							}
						}
						else if (states[j] == State.ESTABLISHED) {		// ESTABLISHED
							if (curTime - beginTime > HoldTime * 1000) {
								sendNotificationMessage(j, 4);
								errorIdle();
								changeState(j, State.ESTABLISHED, State.IDLE);
							}
						}
					}
				}
			});
		}

	}

	public void errorIdle() {
		System.out.print("an error occurred. ");
	}

	public void changeState(int interfaceIndex, State prevState, State nextState) {
		states[interfaceIndex] = nextState;
		timeChange[interfaceIndex] = true;
		System.out.println("state changed from " + prevState +" to " + nextState + " on interface " + interfaceIndex);
	}

	public void processFrame(Frame frame, int ifaceIndex) {

		byte[] data = new byte[frame.data.length];
		System.arraycopy(frame.data, 0, data, 0, data.length);

		EthernetHeader ethernetHeader = new EthernetHeader(data, 0);
		IPv4Header iPv4Header = new IPv4Header(data, 14, 5);

		if (states[ifaceIndex] == State.IDLE)
			return;

		boolean isBGP = true;

		for (int i = 0 ; i < 16 ; i++)
			if (data[34+i] != (byte)0xFF)
				isBGP = false;

		if (isBGP) {			// BGP Packet
			BGP bgp = new BGP(Arrays.copyOfRange(data, 34, data.length));

			int BGPtype = bgp.getType();

			if (BGPtype <= 0 || BGPtype >= 5) {
				sendNotificationMessage(ifaceIndex, 1);
				errorIdle();
				changeState(ifaceIndex, states[ifaceIndex], State.IDLE);
				return;
			}

			if (BGPtype == 1) {                // OPEN
				if (states[ifaceIndex] != State.OPENSTATE) {
					sendNotificationMessage(ifaceIndex, 1);
					errorIdle();
					changeState(ifaceIndex, states[ifaceIndex], State.IDLE);
					return;
				}
				if (checkOpenMessage(bgp) == false) {
					sendNotificationMessage(ifaceIndex, 2);
					errorIdle();
					changeState(ifaceIndex, State.OPENSTATE, State.IDLE);
					return;
				}
				sendKeepaliveMessage(ifaceIndex);
				changeState(ifaceIndex, State.OPENSTATE, State.OPENCONFIRM);
			} else if (BGPtype == 2) {			// UPDATE
				if (states[ifaceIndex] != State.ESTABLISHED) {
					sendNotificationMessage(ifaceIndex, 1);
					errorIdle();
					changeState(ifaceIndex, states[ifaceIndex], State.IDLE);
					return;
				}
				if (checkUpdateMessage(bgp) == false) {
					sendNotificationMessage(ifaceIndex, 3);
					errorIdle();
					changeState(ifaceIndex, State.ESTABLISHED, State.IDLE);
					return;
				}
				timeChange[ifaceIndex] = true;

				byte[] bgpData = bgp.getData();
				if (bgpData[19] == 0 && bgpData[20] == 0) {
					addPaths(Arrays.copyOfRange(bgpData, 21, bgpData.length), ifaceIndex);
				}
				else
				{
					int prefixIP = Utility.convertBytesToInt(Arrays.copyOfRange(bgpData, 21, 25));
					int prefixMask = bgpData[25];
					processWithdraw(prefixIP, prefixMask, ifaceIndex);
				}

			} else if (BGPtype == 3) { 			// NOTIFICATION
				errorIdle();
				changeState(ifaceIndex, states[ifaceIndex], State.IDLE);

			} else if (BGPtype == 4) {			// KEEPALIVE
				if (states[ifaceIndex] != State.OPENCONFIRM && states[ifaceIndex] != State.ESTABLISHED) {
					sendNotificationMessage(ifaceIndex, 1);
					errorIdle();
					changeState(ifaceIndex, states[ifaceIndex], State.IDLE);
					return;
				}
				if (states[ifaceIndex] == State.ESTABLISHED) {
					timeChange[ifaceIndex] = true;
					return;
				}
				changeState(ifaceIndex, State.OPENCONFIRM, State.ESTABLISHED);
			}
		} else									// TCP Packet
		{
			TCP tcp = new TCP(data, 34, 5);
			if (states[ifaceIndex] == State.IDLE)					// IDLE
				return;
			if (states[ifaceIndex] == State.CONNECT) {				// CONNECT
				if (tcp.getAck() && tcp.getSYN()) {                // SYN-ACK
					sendSynAckMessage(ifaceIndex, 1);
					sendOpenMessage(ifaceIndex);
					changeState(ifaceIndex, State.CONNECT, State.OPENSTATE);
				} else if (!tcp.getAck() && tcp.getSYN()) {        // SYN
					sendSynAckMessage(ifaceIndex, 3);
				} else if (tcp.getAck() && !tcp.getSYN()) {		 	// ACK
					sendOpenMessage(ifaceIndex);
					changeState(ifaceIndex, State.CONNECT, State.OPENSTATE);
				}
				return;
			}
			if (states[ifaceIndex] == State.ACTIVESTATE) {			// ACTIVE STATE
				if (tcp.getAck() && tcp.getSYN()) {					// SYN-ACK
					sendSynAckMessage(ifaceIndex, 1);
					sendOpenMessage(ifaceIndex);
					changeState(ifaceIndex, State.ACTIVESTATE, State.OPENSTATE);
				}
				return;
			}
			return;
		}

	}

	public void run() {
		Scanner in = new Scanner(System.in);
		while (true) {
			String command = in.nextLine();
			if (command.startsWith("start")) {								// START CONNECTION
				String[] lines = command.split(" ");
				int interfaceIndex = Integer.parseInt(lines[4]);
				if (getStart[interfaceIndex] == false && states[interfaceIndex] == State.IDLE)
					receiveStartCommand(interfaceIndex);
			}
			else if (command.startsWith("priority")) {                        // PRIORITY
				String[] lines = command.split(" ");
				int interfaceIndex = Integer.parseInt(lines[2]);
				int newPriority = Integer.parseInt(lines[4]);
				priority[interfaceIndex] = newPriority;
			}
			else if (command.startsWith("advertise")) {
				processAdvertiseAll();
			}
			else if (command.startsWith("print")) {
				String[] lines = command.split(" ");
				int posOfSlash = lines[3].lastIndexOf('/');
				int prefixIP = Utility.getIP(lines[3].substring(0, posOfSlash));
				int prefixMask = Integer.parseInt(lines[3].substring(posOfSlash+1));
				printRoutes(prefixIP, prefixMask);
			}
			else if (command.startsWith("withdraw")) {
				String[] lines = command.split(" ");
				int posOfSlash = lines[1].lastIndexOf('/');
				int prefixIP = Utility.getIP(lines[1].substring(0, posOfSlash));
				int prefixMask = Integer.parseInt(lines[1].substring(posOfSlash+1));
				processWithdraw(prefixIP, prefixMask, -1);
			}
			else if (command.startsWith("hijack")) {
				String[] lines = command.split(" ");
				int posOfSlash = lines[1].lastIndexOf('/');
				int prefixIP = Utility.getIP(lines[1].substring(0, posOfSlash));
				int prefixMask = Integer.parseInt(lines[1].substring(posOfSlash+1));
				hijack(prefixIP, prefixMask);
			}
		}
	}

	public void receiveStartCommand(int interfaceIndex) {						// Received Start
		getStart[interfaceIndex] = true;
		sendSynAckMessage(interfaceIndex, 2);
		changeState(interfaceIndex, State.IDLE, State.CONNECT);
		timer[interfaceIndex].start();
		if (timer[interfaceIndex].isAlive() == false) {
			getStart[interfaceIndex] = false;
			changeState(interfaceIndex, State.CONNECT, State.IDLE);
			states[interfaceIndex] = State.IDLE;
		}
	}

	public void sendTCPFrame(EthernetHeader ethernetHeader, IPv4Header iPv4Header, TCP tcp, int interfaceIndex) {
		byte[] data = new byte[ethernetHeader.getData().length + iPv4Header.getData().length + tcp.getData().length];
		System.arraycopy(ethernetHeader.getData(), 0, data, 0, ethernetHeader.getData().length);
		System.arraycopy(iPv4Header.getData(), 0, data, ethernetHeader.getData().length, iPv4Header.getData().length);
		System.arraycopy(tcp.getData(), 0, data, ethernetHeader.getData().length + iPv4Header.getData().length, tcp.getData().length);
		sendFrame(new Frame(data.length, data), interfaceIndex);
	}

	public void sendBGPFrame(EthernetHeader ethernetHeader, IPv4Header iPv4Header, BGP bgp, int interfaceIndex) {
		byte[] data = new byte[ethernetHeader.getData().length + iPv4Header.getData().length + bgp.getData().length];
		System.arraycopy(ethernetHeader.getData(), 0, data, 0, ethernetHeader.getData().length);
		System.arraycopy(iPv4Header.getData(), 0, data, ethernetHeader.getData().length, iPv4Header.getData().length);
		System.arraycopy(bgp.getData(), 0, data, ethernetHeader.getData().length + iPv4Header.getData().length, bgp.getData().length);
		sendFrame(new Frame(data.length, data), interfaceIndex);
	}

	public void sendSynAckMessage(int interfaceIndex, int code) {
		EthernetHeader ethernetHeader = new EthernetHeader(EthernetHeader.BROADCAST, EthernetHeader.BROADCAST, 0x0800);
		IPv4Header iPv4Header = new IPv4Header();
		iPv4Header.setSrc(iface[interfaceIndex].getIp());
		iPv4Header.setDest(neighbourIP[interfaceIndex]);
		iPv4Header.setTTL(255);
		iPv4Header.setTotalLength(40);
		TCP tcp = new TCP();
		if ((code & 2) != 0)
			tcp.setSYN(true);
		if ((code & 1) != 0)
			tcp.setACK(true);
		sendTCPFrame(ethernetHeader, iPv4Header, tcp, interfaceIndex);
	}

	public void sendNotificationMessage(int interfaceIndex, int code) {
		EthernetHeader ethernetHeader = new EthernetHeader(EthernetHeader.BROADCAST, EthernetHeader.BROADCAST, 0x0800);
		IPv4Header iPv4Header = new IPv4Header();
		iPv4Header.setSrc(iface[interfaceIndex].getIp());
		iPv4Header.setDest(neighbourIP[interfaceIndex]);
		iPv4Header.setTTL(255);
		iPv4Header.setTotalLength(40);
		BGP bgp = new BGP(code);
		sendBGPFrame(ethernetHeader, iPv4Header, bgp, interfaceIndex);
	}

	public void sendOpenMessage(int interfaceIndex) {
		EthernetHeader ethernetHeader = new EthernetHeader(EthernetHeader.BROADCAST, EthernetHeader.BROADCAST, 0x0800);
		IPv4Header iPv4Header = new IPv4Header();
		iPv4Header.setSrc(iface[interfaceIndex].getIp());
		iPv4Header.setDest(neighbourIP[interfaceIndex]);
		iPv4Header.setTTL(255);
		iPv4Header.setTotalLength(49);
		BGP bgp = new BGP(ASNumber, iface[interfaceIndex].getIp());
		sendBGPFrame(ethernetHeader, iPv4Header, bgp, interfaceIndex);
	}

	public void sendKeepaliveMessage(int interfaceIndex) {
		EthernetHeader ethernetHeader = new EthernetHeader(EthernetHeader.BROADCAST, EthernetHeader.BROADCAST, 0x0800);
		IPv4Header iPv4Header = new IPv4Header();
		iPv4Header.setSrc(iface[interfaceIndex].getIp());
		iPv4Header.setDest(neighbourIP[interfaceIndex]);
		iPv4Header.setTTL(255);
		iPv4Header.setTotalLength(39);
		BGP bgp = new BGP();
		sendBGPFrame(ethernetHeader, iPv4Header, bgp, interfaceIndex);
	}

	public boolean checkOpenMessage(BGP bgp) {
		byte[] data = bgp.getData();

		for (int i = 0 ; i < 16 ; i++)
			if (data[i] != (byte)0xFF)
				return false;
		if (data[16] != 0)
			return false;
		if (data[17] != 29)
			return false;
		if (data[18] != 1)
			return false;
		if (data[19] != 4)
			return false;
		if (data[22] != 0 || data[23] != 0)
			return false;
		if (data[28] != 0)
			return false;
		return  true;
	}


	public void addPaths(byte[] data, int interfaceIndex) {

		int len = Utility.convertBytesToShort(Arrays.copyOfRange(data, 0, 2));
		Path[] tempPaths = new Path[len];
		byte[] tempData = Arrays.copyOfRange(data, 2, data.length);
		for (int i = 0 ; i < len ; i++) {
			int pathLength = Utility.convertBytesToShort(Arrays.copyOfRange(tempData, 0, 2));
			byte[] AS = Arrays.copyOfRange(tempData, 2, 2*(pathLength+1));
			tempPaths[i] = new Path(AS, ASNumber, interfaceIndex);
			tempData = Arrays.copyOfRange(tempData, 2*(pathLength+1), tempData.length);
		}
		for (int i = 0 ; i < len ; i++) {
			byte[] prefix = Arrays.copyOfRange(tempData, 0, 5);
			tempPaths[i].addPrefixToPath(prefix);
			tempData = Arrays.copyOfRange(tempData, 5, tempData.length);
		}
		for (int i = 0 ; i < tempPaths.length ; i++) {
			int prefixIP = tempPaths[i].prefixIP;
			int prefixMask = tempPaths[i].prefixMask;
			int originAS = tempPaths[i].ASNumbers.get(tempPaths[i].ASNumbers.size() - 1);
			if (paths.contains(tempPaths[i]) == false && tempPaths[i].isValid()) {
				boolean possible = true;
				for (int j = 0; j < paths.size(); j++) {
					Path temp = paths.get(j);
					int tempPrefixIP = temp.prefixIP;
					int tempPrefixMask = temp.prefixMask;
					int tempOriginAS = temp.ASNumbers.get(temp.ASNumbers.size() - 1);
					if (prefixIP == tempPrefixIP && prefixMask == tempPrefixMask && originAS != tempOriginAS)
						possible = false;
				}
				if (possible)
					paths.add(tempPaths[i]);
				else
					System.out.println(Utility.getIPString(prefixIP)+"/"+prefixMask + " is hijacked!");
			}
		}
	}

	public void processAdvertiseAll() {
		for (int i = 0 ; i < numberOfInterfaces ; i++) {
			if (states[i] == State.ESTABLISHED)
				processAdvertiseInterface(i);
		}
	}

	public void processAdvertiseInterface(int interfaceIndex) {
		ArrayList<Byte> pathAttributes, networkLayer;
		pathAttributes = new ArrayList<Byte>();
		networkLayer = new ArrayList<Byte>();
		int totalPath = 0;
		for (int i = 0 ; i < paths.size() ; i++) {
			Path tempPath = paths.get(i);
			if (tempPath.interfaceIndex != -1 && neighbours[interfaceIndex] == Relationship.Peer && neighbours[tempPath.interfaceIndex] == Relationship.Peer)
				continue;
			if (tempPath.interfaceIndex != -1 && neighbours[interfaceIndex] == Relationship.Provider && neighbours[tempPath.interfaceIndex] == Relationship.Provider)
				continue;
			totalPath++;
			byte[] prefixIP = Utility.getBytes(tempPath.prefixIP);
			for (int j = 0 ; j <= 3 ; j++)
				networkLayer.add(prefixIP[j]);
			networkLayer.add((byte) tempPath.prefixMask);
			int pathSize = tempPath.ASNumbers.size();
			pathAttributes.add(Utility.getBytes((short) pathSize)[0]);
			pathAttributes.add(Utility.getBytes((short) pathSize)[1]);
			for (int j = 0 ; j < pathSize ; j++) {
				pathAttributes.add(Utility.getBytes(tempPath.ASNumbers.get(j).shortValue())[0]);
				pathAttributes.add(Utility.getBytes(tempPath.ASNumbers.get(j).shortValue())[1]);
			}
			tempPath.advertisedInterfaces.add(interfaceIndex);
			paths.set(i, tempPath);
		}
		byte[] pathAtt = new byte[pathAttributes.size()];
		for (int i = 0 ; i < pathAttributes.size() ; i++)
			pathAtt[i] = pathAttributes.get(i);
		byte[] networkLayerArray = new byte[networkLayer.size()];
		for (int i = 0 ; i < networkLayer.size() ; i++)
			networkLayerArray[i] = networkLayer.get(i);

		BGP bgp = new BGP(totalPath, pathAtt, networkLayerArray);

		EthernetHeader ethernetHeader = new EthernetHeader(EthernetHeader.BROADCAST, EthernetHeader.BROADCAST, 0x0800);
		IPv4Header iPv4Header = new IPv4Header();
		iPv4Header.setSrc(iface[interfaceIndex].getIp());
		iPv4Header.setDest(neighbourIP[interfaceIndex]);
		iPv4Header.setTTL(255);
		iPv4Header.setTotalLength(20 + bgp.getData().length);

		sendBGPFrame(ethernetHeader, iPv4Header, bgp, interfaceIndex);
	}

	public void printRoutes(int prefixIP, int prefixMask) {
		ArrayList<Integer> indices = new ArrayList<Integer>();
		for (int i = 0 ; i < paths.size() ; i++) {
			Path tempPath = paths.get(i);
			if (tempPath.prefixMask == prefixMask && tempPath.prefixIP == prefixIP)
				indices.add(i);
		}
		if (indices.size() == 0) {
			System.out.println("no routes found for " + Utility.getIPString(prefixIP) + "/" + prefixMask);
			return;
		}
		indices = sortIndices(indices);
		for (int i = 0 ; i < indices.size() ; i++) {
			Path tempPath = paths.get(indices.get(i));
			for (int j = 0 ; j < tempPath.ASNumbers.size() ; j++)
				System.out.print(tempPath.ASNumbers.get(j) + " ");
			System.out.println(Utility.getIPString(prefixIP) + "/" + prefixMask);
		}
	}

	public ArrayList<Integer> sortIndices(ArrayList<Integer> indices) {
		for (int i = 0 ; i < indices.size() ; i++)
			for (int j = i+1 ; j < indices.size() ; j++) {
				int first = indices.get(i);
				int second = indices.get(j);
				Path p1 = paths.get(first);
				Path p2 = paths.get(second);
				if (!comparePath(p1, p2)) {
					indices.set(i, second);
					indices.set(j, first);
				}
			}
		return indices;
	}

	public boolean comparePath(Path p1, Path p2) {
		if (priority[p1.interfaceIndex] < priority[p2.interfaceIndex])
			return false;
		if (priority[p1.interfaceIndex] > priority[p2.interfaceIndex])
			return true;
		if (p1.ASNumbers.size() > p2.ASNumbers.size())
			return false;
		if (p1.ASNumbers.size() < p2.ASNumbers.size())
			return true;
		if (p1.interfaceIndex > p2.interfaceIndex)
			return false;
		if (p1.interfaceIndex < p2.interfaceIndex)
			return true;
		for (int k = 0 ; k < p1.ASNumbers.size() ; k++) {
			int p1as = p1.ASNumbers.get(k);
			int p2as = p2.ASNumbers.get(k);
			if (p1as < p2as)
				return true;
			if (p2as < p1as)
				return true;
		}
		return true;
	}

	public void processWithdraw(int prefixIP, int prefixMask, int interfaceIndex) {

		ArrayList<Integer> interfaces = new ArrayList<Integer>();
		for (int i = paths.size()-1 ; i >= 0 ; i--) {
			Path tempPath = paths.get(i);
			if (tempPath.prefixIP == prefixIP && tempPath.prefixMask == prefixMask && ((interfaceIndex != -1 && tempPath.interfaceIndex == interfaceIndex) || interfaceIndex == -1)) {
				paths.remove(i);
				for (int j = 0 ; j < tempPath.advertisedInterfaces.size() ; j++) {
					interfaces.add(tempPath.advertisedInterfaces.get(j));
				}
			}
		}

		HashSet<Integer> uniqueInterfaces = new HashSet<>(interfaces);


		for (int ifaceIndex : uniqueInterfaces) {
			BGP bgp = new BGP(1, prefixIP, prefixMask);
			EthernetHeader ethernetHeader = new EthernetHeader(EthernetHeader.BROADCAST, EthernetHeader.BROADCAST, 0x0800);
			IPv4Header iPv4Header = new IPv4Header();
			iPv4Header.setSrc(iface[ifaceIndex].getIp());
			iPv4Header.setDest(neighbourIP[ifaceIndex]);
			iPv4Header.setTTL(255);
			iPv4Header.setTotalLength(20 + bgp.getData().length);
			if (states[ifaceIndex] == State.ESTABLISHED)
				sendBGPFrame(ethernetHeader, iPv4Header, bgp, ifaceIndex);
		}
	}

	public void hijack(int prefixIP, int prefixMask) {

		byte[] data = new byte[32];
		// Set marker
		for (int i = 0; i < 16; i++)
			data[i] = (byte) 0xFF;

		// Set length
		data[16] = 0;
		data[17] = 32;

		// Set type
		data[18] = 2;

		// Set Withdrawn Routes Length
		data[19] = 0;
		data[20] = 0;

		// Set Total Path Attribute Length
		data[21] = 0;
		data[22] = 1;

		// Set Path Attributes
		data[23] = 0;
		data[24] = 1;

		System.arraycopy(Utility.getBytes((short)ASNumber), 0, data, 25, 2);

		// Set Network Layer
		System.arraycopy(Utility.getBytes(prefixIP), 0, data, 27, 4);
		data[31] = (byte) prefixMask;

		BGP bgp = new BGP(data);

		for (int i = 0 ; i < numberOfInterfaces ; i++) {
			EthernetHeader ethernetHeader = new EthernetHeader(EthernetHeader.BROADCAST, EthernetHeader.BROADCAST, 0x0800);
			IPv4Header iPv4Header = new IPv4Header();
			iPv4Header.setSrc(iface[i].getIp());
			iPv4Header.setDest(neighbourIP[i]);
			iPv4Header.setTTL(255);
			iPv4Header.setTotalLength(20 + bgp.getData().length);

			if (states[i] == State.ESTABLISHED)
				sendBGPFrame(ethernetHeader, iPv4Header, bgp, i);
		}
	}


	public boolean checkUpdateMessage(BGP bgp) {
		byte[] data = bgp.getData();
		if (data.length < 28)
			return false;

		for (int i = 0 ; i < 16 ; i++)
			if (data[i] != (byte)0xFF)
				return false;

		if (Utility.convertBytesToShort(Arrays.copyOfRange(data, 16, 18)) != (byte)data.length)
			return false;
		if (data[18] != 2)
			return false;
		if (data[19] == 0 && data[20] == 1) {
			if (data.length != 28)
				return false;
			if (data[25] < 0 || data[25] > 32)
				return false;
			if (data[26] != 0)
				return false;
			if (data[27] != 0)
				return false;
			return true;
		}
		if (data[19] == 0 && data[20] == 0) {
			int tot = Utility.convertBytesToShort(Arrays.copyOfRange(data, 21, 23));
			data = Arrays.copyOfRange(data, 23, data.length);
			if (data.length < tot * 5)
				return false;
			byte[] prefixes = Arrays.copyOfRange(data, data.length-5*tot, data.length);
			for (int i = 0 ; i < tot ; i++)
				if (prefixes[5*i+4] > 32 || prefixes[5*i+4] < 0)
					return false;
			data = Arrays.copyOfRange(data, 0, data.length-5*tot);
			for (int i = 0 ; i < tot ; i++) {
				if (data.length < 2)
					return false;
				int pathLength = Utility.convertBytesToShort(Arrays.copyOfRange(data, 0, 2));
				if (data.length < (pathLength+1) * 2)
					return false;
				data = Arrays.copyOfRange(data, (pathLength+1) * 2, data.length);
			}
			if (data.length != 0)
				return false;
			return true;
		}
		return false;
	}
}
