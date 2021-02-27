import java.io.*;
import java.net.*;
import java.util.*;

public class RequestRespond implements Runnable 
{
	
	private BufferedReader proxyToClientReader;
	private BufferedWriter proxyToClientWriter;
	private BufferedReader proxyToServerReader;
	private BufferedWriter proxyToServerWriter;
	private Socket browserSocket;
	
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
			
			String line = "HTTP/1.0 200 Connection established\r\n" +
					"Proxy-Agent: ProxyServer/1.0\r\n" +
					"\r\n";
			proxyToClientWriter.write(line);
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


