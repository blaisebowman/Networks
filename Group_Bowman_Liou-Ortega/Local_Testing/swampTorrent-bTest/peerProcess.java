import common.Constants;
import java.io.IOException;
import peerprocess.PeerNetwork;
import peerprocess.Scheduler;

public class peerProcess {
    public static void main(String[] args) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        if (args.length > 0)
            Constants.PEER_ID = Integer.parseInt(args[0]);
        System.setProperty("peerId", String.valueOf(Constants.PEER_ID));
        Constants.readCfg();
        PeerNetwork connection = PeerNetwork.getInstance();
        connection.run();
    }
}
