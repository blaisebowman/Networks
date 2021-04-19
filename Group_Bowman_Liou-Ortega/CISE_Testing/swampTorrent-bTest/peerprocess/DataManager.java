package peerprocess;

import common.Constants;
import peerprocess.session.Session;

import java.io.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.Vector;

import static common.Constants.PEER_ID;
import peerprocess.Handler;

/*
 * DataManager should handle the relationships and metadata between
 * the various peers. What peers are mapped to the current peer instance, what are their
 * bitfields, what peers are choked, which peer is choking the current instance, which
 * peers is requesting from current peer, which peer has a pending request from current peer
 * etc.
 */

public class DataManager {

	/*
	 * Each Peer needs to know of other:
	 * 		Peer -> Peers;
	 * 		What Peers this Peer is choking -> int [] arrChoking;
	 * 		Which Peers are choking this Peer -> int [] chokedBy;
	 * 		Which Peers are interested in this Peer - >
	 * 		Bitfields -> private static Bitfield bitfield;
	 * 		Downloading Rates ->
	 * 		Pieces this Peer has already Requested ->
	 * 		Field Requesting from Peers ->
	 *
	 */
	public HashMap<Integer, Peer> peerInfoMap; // map: peerID -> Peer:<host, port>

	public HashMap<Integer, Boolean> chokeMap; // map: peerID -> if true: this peer chokes peerID. if false: this peer unchokes peerID

	public HashMap<Integer, Boolean> gettingChokedMap; // map: peerID --> if true: this peer is choked by peerID. if false: this peer is not getting choked by peerID

	public HashMap<Integer, Boolean> interestedMap; // map: peerID --> if true: peerID is interested in this peer. if false: peerID is not interested in this peer.

	public HashMap <Integer, boolean []> booleanFieldMap;

	public boolean[] requesting; // array: which pieces the peer is requesting

	public HashMap<Integer, Boolean> pendingRequestMap; // map: peerId -> if true: peerID has a request from this peer but has not replied back. if false: none

	public HashMap<Integer, Integer> requestedMap; // map: peerId -> piece that peerId is requesting from this peer

	public HashMap<Integer, Integer> downloadRatesMap; //map: peerId -> download rate (in bytes? blocks? pieces?)

	public HashMap<Integer, Session> sessionMap; // map: peerId -> Session: Maps peerID to its session

	private static DataManager instance;

	public static int numOfUnfinishedPeers;

	public static int numOfPeers;

	private int[] arrPreferredPeers; //array of the preferred peer(s)'s ID's

	private String hostname;

	private int portNum;

	private int unchokedPeer;

	private int numPeers;

	public int portNumberCurr;

	boolean isDone;

	private Random random;

	private PeerLog log;

	private Handler handler;

