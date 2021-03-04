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
	private Socket browserSocket;
	
	private static final String OK_200 = "HTTP/1.0 200 OK\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n";
	private static final String CONNECTION_200 = "HTTP/1.0 200 Connection established\r\n" + "Proxy-Agent: ProxyServer/1.0\r\n" + "\r\n";
	private static final String ERROR_404 = "HTTP/1.0 404 NOT FOUND\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n";
	
	public RequestRespond(Socket socket)
	{
		this.browserSocket = socket;
		try 
		{
			browserSocket.setSoTimeout(5000);
			proxyToClientReader = new BufferedReader(new InputStreamReader(browserSocket.getInputStream()));
			proxyToClientWriter = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
			
		} catch (IOException e) 
		{
			System.out.println("Error when initializing thread");
		}
		
	}
	
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
			e.printStackTrace();
		}
		
		System.out.println("Received Request: " + stringRead);
		String[] parts = stringRead.split(" ");
		String command = parts[0];
		String url = parts[1];
		
		
		if(command.equals("CONNECT"))
		{
			try 
			{
				parts = url.split(":");
				String address = parts[0];
				int serverPort = Integer.valueOf(parts[1]);
				
				for (int i = 0; i < 5; i++) 
				{
					proxyToClientReader.readLine();
				}
				
				InetAddress serverAddress;
				serverAddress = InetAddress.getByName(address);
				Socket serverSocket = new Socket(serverAddress, serverPort);
				serverSocket.setSoTimeout(2000);
				proxyToClientWriter.write(CONNECTION_200);
				proxyToClientWriter.flush();
				
				proxyToServerReader = new BufferedReader(new InputStreamReader(browserSocket.getInputStream()));
				proxyToServerWriter = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
				
				transmission(browserSocket, serverSocket);
				closeReadersWriters();
			} 
			catch (IOException e) 
			{
				System.out.println("Error processing Request " + stringRead);
			}

		}
		
		else
		{
			if (!url.substring(0, 4).equals("http")) 
			{
				url = "http://" + url;
			}
			
			File file;
			if((file = Proxy.getCachedPage(url)) != null)
			{
				httpRequestCached(url,file);
			}
			
			else 
			{
				httpRequestNotCached(url);
		    }
	   }
		
	}
	
	public void httpRequestCached(String url, File file)
	{
		System.out.println("Cached Copy found for : " + url);
		
		try 
		{
			String fileExtension = file.getName().substring(file.getName().lastIndexOf('.'));
			if (checkIfImage(fileExtension)) 
			{
				BufferedImage image = ImageIO.read(file);
				
				if (image == null) 
				{
					System.out.println("Image " + file.getName() + " was null");
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
				String line;
				
				while ((line = cachedFileBufferedReader.readLine()) != null) 
				{
					proxyToClientWriter.write(line);
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
	
	public void httpRequestNotCached(String url)
	{
		try
		{
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

				// Create Buffered output stream to write to cached copy of file
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
				
				if(image != null) 
				{
					// Cache image
					ImageIO.write(image, fileExtension.substring(1), fileToCache);

					// OK response
					proxyToClientWriter.write(OK_200);
					proxyToClientWriter.flush();

					// Send image to browser
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
			
			else if (fileExtension.equals(".ico"))
			{
				System.out.println("Can't read .ico file in java");
			}
			
			// else is a text file
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
				
				// Read from input stream between proxy and remote server
				while((line = proxyToServerReader.readLine()) != null)
				{
					// Send on data to client
					proxyToClientWriter.write(line);

					// Write to our cached copy of the file
					if(caching)
					{
						fileToCacheWriter.write(line);
					}
				}
				
				// Ensure all data is sent by this point
				proxyToClientWriter.flush();

				// Close Down Resources
				if(proxyToServerReader != null)
				{
					proxyToServerReader.close();
				}
				
			}
			
			if(caching)
			{
				// Ensure data written and add to our cached hash maps
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
	
	public boolean checkIfImage(String extension)
	{
		if((extension.contains(".png")) || extension.contains(".jpg") || extension.contains(".jpeg") || extension.contains(".gif"))
		{
			return true;
		}
		
		return false;	
	}
	
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

}


