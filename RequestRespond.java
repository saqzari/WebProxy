import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;

public class RequestRespond implements Runnable 
{
	
	private BufferedReader proxyToClientReader;
	private BufferedWriter proxyToClientWriter;
	private BufferedReader proxyToServerReader;
	private BufferedWriter proxyToServerWriter;
	private PrintWriter out;
	private Socket browserSocket;
	
	/* Http browser messages */
	private static final String OK_200 = "HTTP/1.0 200 OK\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n";
	private static final String CONNECTION_200 = "HTTP/1.0 200 Connection established\r\n" + "Proxy-Agent: ProxyServer/1.0\r\n" + "\r\n";
	private static final String ERROR_404 = "HTTP/1.0 404 NOT FOUND\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n";
	private static final String FORBIDDEN_404 = "HTTP/1.0 403 Access Forbidden \n" + "User-Agent: ProxyServer/1.0\n" + "\r\n";
	
	/* Creates object RequestResponder with connection to the browser socket */
	public RequestRespond(Socket socket)
	{
		this.browserSocket = socket;
		try 
		{
			browserSocket.setSoTimeout(5000);
			proxyToClientReader = new BufferedReader(new InputStreamReader(browserSocket.getInputStream()));
			proxyToClientWriter = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
			out = new PrintWriter(browserSocket.getOutputStream());
			
		} catch (IOException e) 
		{
			System.out.println("Error when initializing thread");
		}
		
	}
	
	/* Checks what type of request has been obtained, and deals with it using other functions */
	@Override
	public void run() 
	{
		String stringRead = null;
		try 
		{
			stringRead = proxyToClientReader.readLine();
		} 
		catch (IOException e) 
		{
			System.out.println("Error reading from browser");
		}
		
		System.out.println("\nReceived Request: " + stringRead);
		String[] parts = stringRead.split(" ");
		String command = parts[0];
		String url = parts[1];
		
		/* If https CONNECT request detected */
		if(command.equals("CONNECT"))
		{
			try 
			{
				/* separate port and address */
				parts = url.split(":");
				String address = parts[0];
				int serverPort = Integer.valueOf(parts[1]);
				
				if(Proxy.isBlockedSite(address))
				{
					System.out.println("A blocked site was requested: " + address);
					dealWithBlockedSite();
					return;
				}
				
				/* Empty buffer reader if not empty */
				for (int i = 0; i < 5; i++) 
				{
					proxyToClientReader.readLine();
				}
				
				
				/* Create socket to server and send message to browser  */
				InetAddress serverAddress;
				serverAddress = InetAddress.getByName(address);
				Socket serverSocket = new Socket(serverAddress, serverPort);
				serverSocket.setSoTimeout(2000);
				proxyToClientWriter.write(CONNECTION_200);
				proxyToClientWriter.flush();
				
				proxyToServerReader = new BufferedReader(new InputStreamReader(browserSocket.getInputStream()));
				proxyToServerWriter = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
				
				/* Time handling of https request  */
				long startTime = System.nanoTime();
				transmission(browserSocket, serverSocket);
				long endTime = System.nanoTime();
				long duration = (endTime - startTime);
				System.out.println("Process time for " + address + ": " + duration + " nanoseconds");
				closeReadersWriters();
			} 
			catch (IOException e) 
			{
				System.out.println("Error processing Request " + stringRead);
			}

		}
		
		/* else http request detected */
		else
		{
			if (!url.substring(0, 4).equals("http")) 
			{
				url = "http://" + url;
			}
			
			File file;
			
			/* if website has already been cached */
			if((file = Proxy.getCachedPage(url)) != null)
			{
				long startTime = System.nanoTime();
				httpRequestCached(url,file);
				long endTime = System.nanoTime();
				long duration = (endTime - startTime);
				System.out.println("Process time for " + url + ": " + duration + " nanoseconds");
			}
			
			/* else dealing with non cached request */
			else 
			{
				long startTime = System.nanoTime();
				httpRequestNotCached(url);
				long endTime = System.nanoTime();
				long duration = (endTime - startTime);
				System.out.println("Process time for " + url + ": " + duration + " nanoseconds");
		    }
	   }
		
	}
	