	public DataManager() throws FileNotFoundException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		peerInfoMap = new HashMap<>();
		chokeMap = new HashMap<>();
		gettingChokedMap = new HashMap<>();
		interestedMap = new HashMap<>();
		pendingRequestMap = new HashMap<>();
		downloadRatesMap = new HashMap<>();
		sessionMap = new HashMap<>();
		booleanFieldMap = new HashMap<>();
		random = new Random();
		requestedMap = new HashMap<>();
		log = PeerLog.getLogger(Constants.PEER_ID);
		readPeerInfoCFG();
		
	}

	public static DataManager getInstance() throws FileNotFoundException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		if (instance == null) {
			instance = new DataManager();
		}
		return instance;
	}

	public void set() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException{
		handler = Handler.getInstance();
	}


	private static byte booleansGoByte (boolean [] b, int origin){
		byte byter = 0;
		for (int i = 0; i < 8; i++){
			if (b[origin + i]){
				byter |= 1 << (7 - i);
			}
		}
		return byter;
	}

	public static byte [] toByteArray (boolean [] booleanArray){
		byte [] arr_bytes = new byte [booleanArray.length / 8];
		for (int i = 0; i < arr_bytes.length; i++){
			arr_bytes[i] = booleansGoByte(booleanArray, 8 * i);
		}
		return arr_bytes;
	}

	public static boolean [] toBooleanArray (byte [] byteArray){
		BitSet bits = BitSet.valueOf(byteArray);
		boolean [] booleanArray = new boolean [byteArray.length * 8];
		for (int i = bits.nextSetBit(0); i != -1; i = bits.nextSetBit(i + 1)){
			booleanArray[i] = true;
		}
		return booleanArray;
	}

	private void readPeerInfoCFG() throws FileNotFoundException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		log.info("Begin readPeerInfoCFG()");
		int numPieces;
		int temp = Constants.FILE_SIZE / Constants.PIECE_SIZE;
		if(temp * Constants.PIECE_SIZE == Constants.FILE_SIZE) {
			numPieces = temp;
		}
		else {
			numPieces = temp + 1;
		}
		String file = Constants.PEER_INFO_FILE;
		BufferedReader reader = new BufferedReader(new FileReader(file));
		int lines = 0;
		while (reader.readLine() != null) lines++;
		reader.close();
		Scanner scan;
		try {
			scan = new Scanner(new File(file));

		} catch (FileNotFoundException e) {
			throw new RuntimeException("Cannot find " + file + " .");
		}

		for(int i = 0; i < lines; i++){
			String line = scan.nextLine();
			String[] arr = line.split(" ");
			int id = Integer.parseInt(arr[0]);
			String peerHost = arr[1];
			int port = Integer.parseInt(arr[2]);
			peerInfoMap.put(id, new Peer(peerHost, port));

			int hasFile = Integer.parseInt(arr[3]);
			String has = hasFile > 0 ? "has the file" : "doesn't have the file";
			log.info("Peer " + id + " has host " + peerHost + " at port " + port + " and " + has);
			// initiate bitfield for peer
			boolean[] bitfield = new boolean[Constants.NUMBER_OF_FIELDS];
			Arrays.fill(bitfield, hasFile > 0);
			booleanFieldMap.put(id, bitfield);
			log.info("Peer " + id +" bitfield: " + booleanArrToString(bitfield));
			if (id == Constants.PEER_ID) {
				FileManager.getInstance().peerHasFile(hasFile > 0);
			}
			// initiate peer choke
			chokeMap.put(id, true);

			// start process with every other peer choking this peer.
			gettingChokedMap.put(id, true);

			// start process with every other peer not interested in this peer
			interestedMap.put(id, false);

			// start process with requesting all false
			boolean[] isRequesting = new boolean[Constants.NUMBER_OF_FIELDS];
			Arrays.fill(isRequesting, false);

			// start process with no requests
			pendingRequestMap.put(id, false);

			// start process with download rate 0
			downloadRatesMap.put(id, 0);


			// increases number of peers list. When reads self, sets number of active peers equal to number of peers counted.
			Constants.NUMBER_OF_PEERS++;
			if (id == Constants.PEER_ID) {
				Constants.NUMBER_OF_ACTIVE_PEERS = Constants.NUMBER_OF_PEERS;
			}
		}
		scan.close();

		// start process with no fills requested.
		requesting = new boolean[Constants.NUMBER_OF_FIELDS];
		Arrays.fill(requesting, false);
	}

	public int getPortNum (int peer_id){
		return peerInfoMap.get(peer_id).port;
	}


	public synchronized int getPeerId(Session session){
		ArrayList<Integer> peerList = new ArrayList<>(sessionMap.keySet());
		for (int i = 0; i < peerList.size(); i++) {
			int id = peerList.get(i);
			if (sessionMap.get(id).equals(session)) {
				return id;
			}
		}
		return -1;
	}

	public boolean[] getBooleanField(int peerId) {
		return booleanFieldMap.get(peerId);
	}

	public int getCurrFieldNumber (int peerId){
		boolean[] field = booleanFieldMap.get(peerId);
		int count = 0;
		for (int i = 0; i < field.length; i++) {
			if (field[i]) {
				count++;
			}
		}
		return count;
	}

	public boolean isInterested(int peerId){
		boolean [] current = booleanFieldMap.get(Constants.PEER_ID);
		boolean [] peerInQuestion = booleanFieldMap.get(peerId);
		if(peerInQuestion != null){
			for (int i = 0; i < Constants.NUMBER_OF_FIELDS; i++){
				if(!current[i] && peerInQuestion[i] && !requesting[i]){
					return true;
				}
			}
		}
		return false;
	}


	public void getPrefferedNeighbors () throws IOException {
		log.info("Start reselect preferred neighbors");
		ArrayList<Integer> peerId_list = new ArrayList<>(sessionMap.keySet());
		
		for (int i = 0; i < peerId_list.size(); i++){
			for (int j = i + 1; j < peerId_list.size(); j++){
				if(downloadRatesMap.get(peerId_list.get(i)) < downloadRatesMap.get(peerId_list.get(j))){
					int tmp = peerId_list.get(i);
					peerId_list.set(i, peerId_list.get(j));
					peerId_list.set(j, tmp);
				}
				else if((downloadRatesMap.get(peerId_list.get(i))).equals(downloadRatesMap.get(peerId_list.get(j)))){
					int tmp = random.nextInt(2);
					if (tmp == 1){
						tmp = peerId_list.get(i);
						peerId_list.set(i, peerId_list.get(j));
						peerId_list.set(j, tmp);
					}
				}
			}
		}
		String prefer = "";

		log.info("Number of preferred neighbors: " + Constants.NUMBER_OF_PREFERRED_NEIGHBORS);
		for (int i = 0; i < peerId_list.size(); i++){
			if(i < Constants.NUMBER_OF_PREFERRED_NEIGHBORS){
				prefer = prefer + peerId_list.get(i) + ",";
			}
		}
		log.info ("Peer " + Constants.PEER_ID + " has the preferred neighbors: " + prefer); //list of preferred neighbors
		//select peer
		for (int i = 0; i < peerId_list.size(); i++){
			int peerId = peerId_list.get(i);
			if (i < Constants.NUMBER_OF_PREFERRED_NEIGHBORS){
				if(chokeMap.get(peerId)){
					chokeMap.put(peerId, false);
					Session s = sessionMap.get(peerId);
					if (s != null) {
						log.info("Preferred Neighbors: Send unchoke msg to peer " + peerId);
						handler.sendUnchokeMsg(s);
						log.info("requestedMap.size(): " + requestedMap.size());
						if(requestedMap.containsKey(peerId)){
							log.info("Solve pending request: Send piece " + requestedMap.get(peerId) + " to peer " + peerId);
							handler.sendPieceMsg(requestedMap.get(peerId), s);
							requestedMap.remove(peerId);
						}
					}
				}
			}
			else {
				if(!chokeMap.get(peerId)){
					chokeMap.put(peerId, true);
					Session s = sessionMap.get(peerId);
					if (s != null){
						handler.sendChokeMsg(true, s);
						log.info("Preferred Neighbors: Send choke msg to peer " + peerId);
					}
				}
			}
		}
		for (Integer integer : peerId_list) {
			downloadRatesMap.put(integer, 0);
		}
	}

	public boolean isPeerServer(String hostName, int portNumber){
		// Changes on CISE machines -> hostName is passed as the host's IP address; no changes in portNumber
		// BEFORE: for peer_1003, hostName = lin115-02.cise.ufl.edu/10.242.94.79
		//NOW: peer_1003 is now passed with is passed hostName = 10.242.94.79
		ArrayList<Peer> listPeers = new ArrayList<>(peerInfoMap.values());
		for (int i = 0; i < listPeers.size(); i++) {
			try {
				//for each peer in the peer list we need to get it's IP address from it's host name
				//ex for peer_1003, peer_1002 is a previous peer
				InetAddress inet = InetAddress.getByName(listPeers.get(i).getHost()); //ex:lin115-01.cise.ufl.edu/10.242.94.79
				String ipAddr = inet.getHostAddress(); //ex: 10.242.94.79
				/*log.info(listPeers.get(i) + " " + listPeers.get(i).getHost() + " " + listPeers.get(i).getPort());
				log.info("listPeers.get(i).getHost() = " + listPeers.get(i).getHost());
				log.info("hostName = " + hostName);
				log.info("listPeers.get(i).getPort()" + listPeers.get(i).getPort());
				log.info("portNum = " + portNumber);*/
				if (ipAddr.equals(hostName) && listPeers.get(i).getPort() == portNumber) {
					//so, if 10.242.94.97 equals 10.242.94.97 and port numbers are equal, then it is a peer server
					log.info("isPeerServer = true ");
					return true;
				}
			}
			catch (UnknownHostException e){
				e.printStackTrace();
			}
		}
		log.info("isPeerServer = false ");
		return false;
	}


	public Vector<Peer> getPreviousPeers(){
		Vector<Peer> peers = new Vector<>();
		ArrayList<Integer> list = new ArrayList<>(peerInfoMap.keySet());
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) < Constants.PEER_ID) {
				peers.add(peerInfoMap.get(list.get(i)));
			}
		}
		return peers;
	}

	public synchronized void optimisticUnchokedNeighbor() throws IOException {
		log.info("start reselecting optimistic unchoked neighbor");

		ArrayList <Integer> listOfPeers = new ArrayList<>(sessionMap.keySet());
		ArrayList <Integer> possibleNeighbor = new ArrayList<>();
		for (int i = 0; i < listOfPeers.size(); i++){
			int peer_id = listOfPeers.get(i);
			if(chokeMap.get(peer_id) && interestedMap.get(peer_id)){
				possibleNeighbor.add(peer_id);
			}
		}

		//Now, randomly select one of the possible neighbors as the optimistically unchoked neighbor

		if (possibleNeighbor.isEmpty()){
			return; //exit, no possible neighbors
		}

		int select = random.nextInt(possibleNeighbor.size());
		int peer_id = possibleNeighbor.get(select);
		chokeMap.put(peer_id, false);
		Session sesh = sessionMap.get(peer_id);

		if (sesh != null){
			handler.sendChokeMsg(false, sesh);
			log.info("Peer " + Constants.PEER_ID + " has the optimistically unchoked neighbor " + peer_id);
			if(requestedMap.containsKey(peer_id)){
				try {
					handler.sendPieceMsg(requestedMap.get(peer_id), sesh);
					requestedMap.remove(peer_id);
				}
				catch (IOException e){
					// handle
				}
			}
		}
	}

	public void addToInterestedMap(int peer_id, boolean interested){
		interestedMap.put(peer_id, interested);
	}

	public void addChokeToMap (int peer_id, boolean choked){
		chokeMap.put(peer_id, choked);
	}

	public boolean checkChokedByPeer (int peer_id){
		return chokeMap.get(peer_id);
	}

	public boolean peerBeingChoked(int peer_id){
		return gettingChokedMap.get(peer_id);
	}

	public boolean pendingRequest(int peer_id) {
		return pendingRequestMap.get(peer_id);
	}

	public void addRequestToMap (int peer_id){
		pendingRequestMap.put(peer_id, true);
	}

	public void removeRequest(int peer_id){
		pendingRequestMap.put(peer_id, false);
	}

	public void addToRequestedMap (int value){
		requesting[value] = true;
	}

	public void addToBooleanField(int peerId, int field) {
		log.info("Peer " + peerId + ": adds piece " + field);
		booleanFieldMap.get(peerId)[field] = true;
		log.info("Peer " + peerId + ": " + booleanArrToString(booleanFieldMap.get(peerId)));
	}

	public String booleanArrToString(boolean[] arr) {
		String s = "";
		for (int i = 0; i < arr.length; i++) {
			if (arr[i]) {
				s+="1";
			} else {
				s+="0";
			}
		}
		return s;
	}

	public int fieldToRequest (int peer_id){
		ArrayList <Integer> possibleNeighbor = new ArrayList<>();
		boolean [] otherPeersField = booleanFieldMap.get(peer_id);
		boolean [] fieldOfCurrentPeer = booleanFieldMap.get(Constants.PEER_ID);
		for (int i = 0; i < otherPeersField.length; i++){
			if(otherPeersField[i] && !fieldOfCurrentPeer[i] && !requesting[i]){
				possibleNeighbor.add(i);
			}
		}

		if (possibleNeighbor.isEmpty()){
			return - 1; //exit, no possible neighbors
		}
		int randomNeighbor = random.nextInt(possibleNeighbor.size());
		
		//int neighborPeerId = possibleNeighbor.get(randomNeighbor);
		return possibleNeighbor.get(randomNeighbor);
	}

	public synchronized void increaseDownloadRate (int peer_id){
		int tmpVal = downloadRatesMap.get(peer_id);
		tmpVal++; // increase download rate
		downloadRatesMap.put(peer_id, tmpVal);
	}

	/*
	 * checkFileStatusOfAllPeers():
	 * Check every bitfield, if one bitfield is incomplete, break.
	 * If all bitfields are finished
	 * Log it
	 * Close Scheduler, close the filemanager, close handler, close peer network, close all the session and sockets.
	 * Close
	 */

	public synchronized void checkStatusOfPeers ()
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
		log.info("testing if all Peers are finished");
		boolean isDone = true;
		ArrayList <Integer> peer_ids = new ArrayList<>(peerInfoMap.keySet());
		//for (Integer i : peer_ids){
		for (int i = 0; i < peer_ids.size(); i++){
			log.info("Checking..." + peer_ids.get(i));
			boolean[] bitfield = booleanFieldMap.get(peer_ids.get(i));
			for (boolean a : bitfield){
				isDone &= a;
				if (!isDone){
					break; //if any bitfield is not complete, break
				}
			}
			if(!isDone){
				log.info(peer_ids.get(i)+ " is not finished");
				log.info("Peer " + peer_ids.get(i) + ": " + booleanArrToString(booleanFieldMap.get(peer_ids.get(i))));
				break;
			}
			log.info(peer_ids.get(i)+ " is finished");
		}
		if(isDone){ //if ALL bitfields are done
			log.info("All peers have received file, terminate process");
			Scheduler.getInstance().stop(); // Close the Scheduler
			FileManager.getInstance().finish(); // Close the File Manager

			ArrayList<Integer> sessionPeerIds = new ArrayList<>(sessionMap.keySet());
			for (int i = 0; i < sessionPeerIds.size(); i++) {
				int p = sessionPeerIds.get(i);
				if (p > Constants.PEER_ID) {
					log.info("Closing " + p);
					Session ss = sessionMap.get(p);
					ss.close();
				}
			}
			PeerNetwork.getInstance().close(); // Close the Peer Network
		}
	}

	public synchronized ArrayList<Session> getAllActiveSessions() {
		return new ArrayList<Session>(sessionMap.values());
	}

	public synchronized void addSession(int peerId, Session session) {
		sessionMap.put(peerId, session);
	}
}
