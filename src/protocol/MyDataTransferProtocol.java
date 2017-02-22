package protocol;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import client.*;

public class MyDataTransferProtocol extends IRDTProtocol {

	// change the following as you wish:
	static final int HEADERSIZE = 1; // number of header bytes in each packet
	static final int DATASIZE = 128; // max. number of user data bytes in each packet
	int sequence;
	private Set<Integer> receivedAcks = new HashSet<Integer>();

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
			sendPacket(pkt);

			// and loop and sleep; you may use this loop to check for incoming acks...
			boolean stop = false;
			while (!stop) {
				try {
					Integer[] acknowledged = getNetworkLayer().receivePacket();
					if (acknowledged != null) {
						receivedAcks.add(acknowledged[0]);
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
		Integer[] pkt = (Integer[]) tag;
		if (receivedAcks.contains(pkt[0])) {
			return;
		}
		// handle expiration of the timeout:
		System.out.println("Timer expired with tag=" + pkt[0]);
		// resend packet
		sendPacket(pkt);
	}

	private void sendPacket(Integer[] packet) {
		getNetworkLayer().sendPacket(packet);
		System.out.println("Sent packet with header=" + packet[0]);
		client.Utils.Timeout.SetTimeout(1000, this, packet);
	}

	@Override
	public void receiver() {
		System.out.println("Receiving...");

		// create the array that will contain the file contents
		// note: we don't know yet how large the file will be, so the easiest (but not most efficient)
		//   is to reallocate the array every time we find out there's more data
		Integer[] fileContents = new Integer[0];
		Set<Integer> receivedSequences = new HashSet<>();

		// loop until we are done receiving the file
		boolean stop = false;
		int lastSequence = -1;
		while (!stop) {

			// try to receive a packet from the network layer
			Integer[] packet = getNetworkLayer().receivePacket();

			// if we indeed received a packet
			if (packet != null && (lastSequence == -1 || lastSequence > packet[0])) {

				if (!receivedSequences.contains(packet[0])) {
					receivedSequences.add(packet[0]);
					lastSequence = packet[0];
					// tell the user
					System.out.println("Received packet, length=" + packet.length + "  first byte=" + packet[0]);

					// append the packet's data part (excluding the header) to the fileContents array, first making it larger
					int oldlength = fileContents.length;
					int datalen = packet.length - HEADERSIZE;
					fileContents = Arrays.copyOf(fileContents, oldlength + datalen);
					System.arraycopy(packet, HEADERSIZE, fileContents, oldlength, datalen);

					// and let's just hope the file is now complete
					if (packet[0] == 0) {
						stop = true;
					}
				}

				Integer[] ack = new Integer[1];
				ack[0] = packet[0];
				// send the ack to the network layer
				getNetworkLayer().sendPacket(ack);
				System.out.println("Sent acknowledgement: " + ack[0]);



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
