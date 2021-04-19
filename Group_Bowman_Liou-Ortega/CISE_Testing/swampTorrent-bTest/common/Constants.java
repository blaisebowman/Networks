package common;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import peerprocess.PeerLog;


public class Constants {
    public enum MessageType{CHOKE, UNCHOKE, INTERESTED, NOTINTERESTED, HAVE, BITFIELD, REQUEST, PIECE, UNIDENTIFIED};
    
    public static String COMMON_FILE = "Common.cfg";
    public static String PEER_INFO_FILE = "PeerInfo.cfg";
    
    public static int PEER_ID = 1001;
    public static int NUMBER_OF_PREFERRED_NEIGHBORS = 2;
    public static int UNCHOKING_INTERVAL = 6;
    public static int OPTIMISTIC_UNCHOKING_INTERVAL = 9;
    public static String FILE_NAME = "TheFile.dat";
    public static int FILE_SIZE = 277752;
    public static int PIECE_SIZE = 32768;
    public static int NUMBER_OF_FIELDS = 9;
    public static int NUMBER_OF_PEERS = 0;
    public static int NUMBER_OF_ACTIVE_PEERS = 0;
    
    public static int CHECKING_STATUS_OF_ALL_PEERS = 5;
    private static PeerLog log;

    /*
     * readCfg(): Read Common file for metadata statistics
     */
    public static void readCfg() throws FileNotFoundException, IOException, ClassNotFoundException,
            InstantiationException, IllegalAccessException {
        log = PeerLog.getLogger(Constants.PEER_ID);
        log.info("Common.cfg reading:");
        BufferedReader buff = new BufferedReader(new FileReader(COMMON_FILE));
        String s = null;
        while ((s = buff.readLine()) != null){
            String tmp[] = s.split(" ");
            String value  = tmp[1].trim();
            String variable = tmp[0].trim();
            if (variable.equals("NumberOfPreferredNeighbors")){
                NUMBER_OF_PREFERRED_NEIGHBORS = Integer.parseInt(value);
                log.info("NUMBER_OF_PREFERRED_NEIGHBORS = " + NUMBER_OF_PREFERRED_NEIGHBORS);
            } else  if (variable.equals("UnchokingInterval")) {
                UNCHOKING_INTERVAL = Integer.parseInt(value);
                log.info("UNCHOKING_INTERVAL = " + UNCHOKING_INTERVAL);
            } else if (variable.equals("OptimisticUnchokingInterval")){
                OPTIMISTIC_UNCHOKING_INTERVAL = Integer.parseInt(value);
                log.info("OptimisticUnchokingInterval = " + OPTIMISTIC_UNCHOKING_INTERVAL);
            } else if (variable.equals("FileName")){
                FILE_NAME = value;
                log.info("FileName = " + FILE_NAME);
            } else if (variable.equals("FileSize")){
                FILE_SIZE = Integer.parseInt(value);
                log.info("FileSize = " + FILE_SIZE);
            } else if (variable.equals("PieceSize")) {
                PIECE_SIZE = Integer.parseInt(value);
                log.info("PieceSize = " + PIECE_SIZE);
            }                    
        }
        
        NUMBER_OF_FIELDS = (int)Math.ceil(((double)FILE_SIZE)/PIECE_SIZE);
        log.info("NumberOfFields = " + NUMBER_OF_FIELDS);
        
        buff.close();
    }
    
    /* convertMsgTypeToByte(MessageType type):
    *  Convert from message type to byte type representation. 
    */
    public static byte convertMsgTypeToByte(MessageType type){
        switch(type){
            case CHOKE:
                return 0;
            case UNCHOKE:
                return 1;
            case INTERESTED:
                return 2;
            case NOTINTERESTED:
                return 3;
            case HAVE:
                return 4;
            case BITFIELD:
                return 5;
            case REQUEST:
                return 6;
            case PIECE:
                return 7;
            default:
                    break;
        }
        return -1;
    }
    
    /* convertByteToMsgType(byte type):
    *  Converts from byte type to message type representation.
    */
    public static MessageType convertByteToMsgType(byte type){
        switch(type){
            case 0:
                return MessageType.CHOKE;
            case 1:
                return MessageType.UNCHOKE;
            case 2:
                return MessageType.INTERESTED;
            case 3:
                return MessageType.NOTINTERESTED;
            case 4:
                return MessageType.HAVE;
            case 5:
                return MessageType.BITFIELD;
            case 6:
                return MessageType.REQUEST;
            case 7:
                return MessageType.PIECE;
            default:
                break;
        }
        return MessageType.UNIDENTIFIED;
    }
}
