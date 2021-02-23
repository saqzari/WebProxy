import java.io.*;
import java.net.*;
import java.util.*;

public class RequestRespond 
{
	
	private BufferedReader requestReader;
	private Socket socket;
	
	public RequestRespond(Socket socket)
	{
		this.socket = socket;
		try 
		{
			InputStreamReader in = new InputStreamReader(socket.getInputStream());
			requestReader = new BufferedReader(in);
			
		} catch (IOException e) 
		{
			System.out.println("Error");
		}
		
	}
	
	public void use()
	{
		String str = null;
		try {
			str = requestReader.readLine();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String[] parts = str.split(" ");
		String command = parts[0];
		String url = parts[1];
		
		if(command.equals("GET"))
		{
			System.out.println("hello get");
			System.out.println(url);
		}
		else if(command.equals("CONNECT"))
		{
			System.out.println("hello connect");
			System.out.println(url);
		}
	}
	
	

}
