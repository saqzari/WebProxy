import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

public class HttpsTransmission implements Runnable
{
	private InputStream inputStream;
	private OutputStream outputStream;

	public HttpsTransmission(InputStream proxyToClientIS, OutputStream proxyToServerOS) {
		this.inputStream = proxyToClientIS;
		this.outputStream = proxyToServerOS;
	}

	@Override
	public void run() {
		try {
			byte[] buffer = new byte[4096];
			int read;
			do {
				read = inputStream.read(buffer);
				if (read > 0) {
					outputStream.write(buffer, 0, read);
					if (inputStream.available() < 1) {
						outputStream.flush();
					}
				}
			} while (read >= 0);
		} catch (SocketTimeoutException e) {
		} catch (IOException e) {
			System.out.println("HTTPS read timed out");
		}
	}
}
