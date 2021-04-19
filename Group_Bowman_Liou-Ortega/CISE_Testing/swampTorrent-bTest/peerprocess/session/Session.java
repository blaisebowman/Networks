package peerprocess.session;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;
import peerprocess.PeerLog;
import common.Constants;
import peerprocess.Handler;

public class Session extends Thread {

		private Socket connection;
		private Handler handler;
		private ObjectInputStream in; // Read from the socket
		private ObjectOutputStream out; // Write to the socket
		private PeerLog log;
		
		public Session(Socket connection, Handler handler) throws IOException, ClassNotFoundException,
				InstantiationException, IllegalAccessException {
			log = PeerLog.getLogger(Constants.PEER_ID);
			log.info("Session instanced for " + Constants.PEER_ID);				
			this.connection = connection;
			this.handler = handler;
			
			out = new ObjectOutputStream(connection.getOutputStream());
			out.flush();
		}
		
		@Override
		public void run() {
			try {
				try {
					in = new ObjectInputStream(connection.getInputStream());
					while (true) {
					Object msg = in.readObject();
					handler.readMessage(this, msg);
					}
				} catch (EOFException ex) {
					
				} catch (SocketException ex) {
					
				}
			} catch (IOException ex) {
				log.severe(ex);
			} catch (Exception ex) {
				log.severe(ex);
			} finally {
				try {
					close();
				} catch (IOException ex) {
					log.severe(ex);
				}
			}
		}
		
		public synchronized void write(byte[] msg) {
			try {
				out.writeObject(msg);
				out.flush();
			} catch (IOException ex) {
				log.severe(ex);
			}
		}
		
		public SocketAddress getRemoteAddress() {
			return connection.getRemoteSocketAddress();
		}
		
		public void close() throws IOException {
			in.close();
			out.close();
			connection.close();
		}
}
