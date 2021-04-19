package peerprocess;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;

import common.Constants;
import peerprocess.DataManager;
import peerprocess.FileManager;
import peerprocess.PeerLog;
import peerprocess.session.Session;

/*
 *This program could handle the interactions between each peer. It will need both
 *DataManager and FileManager to do so. The interactions are the actual communication 
 *and handshakes being passed between each peer as well as the transfer of each piece by bitfield.
 *Sending interested messages, choked messages, requests, and searching for which peer has the piece
 *needed and requesting that piece from that peer.
 */
public class Handler {
	
	private static Handler instance;
	private PeerLog log;
	private static DataManager dataManager;
	private FileManager fileManager;
	
	public Handler() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		dataManager = DataManager.getInstance();
		fileManager = FileManager.getInstance();
		log = PeerLog.getLogger(Constants.PEER_ID);
		log.info("Handler instanced");
	}
	
	public static Handler getInstance() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
		if (instance == null) {
			instance = new Handler();
		}
		return instance;
	}
	
	// Should call when there is a new TCP connection to THIS peer
	public synchronized void connectSession(Session session) throws Exception {
			log.info("Connected to session " + session.getRemoteAddress());
			String addr = String.valueOf(session.getRemoteAddress()); //format: "/host:port
			String[] tmp = addr.split(":");
			//splits ex: lin115-00.cise.ufl.edu/10.242.94.77:6008 into:
			// tmp [0] = lin115-00.cise.ufl.edu/10.242.94.77
			// tmp [1] = 6008
			String hostTmp = tmp[0];
			String []  hostSplit = hostTmp.split("/");
			//splits ex: lin115-00.cise.ufl.edu/10.242.94.77 into:
			// hostSplit[0] = lin115-00.cise.ufl.edu
			// hostSplit[1] = 10.242.94.77
			String host = hostSplit[1]; //now returns host as 10.242.94.77, the host's IP address
			//String host = "localhost"; //for running on local machine
			int port = Integer.parseInt(tmp[1]);
			log.info(host + " " + port + " checking if is PeerServer: " + dataManager.isPeerServer(host, port));
			if (dataManager.isPeerServer(host, port)) {
				sendHandShakeMsg(session);
			}
	}
	
	private void sendHandShakeMsg(Session session) throws UnsupportedEncodingException, IOException {
		byte[] msg = new byte[32];
		byte[] tmp = new String("P2PFILESHARINGPROJ").getBytes("UTF-8");
		
		for (int i = 0; i < tmp.length; i++) {
			msg[i] = tmp[i];
		}
		for (int i = 0; i < 10; i++) {
			msg[i+18] = (byte)0;
		}
		int p = Constants.PEER_ID;
		msg[28] = (byte)(p >> 24 & 0xFF);
		msg[29] = (byte)(p >> 16 & 0xFF);
		msg[30] = (byte)(p >> 8 & 0xFF);
		msg[31] = (byte)(p >> 0 & 0xFF);
		log.info(p + " sends handshake to " + session.getRemoteAddress());
		session.write(msg);
	}
	
	// Called when there is a new message sent to THIS peer.
	private synchronized void receiveHandShakeMsg(Session session, byte[] message) throws UnsupportedEncodingException, IOException  {
		byte[] tmp = new byte[4];
		for (int i = 0; i < 4; i++) {
			tmp[i] = message[i+28];
		}
		
		int peerId = (tmp[0] & 0xFF) << 24
				| (tmp[1] & 0xFF) << 16
				| (tmp[2] & 0xFF) << 8
				| (tmp[3] & 0xFF) << 0;
		log.info("Receive handshake message from peer " + peerId);
		dataManager.addSession(peerId, session);
		//Peers introduced by order of size, smallest to largest
		if (peerId > Constants.PEER_ID) {
			log.info("Peer " + Constants.PEER_ID + " is connected from Peer " + peerId);
			sendHandShakeMsg(session);
		} else {
			log.info("Peer " + Constants.PEER_ID + " makes a connection to " + peerId);
			sendBitFieldMsg(session);
		}
	}
	
	// This is called when THIS peer receives a new message: Identifies it's message type.
	public void readMessage(Session session, Object message) throws IOException, SocketException {
		if (message instanceof byte[]) {
			byte[] msg = (byte[])message;
			if (msg.length > 19 && (new String(msg).substring(0, 18)).equals("P2PFILESHARINGPROJ")) {
				receiveHandShakeMsg(session, msg);
			} else {
				Constants.MessageType type = Constants.convertByteToMsgType(msg[4]);
				
				switch(type) {
					case CHOKE:
						receiveChokeMsg(session);
						break;
					case UNCHOKE:
						receiveUnchokeMsg(session);
						break;
					case BITFIELD:
						receiveBitFieldMsg(msg, session);
						break;
					case HAVE:
						receiveHaveMsg(session, msg);
						break;
					case INTERESTED:
						receiveInterestedMsg(session);
						break;
					case NOTINTERESTED:
						receiveNotInterestedMsg(session);
						break;
					case PIECE:
						receivePieceMsg(msg, session);
						break;
					case REQUEST:
						receiveRequestMsg(msg, session);
						break;
					case UNIDENTIFIED:
						log.info("Something messed up");
						break;
				}
			}
		}
	}
	
	// Called when sending a choke message.
	public void sendChokeMsg(boolean isChoking, Session session) throws IOException{
		byte[] msg = new byte[5];
		Arrays.fill(msg, (byte)0);
		msg[4] = Constants.convertMsgTypeToByte(isChoking? Constants.MessageType.CHOKE: Constants.MessageType.UNCHOKE);
		session.write(msg);
		log.info("Peer " + Constants.PEER_ID + " sends 'choke' message to " + dataManager.getPeerId(session));
	}
	
	// Called when receiving a choke message.
	private void receiveChokeMsg(Session session) throws IOException{
		int peerId = dataManager.getPeerId(session);
		dataManager.gettingChokedMap.put(peerId, true);
		log.info("Peer " + Constants.PEER_ID + " is choked by " + peerId);
	}
	
	// Called when sending an unchoke message.
	public void sendUnchokeMsg(Session session) throws IOException{
		byte[] msg = new byte[5];
		Arrays.fill(msg, (byte)0);
		msg[4] = Constants.convertMsgTypeToByte(Constants.MessageType.UNCHOKE);
		session.write(msg);
		log.info("Peer " + Constants.PEER_ID + " sends 'unchoke' message to " + dataManager.getPeerId(session));
	}
	
	// Called when receiving an unchoke message.
	private void receiveUnchokeMsg(Session session) throws IOException{
		int peerId = dataManager.getPeerId(session);
		dataManager.gettingChokedMap.put(peerId, false);
		log.info("Peer " + Constants.PEER_ID + " is unchoked by" + peerId);
		if (!dataManager.pendingRequestMap.get(peerId)) {
			int field = dataManager.fieldToRequest(peerId);
			log.info("random field to request: "+field);
			if (field != -1) {
				log.info("Request field " + field + " from peer" + peerId);
				sendRequestMsg(field, session);
				dataManager.pendingRequestMap.put(peerId, true);
				dataManager.requestedMap.put(peerId, field);
			}
		}
		
	}
	
	// Called when sending an interested message.
	private void sendInterestedMsg(Session session) {
		byte[] msg = new byte[5];
		Arrays.fill(msg, (byte)0);
		msg[4] = (byte)2;
		session.write(msg);
		log.info("Peer " + Constants.PEER_ID + " sends 'interested' Message to " + dataManager.getPeerId(session));
	}
	
	// Called when receiving an interested message.
	private void receiveInterestedMsg(Session session) throws IOException {
		int peerId = dataManager.getPeerId(session);
		dataManager.interestedMap.put(peerId, true);
		log.info("Peer " + Constants.PEER_ID + " received 'interested' message from " + peerId);
	}
	
	// Called when sending a not interested message.
	private void sendNotInterestedMsg(Session session) {
		byte[] msg = new byte[5];
		Arrays.fill(msg, (byte)0);
		msg[4] = (byte)3;
		session.write(msg);
	}
	
	// Called when receiving a not interested message.
	private void receiveNotInterestedMsg(Session session) throws IOException{
		int peerId = dataManager.getPeerId(session);
		dataManager.interestedMap.put(peerId, false);
		log.info("Peer " + Constants.PEER_ID + " received 'not interested' message from " + peerId);
	}
	
	// Called when sending a have message.
	private void broadcastHaveMsg(int pieceIndexField) throws IOException {
		log.info(Constants.PEER_ID + " broadcasts piece " + pieceIndexField);
		ArrayList<Session> sessionsList = dataManager.getAllActiveSessions();
		byte[] msg = new byte[9];
		msg[4] = Constants.convertMsgTypeToByte(Constants.MessageType.HAVE);
		msg[5] = (byte)(pieceIndexField >> 24);
		msg[6] = (byte)(pieceIndexField >> 16);
		msg[7] = (byte)(pieceIndexField >> 8);
		msg[8] = (byte)(pieceIndexField);
		
		for (int i = 0; i < sessionsList.size(); i++) {
			Session session = sessionsList.get(i);
			session.write(msg);;
		}
	}
	
	// Called when receiving a have message.
	private void receiveHaveMsg(Session session, byte[] msg) throws IOException {
		int peerId = dataManager.getPeerId(session);
		int field = msg[5] << 24
				| (msg[6] & 0xFF) << 16
				| (msg[7] & 0xFF) << 8
				| (msg[8] & 0xFF);
		log.info("Peer " + Constants.PEER_ID + " received the 'have' message from " + peerId + " for the piece " + field);
//		save the peerId and field to the data manager
		dataManager.addToBooleanField(peerId, field);
//		check if this peer has downloaded teh complete file
		int checkIfDownload = dataManager.getCurrFieldNumber(peerId);
		if(dataManager.getCurrFieldNumber(peerId) == Constants.NUMBER_OF_FIELDS){
			//HAS downloaded the complete file
			log.info("Peer " + peerId + " has downloaded the complete file.");
		}
//		send interest or uninterested message back to session
		boolean amInterested = dataManager.isInterested(peerId);
		 //if interested
		 if (amInterested) {
			sendInterestedMsg(session);
		 } else {
			 sendNotInterestedMsg(session);
		 }
	}
	
	// Called when sending a bit field message.
	private void sendBitFieldMsg(Session session) throws IOException {
		// Size of bitfield equals the number of fields divided by 8
		byte[] msg = new byte[5 + (int)Math.ceil(((double)Constants.NUMBER_OF_FIELDS)/8)];
		msg[4] = Constants.convertMsgTypeToByte(Constants.MessageType.BITFIELD);
		
		//need to initiate message lenngth and bitfield of current peer
		msg[0] = (byte)((int)Math.ceil(((double)Constants.NUMBER_OF_FIELDS)/8) >> 24);
		msg[1] = (byte)((int)Math.ceil(((double)Constants.NUMBER_OF_FIELDS)/8) >> 16);
		msg[2] = (byte)((int)Math.ceil(((double)Constants.NUMBER_OF_FIELDS)/8) >> 8);
		msg[3] = (byte)((int)Math.ceil(((double)Constants.NUMBER_OF_FIELDS)/8));
		
		//Insert current bitfield of peerId
		boolean[] bitfield = dataManager.getBooleanField(Constants.PEER_ID);
		
		booleanArrayToBytes(bitfield, msg, 5);
		// convert bitfield into byte and insert it into msg
		session.write(msg);
		log.info("Peer " + Constants.PEER_ID + " sends 'bitfield' message to " + dataManager.getPeerId(session));
	}

	public void booleanArrayToBytes(boolean[] bitfield, byte[] msg, int index) throws IOException {
		byte b = 0;
		int i = index;
		for (int j = 0; j < bitfield.length; j++) {
			if (bitfield[j]) {
				b = (byte)(b | 0x1);
			}
			if (j%8 == 7) {
				msg[i] = b;
				i = i + 1;
			}
			b = (byte) ((b << 1) & 0xFF);
		}

		if (bitfield.length % 8 > 0) {
			b = (byte)((b << (7 - (bitfield.length % 8))) & 0xFF);
			msg[i] = b;
		}
	}
	

	// Called when receiving a bit field message.
	private void receiveBitFieldMsg(byte[] msg, Session session) throws IOException {
		int peerId = dataManager.getPeerId(session);
		log.info("Receive bitfield message from " + peerId);
		// Convert bitfield msg to a bitfield boolean array
		boolean[] bits = new boolean[Constants.NUMBER_OF_FIELDS];
		Arrays.fill(bits, false);
		// reverse the send bitfield process
		int index = 5;
		byte b = msg[index];
		int count = 0;
		while (count < Constants.NUMBER_OF_FIELDS) {
			// single out the leftmost bit, check if it's greater than 0 for true, else false.
			bits[count] = ((b & 0x80) >> 7) > 0;
			count++;
			b = (byte) ((b << 1) & 0xFF);
			if (count % 8 == 0 && count < Constants.NUMBER_OF_FIELDS) {
				index++;
				b = msg[index];
			}
		}

//		add the bitfield from the msg to the datamanager.
		dataManager.booleanFieldMap.put(peerId, bits);
		
		if (peerId > Constants.PEER_ID) {
			sendBitFieldMsg(session);
		}
//		send interest or uninterest message
		boolean isInterested = dataManager.isInterested(peerId);
		if(isInterested) { //if interested
			sendInterestedMsg(session);
		}
		else { //if not interested
			sendNotInterestedMsg(session);
		}
	}
	
	// Called when sending a request message.
	private void sendRequestMsg(int pieceIndexField, Session session) throws IOException {
		byte[] msg = new byte[9];
		Arrays.fill(msg, (byte)0);
		msg[4] = Constants.convertMsgTypeToByte(Constants.MessageType.REQUEST);
		msg[5] = (byte)(pieceIndexField >> 24);
		msg[6] = (byte)(pieceIndexField >> 16);
		msg[7] = (byte)(pieceIndexField >> 8);
		msg[8] = (byte)(pieceIndexField);
		
		session.write(msg);
		log.info("Peer " + Constants.PEER_ID + " sends 'request' message to " + dataManager.getPeerId(session));
		
	}
	
	// Called when receiving a request message.
	private void receiveRequestMsg(byte[] msg, Session session) throws IOException {
//		get peer Id from datamanager
		int peerId = dataManager.getPeerId(session);
//		get field being requested from msg. Convert bytes to integer
		int requestedField = msg[5] << 24 
				| (msg[6] & 0xFF) << 16
				| (msg[7] & 0xFF) << 8
				| (msg[8] & 0xFF);
		log.info("Receive request for field " + requestedField + " from peer " + peerId);
//		
//		if the peer is choked
//			save the request for later until peer is free
//		else
//			send the piece to the session
		if (dataManager.chokeMap.get(peerId)) {
			log.info("Peer " + peerId + " is choked so request for field " + requestedField + " is saved and awaits reply");
			dataManager.requestedMap.put(peerId, requestedField);
		} else {
			log.info("Send piece msg for field " + requestedField + " to peer " + peerId);
			sendPieceMsg(requestedField, session);
		}
	}
	
	// Called when sending a piece message.
	public void sendPieceMsg(int pieceIndexField, Session session) throws IOException {
		
		byte[] bytes = fileManager.getPieceBytes(pieceIndexField);
		int messageLength = 1 + 4 + bytes.length; // 1 byte for msg type, 4 bytes for piece index field, the rest for the data.
		byte[] msg = new byte[4 + messageLength];
		
		msg[0] = (byte)(messageLength >> 24);
		msg[1] = (byte)(messageLength >> 16);
		msg[2] = (byte)(messageLength >> 8);
		msg[3] = (byte)(messageLength);
		msg[4] = Constants.convertMsgTypeToByte(Constants.MessageType.PIECE);
		msg[5] = (byte)(pieceIndexField >> 24);
		msg[6] = (byte)(pieceIndexField >> 16);
		msg[7] = (byte)(pieceIndexField >> 8);
		msg[8] = (byte)(pieceIndexField);
		
		for (int i = 0; i<bytes.length; i++) {
			msg[9+i] = bytes[i];
		}
		session.write(msg);
		log.info("Peer " + Constants.PEER_ID + " sends 'piece' message to " + dataManager.getPeerId(session));
	}
	
	public int getIntegerFrom4Bytes(byte[] msg, int startIndex) {
		int r = msg[startIndex] << 24
				| (msg[startIndex + 1] & 0xFF) << 16
				| (msg[startIndex + 2] & 0xFF) << 8
				| (msg[startIndex + 3] & 0xFF);
		return r;
	}
	// Called when receiving a piece message.
	private void receivePieceMsg(byte[] msg, Session session) throws FileNotFoundException, IOException {
//		get peerId of session and field of msg
		int peerId = dataManager.getPeerId(session);
		int field = msg[5] << 24 
				| (msg[6] & 0xFF) << 16
				| (msg[7] & 0xFF) << 8
				| (msg[8] & 0xFF);
		
		log.info("Receive piece msg of field " + field + " from peer " + peerId);
		int currNumberOfFields = dataManager.getCurrFieldNumber(Constants.PEER_ID);
		log.info("Peer " + Constants.PEER_ID + " has received the piece " + field + " from " + peerId + ". Current number of pieces"
				+ " is now: " + currNumberOfFields);
		
//		if this is a pending request, remove it from the list
		dataManager.pendingRequestMap.put(peerId, false);
		/*
		 * store the data: First 4 bytes of msg is the message length. Reduce by five to not count the bytes for the type and index
		 * In byte array, copy over the payload of the message
		 */
		int dataLength = getIntegerFrom4Bytes(msg, 0) - 5;
		byte[] data = new byte[dataLength];
		for (int i = 0; i < dataLength; i++) {
			data[i] = msg[9 + i];
		}

		fileManager.storePieceBytes(field, data);
//		update download rate
		dataManager.increaseDownloadRate(peerId);
//		add the field into datamanager by updating the booleanfields.
		dataManager.booleanFieldMap.get(Constants.PEER_ID)[field] = true;
//		send have msg to all other peers
		broadcastHaveMsg(field);
//		if this peer is not being choked currently
//			randomly select a field to request and send request message to that peer
//			update the pending requests and currently requesting trackers.
		if (!dataManager.gettingChokedMap.get(peerId)) {
			int newFieldToRequest = dataManager.fieldToRequest(peerId);
			if (newFieldToRequest != -1) {
				log.info("Request field " + newFieldToRequest + " from peer " + peerId);
				sendRequestMsg(newFieldToRequest, session);
				dataManager.pendingRequestMap.put(peerId, true);
				dataManager.requesting[newFieldToRequest] = true;
			}
		}
	}
}