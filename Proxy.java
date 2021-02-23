import java.io.*;
import java.net.*;
import java.util.*;

public class Proxy  
{

	public static void main(String[] args) throws IOException 
	{
		Proxy proxy = new Proxy();
		proxy.listen();
	}
	
	private int browserPort;
	private ServerSocket browserListener;
	private volatile boolean running = true;
	
	public Proxy() 
	{
		browserPort = 8080;
		try {
			browserListener = new ServerSocket(browserPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Waiting for client on port " + browserListener.getLocalPort());
		running = true;
	}

	
	public void listen() {
		while (running)
		{
			Socket socket = null;
			try {
				socket = browserListener.accept();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("browser connected");
			RequestRespond response = new RequestRespond(socket);
			response.use();
		}
	}

}
