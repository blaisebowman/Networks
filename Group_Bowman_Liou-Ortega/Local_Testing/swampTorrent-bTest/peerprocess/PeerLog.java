package peerprocess;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class PeerLog {
	
	private static Logger LOGGER;
	
	private static PeerLog PLOG;
	
	public PeerLog() {
		
	}
	
	private PeerLog(Logger log) {
		LOGGER = log;
	}
	
	public void configure(int peerId) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		FileHandler handler = new FileHandler("log_peer_" + peerId + ".log", false);
		Formatter formatter = new Formatter();
		handler.setFormatter(formatter);
		handler.setLevel(Level.INFO);
		PeerLog.LOGGER.addHandler(handler);
	}
	
	public static PeerLog getLogger(int peerId) throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		if (PLOG == null) {
			PLOG = new PeerLog(Logger.getLogger("CNT4007C"));
			PLOG.configure(peerId);
		}
		return PLOG;
	}
	
	public synchronized void config(String msg) {
		LOGGER.log(Level.CONFIG, msg);
	}
	
	public synchronized void debug(String msg) {
		LOGGER.log(Level.FINE, msg);
	}
	
	public synchronized void info(String msg) {
		LOGGER.log(Level.INFO, msg);
	}
	
	public synchronized void severe(String msg) {
		LOGGER.log(Level.SEVERE, msg);
	}
	
	public synchronized void warning(String msg) {
		LOGGER.log(Level.WARNING, msg);
	}
	
	private static String stackTraceToString(Throwable t) {
		final Writer sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}
	
	public synchronized void severe(Throwable t) {
		LOGGER.log(Level.SEVERE, stackTraceToString(t));
	}
	
	public synchronized void warning(Throwable t) {
		LOGGER.log(Level.WARNING, stackTraceToString(t));
	}
	

}

class Formatter extends SimpleFormatter {
	
	public Formatter() {
		super();
	}
	
	public String getCurrentTimeStamp() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
	}
	
	public String format(LogRecord record) {
		if (record.getLevel() == Level.INFO) {
			String timeStamp = getCurrentTimeStamp();
			return timeStamp + ": " + record.getMessage() + "\r\n";
		} else {
			return super.format(record);
		}
	}
	
}