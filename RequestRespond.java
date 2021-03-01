import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;

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
			browserSocket.setSoTimeout(2000);
			proxyToClientReader = new BufferedReader(new InputStreamReader(browserSocket.getInputStream()));
			proxyToClientWriter = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
			
		} catch (IOException e) 
		{
			System.out.println("Error");
		}
		
	}
	
	@Override
	public void run() 
	{
		String str = null;
		try {
			str = proxyToClientReader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(str);
		String[] parts = str.split(" ");
		String command = parts[0];
		String url = parts[1];

		
		if(command.equals("CONNECT"))
		{
			try {
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
			proxyToClientReader.close();
			proxyToClientWriter.close();
			proxyToServerReader.close();
			proxyToServerWriter.close();
			
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		
		else
		{
			
			if (!url.substring(0, 4).equals("http")) {
				url = "http://" + url;
			}
			
			File file;
			if((file = Proxy.getCachedPage(url)) != null)
			{
				System.out.println("Cached Copy found for : " + url + "\n");
				try {
					String fileExtension = file.getName().substring(file.getName().lastIndexOf('.'));
					if (checkIfImage(fileExtension)) {
						BufferedImage image = ImageIO.read(file);
						if (image == null) {
							System.out.println("Image " + file.getName() + " was null");
							proxyToClientWriter.write(ERROR_404);
							proxyToClientWriter.flush();
						} else {
							proxyToClientWriter.write(OK_200);
							proxyToClientWriter.flush();
							ImageIO.write(image, fileExtension.substring(1), browserSocket.getOutputStream());
						}
					} else {
						BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
						proxyToClientWriter.write(OK_200);
						proxyToClientWriter.flush();
						String line;
						while ((line = cachedFileBufferedReader.readLine()) != null) {
							proxyToClientWriter.write(line);
						}
						proxyToClientWriter.flush();

						if (cachedFileBufferedReader != null) {
							cachedFileBufferedReader.close();
						}
					}
					if (proxyToClientWriter != null) {
						proxyToClientWriter.close();
					}
				} catch (IOException e) {
					System.out.println("Error sending cached file to client");
				}
			}
			else 
			{
			
				try{
					
					String [] fileParts = computeFile(url);
					String fileName  = fileParts[0];
					String fileExtension = fileParts[1];
	
	
					// Attempt to create File to cache to
					boolean caching = true;
					File fileToCache = null;
					BufferedWriter fileToCacheBW = null;
					
					try{
						// Create File to cache 
						fileToCache = new File(fileName);
	
						if(!fileToCache.exists()){
							fileToCache.createNewFile();
						}
	
						// Create Buffered output stream to write to cached copy of file
						fileToCacheBW = new BufferedWriter(new FileWriter(fileToCache));
					}
					catch (IOException e){
						System.out.println("Couldn't cache: " + fileName);
						caching = false;
						e.printStackTrace();
					} catch (NullPointerException e) {
						System.out.println("NPE opening file");
					}
	
					// Check if file is an image
					if(checkIfImage(fileExtension)){
						// Create the URL
						URL remoteURL = new URL(url);
						BufferedImage image = ImageIO.read(remoteURL);
	
						if(image != null) {
							// Cache the image to disk
							ImageIO.write(image, fileExtension.substring(1), fileToCache);
	
							// Send response code to client
							proxyToClientWriter.write(OK_200);
							proxyToClientWriter.flush();
	
							// Send them the image data
							ImageIO.write(image, fileExtension.substring(1), browserSocket.getOutputStream());
	
						// No image received from remote server
						} else {
							System.out.println("Sending 404 to client as image wasn't received from server" + fileName);
							proxyToClientWriter.write(ERROR_404);
							proxyToClientWriter.flush();
							return;
						}
					} 
	
					// File is a text file
					else {
										
						// Create the URL
						URL remoteURL = new URL(url);
						// Create a connection to remote server
						HttpURLConnection proxyToServerCon = (HttpURLConnection)remoteURL.openConnection();
						proxyToServerCon.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
						proxyToServerCon.setRequestProperty("Content-Language", "en-US");  
						proxyToServerCon.setUseCaches(false);
						proxyToServerCon.setDoOutput(true);
					
						// Create Buffered Reader from remote Server
						BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));
						
	
						// Send success code to client
						String line;
						proxyToClientWriter.write(OK_200);
						
						
						// Read from input stream between proxy and remote server
						while((line = proxyToServerBR.readLine()) != null){
							// Send on data to client
							proxyToClientWriter.write(line);
	
							// Write to our cached copy of the file
							if(caching){
								fileToCacheBW.write(line);
							}
						}
						
						// Ensure all data is sent by this point
						proxyToClientWriter.flush();
	
						// Close Down Resources
						if(proxyToServerBR != null){
							proxyToServerBR.close();
						}
					}
					
					if(caching){
						// Ensure data written and add to our cached hash maps
						fileToCacheBW.flush();
						Proxy.addCachedPage(url, fileToCache);
					}

					// Close down resources
					if(fileToCacheBW != null){
						fileToCacheBW.close();
					}

	
					if(proxyToClientWriter != null){
						proxyToClientWriter.close();
					}
				} 
	
				catch (Exception e){
					e.printStackTrace();
				}
			
		  }
	   }
	}
	
	public String[] computeFile(String url)
	{
		// Compute a logical file name as per schema
		// This allows the files on stored on disk to resemble that of the URL it was taken from
		int fileExtensionIndex = url.lastIndexOf(".");
		String fileExtension;

		// Get the type of file
		fileExtension = url.substring(fileExtensionIndex, url.length());

		// Get the initial file name
		String fileName = url.substring(0,fileExtensionIndex);


		// Trim off http://www. as no need for it in file name
		fileName = fileName.substring(fileName.indexOf('.')+1);

		// Remove any illegal characters from file name
		fileName = fileName.replace("/", "__");
		fileName = fileName.replace('.','_');
		
		// Trailing / result in index.html of that directory being fetched
		if(fileExtension.contains("/")){
			fileExtension = fileExtension.replace("/", "__");
			fileExtension = fileExtension.replace('.','_');
			fileExtension += ".html";
		}
	
		fileName = fileName + fileExtension;
		
		String [] fileParts = new String[2];
		fileParts[0] = fileName;
		fileParts[1] = fileExtension;
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


