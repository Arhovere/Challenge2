package protocol;

import java.util.Arrays;
import client.*;

public class MyDataTransferProtocol extends IRDTProtocol {

	// change the following as you wish:
	static final int HEADERSIZE = 1; // number of header bytes in each packet
	static final int DATASIZE = 8; // max. number of user data bytes in each packet
	int sequence;

	@Override
	public void sender() {
		System.out.println("Sending...");

		// read from the input file
		Integer[] fileContents = Utils.getFileContents(getFileID());

		if (fileContents.length % DATASIZE != 0) {
			sequence = fileContents.length / DATASIZE;
		} else {
			sequence = (fileContents.length / DATASIZE) - 1;
		}

		// keep track of where we are in the data
		int filePointer = 0;
		while (filePointer < fileContents.length) {

			// create a new packet of appropriate size
			int datalen = Math.min(DATASIZE, fileContents.length - filePointer);

			Integer[] pkt = new Integer[HEADERSIZE + datalen];
			// write something random into the header byte
			pkt[0] = sequence;
			sequence--;

			// copy databytes from the input file into data part of the packet, i.e., after the header
			System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);
			
			filePointer += datalen;

			// send the packet to the network layer
			getNetworkLayer().sendPacket(pkt);
			System.out.println("Sent packet with header=" + pkt[0]);

			// schedule a timer for 1000 ms into the future, just to show how that works:
			client.Utils.Timeout.SetTimeout(1000, this, 28);

			// and loop and sleep; you may use this loop to check for incoming acks...
			boolean stop = false;
			while (!stop) {
				try {
					Integer[] acknowledged = getNetworkLayer().receivePacket();
					if (acknowledged != null) {
						System.out.println("Received ack:" + acknowledged[0]);
						stop = true;

					} else {
						Thread.sleep(10);
					}
				} catch (InterruptedException e) {
					stop = true;
				}
			}
		}

	}

	@Override
	public void TimeoutElapsed(Object tag) {
		int z = (Integer) tag;
		// handle expiration of the timeout:
		System.out.println("Timer expired with tag=" + z);
	}

	@Override
	public void receiver() {
		System.out.println("Receiving...");

		// create the array that will contain the file contents
		// note: we don't know yet how large the file will be, so the easiest (but not most efficient)
		//   is to reallocate the array every time we find out there's more data
		Integer[] fileContents = new Integer[0];

		// loop until we are done receiving the file
		boolean stop = false;
		while (!stop) {

			// try to receive a packet from the network layer
			Integer[] packet = getNetworkLayer().receivePacket();

			// if we indeed received a packet
			if (packet != null) {

				// tell the user
				System.out.println("Received packet, length=" + packet.length + "  first byte=" + packet[0]);

				// append the packet's data part (excluding the header) to the fileContents array, first making it larger
				int oldlength = fileContents.length;
				int datalen = packet.length - HEADERSIZE;
				fileContents = Arrays.copyOf(fileContents, oldlength + datalen);
				System.arraycopy(packet, HEADERSIZE, fileContents, oldlength, datalen);

				Integer[] ack = new Integer[1];
				ack[0] = packet[0];
				// send the ack to the network layer
				getNetworkLayer().sendPacket(ack);
				System.out.println("Sent acknowledgement: " + ack[0]);

				// and let's just hope the file is now complete
				if (packet[0] == 0) {
					stop = true;
				}

			} else {
				// wait ~10ms (or however long the OS makes us wait) before trying again
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					stop = true;
				}
			}
		}

		// write to the output file
		Utils.setFileContents(fileContents, getFileID());
	}
}