	/* Method dealing with request if already been cached */
	public void httpRequestCached(String url, File file)
	{
		if(Proxy.isBlockedSite(url))
		{
			System.out.println("A blocked site was requested: " + url);
			dealWithBlockedSite();
			return;
		}
		
		System.out.println("Cached Copy found for : " + url);
		
		try 
		{
			String fileExtension = file.getName().substring(file.getName().lastIndexOf('.'));
			if (checkIfImage(fileExtension)) 
			{
				BufferedImage image = ImageIO.read(file);
				
				if (image == null) 
				{
					System.out.println("Image " + file.getName() + " was a null image");
					proxyToClientWriter.write(ERROR_404);
					proxyToClientWriter.flush();
				} 
				
				else 
				{
					proxyToClientWriter.write(OK_200);
					proxyToClientWriter.flush();
					ImageIO.write(image, fileExtension.substring(1), browserSocket.getOutputStream());
				}
			}
			
			else 
			{
				BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
				proxyToClientWriter.write(OK_200);
				proxyToClientWriter.flush();
				String cacheLine;
				
				while ((cacheLine = cachedFileBufferedReader.readLine()) != null) 
				{
					proxyToClientWriter.write(cacheLine);
				}
				
				proxyToClientWriter.flush();

				if (cachedFileBufferedReader != null) 
				{
					cachedFileBufferedReader.close();
				}
			}
			
			closeReadersWriters();
			
		} 
		
		catch (IOException e) 
		{
			System.out.println("Error sending cached file to client");
		}
	}
	
	/* Method dealing with request if not been cached yet */
	public void httpRequestNotCached(String url)
	{
		try
		{
			if(Proxy.isBlockedSite(url))
			{
				System.out.println("A blocked site was requested: " + url);
				dealWithBlockedSite();
				return;
			}
			
			System.out.println("No Cached Copy found for : " + url);
			
			String [] fileParts = computeFile(url);
			String fileName  = fileParts[0];
			String fileExtension = fileParts[1];
			
			boolean caching = true;
			File fileToCache = null;
			BufferedWriter fileToCacheWriter = null;
			
			try
			{ 
				fileToCache = new File(fileName);

				if(!fileToCache.exists())
				{
					fileToCache.createNewFile();
				}

				fileToCacheWriter = new BufferedWriter(new FileWriter(fileToCache));
			}
			catch (IOException e)
			{
				System.out.println("Couldn't cache: " + fileName);
				caching = false;
				e.printStackTrace();
			} 
			catch (NullPointerException e) 
			{
				System.out.println("Null pointer exception trying to open file");
			}

			if(checkIfImage(fileExtension))
			{
				BufferedImage image = null;
				URL remoteURL = new URL(url);
				HttpURLConnection proxyToServerConnection = (HttpURLConnection)remoteURL.openConnection();
				proxyToServerConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
				proxyToServerConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				proxyToServerConnection.setRequestProperty("Content-Language", "en-US");  
				proxyToServerConnection.setUseCaches(false);
				proxyToServerConnection.setDoOutput(true);
				
				try
				{
					image = ImageIO.read(proxyToServerConnection.getInputStream());
				}
				catch (IOException e) 
				{
					System.out.println("Sending 404 to client as image wasn't received from server: " + fileName);
					proxyToClientWriter.write(ERROR_404);
					proxyToClientWriter.flush();
					closeReadersWriters();
					return;
				}
				
				/* Write image to cache, send OK response and then send image to browser */
				
				if(image != null) 
				{
					ImageIO.write(image, fileExtension.substring(1), fileToCache);

					proxyToClientWriter.write(OK_200);
					proxyToClientWriter.flush();

					ImageIO.write(image, fileExtension.substring(1), browserSocket.getOutputStream());
				} 
				else 
				{
					System.out.println("Sending 404 to client as image wasn't received from server" + fileName);
					proxyToClientWriter.write(ERROR_404);
					proxyToClientWriter.flush();
					return;
				}
				
			}
			
			// .ico images can't be read or write in java by normal means
			else if (fileExtension.equals(".ico"))
			{
				System.out.println("Can't read and write .ico image in java. May not appear in browser");
			}
			
			// When the file is most likely a text file
			else 
			{			
				URL remoteURL = new URL(url);
				
				// Create a connection to remote server
				HttpURLConnection proxyToServerConnection = (HttpURLConnection)remoteURL.openConnection();
				proxyToServerConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
				proxyToServerConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				proxyToServerConnection.setRequestProperty("Content-Language", "en-US");  
				proxyToServerConnection.setUseCaches(false);
				proxyToServerConnection.setDoOutput(true);
				
				proxyToServerReader = new BufferedReader(new InputStreamReader(proxyToServerConnection.getInputStream()));
				proxyToClientWriter.write(OK_200);
				
				String line;
				
				/* Read from input stream between proxy and remote server, write 
				   data to client and write to cache if caching */
				
				while((line = proxyToServerReader.readLine()) != null)
				{
					proxyToClientWriter.write(line);

					if(caching)
					{
						fileToCacheWriter.write(line);
					}
				}
				
				proxyToClientWriter.flush();

				closeReadersWriters();
			}
			
			if(caching)
			{
				fileToCacheWriter.flush();
				Proxy.addCachedPage(url, fileToCache);
			}

			closeReadersWriters();
			
		} 
		catch (Exception e)
		{
			System.out.println("Error handling non cached Http request for " + url);
		}
	
	}
	
