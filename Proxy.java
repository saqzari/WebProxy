import java.io.*;
import java.net.*;
import java.util.*;

public class Proxy implements Runnable  
{
	
	private static ArrayList<Thread> requestThreads;
	private static HashMap<String, File> cachedFiles;
	private static HashMap<String, String> blockedSites;
	private int browserPortNumber;
	private ServerSocket browserListener;
	private volatile boolean running;

	public static void main(String[] args) throws IOException 
	{
		Proxy proxy = new Proxy(8080);
		proxy.listen();
	}
	
	public static boolean isBlockedSite(String url) 
	{
		for (String key : blockedSites.keySet()) 
		{
			if (url.contains(key)) 
			{
				return true;
			}
		}
		return false;
	}
	
	public static File getCachedPage(String url) 
	{
		return cachedFiles.get(url);
	}

	public static void addCachedPage(String url, File file) 
	{
		cachedFiles.put(url, file);
	}
	
	public Proxy(int portNumber) 
	{
		browserPortNumber = portNumber;
		cachedFiles = new HashMap<>();
		requestThreads = new ArrayList<>();
		try 
		{
			browserListener = new ServerSocket(browserPortNumber);
		} catch (IOException e) 
		{
			System.out.println("Error trying to connect with port " + browserListener.getLocalPort());
		}
		System.out.println("Waiting for client on port " + browserListener.getLocalPort());
		running = true;
		new Thread(this).start();
	}

	
	public void listen() throws IOException 
	{
		while (running)
		{
			Socket socket = browserListener.accept();
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
