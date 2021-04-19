package peerprocess;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

import common.Constants;

/* Scheduler:
 * This program should handle just the timer portion of events in the P2P sharing process,
 * namely the updates on where the current preferred neighbors are the 
 * fastest downloads, the optimisticUnchoking, and the fileStatus check.
*/

public class Scheduler {
	
	private Timer timer;
	
	private static Scheduler instance;
	
	private PeerLog Logger;
	
	public Scheduler() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		Logger = PeerLog.getLogger(Constants.PEER_ID);
		timer = new Timer();
	}
	
	public static Scheduler getInstance() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
		if (instance == null) {
			instance = new Scheduler();
		}
		return instance;
	}
	
	public void run() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		Logger.info("Beginning running scheduler");
		DataManager dm = DataManager.getInstance();		
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					dm.getPrefferedNeighbors();
				} catch (Exception ex) {
					Logger.severe(ex);
				}
			}
		}, 2000, Constants.UNCHOKING_INTERVAL * 1000);
		
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					dm.optimisticUnchokedNeighbor();
				} catch (Exception ex) {
					Logger.severe(ex);
				}
			}
		}, 2000, Constants.OPTIMISTIC_UNCHOKING_INTERVAL * 1000);
		
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					dm.checkStatusOfPeers();
				} catch (Exception ex) {
					Logger.severe(ex);
				}
			}
		}, 2000, Constants.CHECKING_STATUS_OF_ALL_PEERS * 1000);
	}

	public void stop (){
		Logger.info("Scheduler terminated");
		timer.cancel();
	}

}