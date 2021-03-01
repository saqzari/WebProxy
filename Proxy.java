import java.io.*;
import java.net.*;
import java.util.*;

public class Proxy implements Runnable  
{
	
	private static ArrayList<Thread> requestThreads;
	private static HashMap<String, File> cachedFiles;

	public static void main(String[] args) throws IOException 
	{
		Proxy proxy = new Proxy(8080);
		proxy.listen();
	}
	
	private int browserPort;
	private ServerSocket browserListener;
	private volatile boolean running = true;
	
	public static File getCachedPage(String url) 
	{
		return cachedFiles.get(url);
	}

	public static void addCachedPage(String url, File file) 
	{
		cachedFiles.put(url, file);
	}
	
	public Proxy(int port) 
	{
		cachedFiles = new HashMap<>();
		requestThreads = new ArrayList<>();
		browserPort = port;
		try {
			browserListener = new ServerSocket(browserPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Waiting for client on port " + browserListener.getLocalPort());
		running = true;
		new Thread(this).start();
	}

	
	public void listen() throws IOException {
		while (running)
		{
			Socket socket = browserListener.accept();
			System.out.println("browser connected");
			RequestRespond response = new RequestRespond(socket);
			Thread thread = new Thread(response);
			requestThreads.add(thread);
			thread.start();	
		}
	}


	@Override
	public void run() 
	{
		// TODO Auto-generated method stub
		
	}


	

}
