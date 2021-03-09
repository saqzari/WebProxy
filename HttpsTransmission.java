import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

/* A class that can be used to write everything from an input stream to an output stream. Used for https requests */
public class HttpsTransmission implements Runnable
{
	private InputStream inputStream;
	private OutputStream outputStream;

	public HttpsTransmission(InputStream proxyToClientInputStream, OutputStream proxyToServerOutputStream) 
	{
		this.inputStream = proxyToClientInputStream;
		this.outputStream = proxyToServerOutputStream;
	}

	@Override
	public void run() 
	{
		try 
		{
			byte[] buffer = new byte[4096];
			int data;
			do 
			{
				data = inputStream.read(buffer);
				if (data > 0) 
				{
					outputStream.write(buffer, 0, data);
					if (inputStream.available() < 1) 
					{
						outputStream.flush();
					}
				}
				
			} while (data >= 0);
		} 
		catch (SocketTimeoutException e) {}
		catch (IOException e) 
		{
			System.out.println("HTTPS read timed out");
		}
	}
}
