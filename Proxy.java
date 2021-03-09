import java.io.*;
import java.net.*;
import java.util.*;

public class Proxy implements Runnable  
{
	// Constants for user input
	private static final String BLOCKED = "BLOCKED";
	private static final String CACHED = "CACHED";
	private static final String CLOSE = "CLOSE";
	private static final String HELP = "HELP";
	
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
	
	/* Checks if inputed url is from the blocked site list */
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
	
	/* Gets url/page from cache */
	public static File getCachedPage(String url) 
	{
		return cachedFiles.get(url);
	}

	/* Adds url/page to cache */
	public static void addCachedPage(String url, File file) 
	{
		cachedFiles.put(url, file);
	}
	
	/* Initializes hashmaps and lists
	 * Sets up browser socket using inputed port number */
	public Proxy(int portNumber) 
	{
		browserPortNumber = portNumber;
		cachedFiles = new HashMap<>();
		blockedSites = new HashMap<>();
		requestThreads = new ArrayList<>();
		
		Thread managementConsole = new Thread(this);
		managementConsole.start();
		
		try 
		{
			browserListener = new ServerSocket(browserPortNumber);
		} 
		catch (IOException e) 
		{
			System.out.println("Error trying to connect with port " + browserListener.getLocalPort());
		}
		
		System.out.println("Waiting for client on port " + browserListener.getLocalPort());
		running = true;
	}

	
	/* Listens for requests from the browser/client and makes a thread to deal with it */
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


	/* Runs the management console functionality */
	@Override
	public void run() 
	{
		Scanner consoleScanner = new Scanner(System.in);
		String userInput;
		while (running) 
		{
			System.out.println("Enter command. Enter HELP to view all commands");
			userInput = consoleScanner.nextLine().toUpperCase();

			switch (userInput) 
			{
				case CACHED:
					System.out.println("\nCurrently Cached Sites");
					for (String key : cachedFiles.keySet()) 
					{
						System.out.println(key);
					}
					System.out.println();
					break;
				case BLOCKED:
					System.out.println("\nCurrently Blocked Sites");
					for (String key : blockedSites.keySet()) 
					{
						System.out.println(key);
					}
					System.out.println();
					break;
				case CLOSE:
					running = false;
					System.out.println("\nProxy Closing");
					for (Thread thread : requestThreads) 
					{
						if (thread.isAlive()) 
						{
							try 
							{
								thread.join();
							} 
							catch (InterruptedException e) 
							{
								System.out.println("Error when closing thread");
							}
						}
					}
					break;
				case HELP:
					System.out.println("1.Input CACHED to view the list of caches webpages");
					System.out.println("2.Input BLOCKED to view the list of blocked URLs");
					System.out.println("3.Input CLOSE to close the entire proxy");
					System.out.println("4.Input HELP to see the list of possible commands");
					System.out.println("5.Otherwise, enter a URL to add it to the blocked list");
					break;
				default:
					blockedSites.put(userInput.toLowerCase(), userInput.toLowerCase());
					System.out.println("\n" + userInput + " has been blocked \n");
					break;
			}
		}
		consoleScanner.close();
	}

}