	/* Takes url from request and returns array with the file name and the extension of the file
	 * from the given url making it the correct format */
	public String[] computeFile(String url)
	{
		int fileExtensionIndex = url.lastIndexOf(".");

		// Get type of file
		String fileType = url.substring(fileExtensionIndex, url.length());

		// Get file name
		String fileName = url.substring(0,fileExtensionIndex);

		// Get rid of http://www. 
		fileName = fileName.substring(fileName.indexOf('.') + 1);

		// Remove any illegal characters 
		fileName = fileName.replace("/", "__");
		fileName = fileName.replace('.','_');
		
		if(fileType.contains("/"))
		{
			fileType = fileType.replace("/", "__");
			fileType = fileType.replace('.','_');
			fileType += ".html";
		}
	
		fileName = fileName + fileType;
		
		String [] fileParts = new String[2];
		fileParts[0] = fileName;
		fileParts[1] = fileType;
		return fileParts;
		
	}
	
	/* Checks if string is an image file extension */
	public boolean checkIfImage(String extension)
	{
		if((extension.contains(".png")) || extension.contains(".jpg") || extension.contains(".jpeg") || extension.contains(".gif"))
		{
			return true;
		}
		
		return false;	
	}
	
	/* Closes all Buffer Readers and Writers */
	public void closeReadersWriters() throws IOException
	{
		if(proxyToClientWriter != null)
		{
			proxyToClientWriter.close();
		}
		if(proxyToClientReader != null)
		{
			proxyToClientReader.close();
		}
		if(proxyToServerReader != null)
		{
			proxyToClientWriter.close();
		}
		if(proxyToServerWriter != null)
		{
			proxyToClientWriter.close();
		}
	}
	
	/* Handles Https CONNECT connections. Two threads are made between client and server so both
	 * can send between them*/
	public void transmission(Socket browserSocket, Socket serverSocket) throws IOException
	{
		final Thread[] threads = new Thread[2];
		
		HttpsTransmission clientToServerHttps = new HttpsTransmission(browserSocket.getInputStream(), serverSocket.getOutputStream());
		HttpsTransmission serverToClientHttps = new HttpsTransmission(serverSocket.getInputStream(), browserSocket.getOutputStream());
		
		threads[0] = new Thread(clientToServerHttps);
		threads[1] = new Thread(serverToClientHttps);
		
		threads[0].start();
		threads[1].start();
		
		try { threads[0].join(); threads[1].join(); } catch (InterruptedException e) {}
	}
	
	/* Deals with a request from a previously blocked site including writing messages on browser (may only work if http request) */
	private void dealWithBlockedSite()
	{
		try 
		{
			out.println("<p> Website blocked </p>");
			out.flush();
			out.close();
			closeReadersWriters();
		} 
		catch (IOException e) 
		{
			System.out.println("Error writing to client when requested a blocked site");
			e.printStackTrace();
		}
	}

}


