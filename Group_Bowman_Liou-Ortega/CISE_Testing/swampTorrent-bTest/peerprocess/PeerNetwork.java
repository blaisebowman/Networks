package peerprocess;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Vector;

import common.Constants;
import peerprocess.session.Session;

/*
 * PeerNetwork class:
 * Clients connect to two things. A server/tracker that keeps a list of available peers and to other peers
 * in order to communicate. To support multiple simultaneous connections, threads are used so the tracker can
 * handle multiple peers asynchronously.
 */

public class PeerNetwork extends Thread {
	private static PeerNetwork instance;
	private DataManager dataManager;
	private Handler handler;
	private ServerSocket acceptor;
	private PeerLog log;

	public static PeerNetwork getInstance()
			throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		if (instance == null) {
			instance = new PeerNetwork();
		}
		return instance;
	}

	public PeerNetwork() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		log = PeerLog.getLogger(Constants.PEER_ID);
		log.info("PeerNetwork instanced");
		dataManager = DataManager.getInstance();
		handler = Handler.getInstance();
		dataManager.set();
		Scheduler.getInstance().run();
	}

	@Override
	public void run() {
		try {
			startClients();
			startServer();
		} catch (IOException ex) {
			log.severe(ex);
		} catch (Exception ex) {
			log.severe(ex);
		}

	}

	/*
	 * startClients(): Gets list of available clients from the DataManager that are
	 * less than it's id and establish a connection between them.
	 */
	public void startClients() throws Exception {
		log.info("Begin startClients()");
		Vector<Peer> listPeerServers = dataManager.getPreviousPeers();
		for (int i = 0; i < listPeerServers.size(); i ++){
			String clientHostName = listPeerServers.get(i).getHost();
			int clientPortNumber = listPeerServers.get(i).getPort();
			Socket client = new Socket(clientHostName, clientPortNumber); //TO-DO
			log.info("socket created with host: "+clientHostName+" and port "+ clientPortNumber);
			Session session = new Session(client,handler);
			session.start();
			handler.connectSession(session);
		}
	}
	
	/*
	 * startServer():
	 * Creates an unbound server socket that awaits client to make a connection to it.
	 * Once a connection is make, a 'Session' is established and the handler is notified that a new TCP
	 * connection has been established and that communication needs to be established. Keeps accepting connections
	 * until the max amount of peers has been reached.
	 */
	public void startServer() throws IOException, Exception {
		log.info("Begin startServer()");
		//int portNumber = dataManager.getPortNum(Constants.PEER_ID); // new method from dm if you want to use it, doesn't matter
		acceptor = new ServerSocket(dataManager.getPortNum(Constants.PEER_ID)); //Need to get port number of Constants.PEER_ID from datamanager and put into ServerSocket
		//try {
			while (Constants.NUMBER_OF_ACTIVE_PEERS < Constants.NUMBER_OF_PEERS) {
				Socket socket = acceptor.accept();
				Session session = new Session(socket, handler);
				session.start();
				handler.connectSession(session);
				Constants.NUMBER_OF_ACTIVE_PEERS++;
			}
		//Constants.NUMBER_OF_ACTIVE_PEERS++;
		/*}
		finally {

		}*/

		acceptor.close();
	}
	
	public void close() throws IOException {
		try {
			acceptor.close();
		}
		catch (IOException e){
			e.printStackTrace();
		}
	}
}
