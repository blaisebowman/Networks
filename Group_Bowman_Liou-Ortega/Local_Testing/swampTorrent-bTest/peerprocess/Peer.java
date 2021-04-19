package peerprocess;

public class Peer {

	
	public int port;
	public String host;
   	public Peer(String host, int port){
   		this.host = host;
   		this.port = port;
   	}
   	
   	public String getHost() {
   		return host;
   	}
   	
   	public int getPort() {
   		return port;
   	}
   	
   	public void setHost(String host) {
   		this.host = host;
   	}
   	
   	public void setPort(int port) {
   		this.port = port;
   	}
}
