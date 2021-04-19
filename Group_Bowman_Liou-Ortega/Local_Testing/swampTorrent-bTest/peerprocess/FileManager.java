package peerprocess;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import common.Constants;

/*This program should handle the actual file data being passed
 *between peers. If the current peer has the file, then it should split
 *the file into the piece sizes declared in the Common.cfg and write them out.
 */

public class FileManager {

	int numPrefPeers = Constants.NUMBER_OF_PREFERRED_NEIGHBORS;
	int unchoke = Constants.UNCHOKING_INTERVAL;
	int optimist = Constants.OPTIMISTIC_UNCHOKING_INTERVAL;
	String file = Constants.FILE_NAME;
	int fileSize = Constants.FILE_SIZE;
	int pieceSize = Constants.PIECE_SIZE;

	public String hostname;
	public int portNumberCurr;
	boolean isDone;

	private static FileManager instance;
	
	private String filePath;
	private PeerLog log;
	
	private boolean hasFile = false; // true if peer contains file at runtime
	
	public FileManager() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		filePath = "peer_" + Constants.PEER_ID + "/";
		File fileDir = new File("peer_" + Constants.PEER_ID);

		boolean directoryExists = fileDir.exists();
		if(!directoryExists){
			directoryExists = fileDir.mkdir();
		}
		log = PeerLog.getLogger(Constants.PEER_ID);
		log.info("FileManager instanced");
	}
	
	public static FileManager getInstance() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		if (instance == null) {
			instance = new FileManager();
		}
		return instance;
	}
	
	public byte[] getPieceBytes(int pieceIndexField) throws IOException {
		String filename = filePath + "file_part_" + Integer.toString(pieceIndexField) + ".dat";
		byte[] piece = Files.readAllBytes(Paths.get(filename));
		return piece;
	}
	
	public void storePieceBytes(int pieceIndexField, byte[] bytes) throws FileNotFoundException, IOException {
		String filename = filePath + "file_part_" + Integer.toString(pieceIndexField) + ".dat";
		FileOutputStream fos = new FileOutputStream(filename);
		fos.write(bytes);
		fos.close();
	}
	
	// Called when the Peer detects it has the complete file at program start.
	public void peerHasFile(boolean hasFile) throws IOException {
		// File is 11 bytes
		// Piece Size is 2 bytes
		// Number_of_fields is 6
		// bb = [10101010101]
		// bytes0 = [10], bytes1 = [10], bytes2 = [10], bytes3 = [10], bytes4 = [10], bytes5 = [1]
		this.hasFile = hasFile;
		if (hasFile) {
			// Read file into Bytes
			byte[] bb = Files.readAllBytes(Paths.get(filePath + Constants.FILE_NAME));
			// Split file into pieces of size PIECE_SIZE
			for (int i = 0; i < Constants.NUMBER_OF_FIELDS; i++) {
				//Size of last piece may be small than the Constants.PIECE_SIZE
				int pieceSize = Constants.PIECE_SIZE;
				if (i == (Constants.NUMBER_OF_FIELDS - 1)) {
					pieceSize = Constants.FILE_SIZE - i * Constants.PIECE_SIZE;
				}

				
				// Fill in bytes from source bb byte array.
				byte[] bytes = new byte[pieceSize];
				for (int j = 0; j < pieceSize; j++) {
					bytes[j] = bb[i * Constants.PIECE_SIZE + j];
				}
				
				String file_name = filePath + "file_part_" + Integer.toString(i) + ".dat";
				FileOutputStream fos = new FileOutputStream(file_name);
				fos.write(bytes);
				fos.close();
			}
		}
	}
	
	// Called when this peer receives all pieces. Needs to sum up all pieces into one Final File
	public void finish() throws IOException {
		log.info("Initiating termination of File Manager");
		if (!hasFile) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			for (int i = 0; i < Constants.NUMBER_OF_FIELDS; i++) {
				String file_name = filePath + "file_part_" + Integer.toString(i) + ".dat";
				byte[] current_byte = Files.readAllBytes(Paths.get(file_name));
				bos.write(current_byte);
			}
			byte[] final_file = bos.toByteArray();
			FileOutputStream fos = new FileOutputStream(filePath + Constants.FILE_NAME);
			fos.write(final_file);
			fos.close();
		}
		
		// delete all the pieces
		for (int i = 0; i < Constants.NUMBER_OF_FIELDS; i++) {
			File file = new File(filePath + "file_part_" + Integer.toString(i) + ".dat");
			file.delete();
		}
	}
}

