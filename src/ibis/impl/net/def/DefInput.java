package ibis.ipl.impl.net.def;

import ibis.ipl.impl.net.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.net.SocketException;

public final class DefInput extends NetBufferedInput {

	private volatile Integer spn          = null;
	private InputStream      defIs        = null;
        private NetReceiveBuffer buf          = null;

	DefInput(NetPortType pt, NetDriver driver, String context)
		throws IOException {
		super(pt, driver, context);
		headerLength = 4;
	}

	/*
	 * {@inheritDoc}
	 */
	synchronized public void setupConnection(NetConnection cnx)
		throws IOException {
                if (this.spn != null) {
                        throw new Error("connection already established");
                }

                defIs    = cnx.getServiceLink().getInputSubStream(this, "def");

                mtu = 1024;
		if (factory == null) {
		    factory = new NetBufferFactory(mtu, new NetReceiveBufferFactoryDefaultImpl());
		} else {
		    factory.setMaximumTransferUnit(mtu);
		}

		this.spn = cnx.getNum();
                startUpcallThread();
	}

	/* Create a NetReceiveBuffer and do a blocking receive. */
	private NetReceiveBuffer receive() throws IOException {

		NetReceiveBuffer buf = createReceiveBuffer(0);
		byte [] b = buf.data;
		int     l = 0;

		int offset = 0;

		do {
			int result = defIs.read(b, offset, 4);
			if (result == -1) {
				if (offset != 0) {
					throw new Error("broken pipe");
				}

				// System.err.println("tcp_blk: receiveByteBuffer <-- null");
				return null;
			}

			offset += result;
		} while (offset < 4);

		l = NetConvert.readInt(b);
		//System.err.println("received "+l+" bytes");

		do {
			int result = defIs.read(b, offset, l - offset);
			if (result == -1) {
				throw new Error("broken pipe");
			}
			offset += result;
		} while (offset < l);

		buf.length = l;
		return buf;
	}


	/**
	 * {@inheritDoc}
	 */
	public Integer doPoll(boolean block) throws IOException {
		if (spn == null) {
			return null;
		}

		if (block) {
			buf = receive();
			if (buf != null) {
				return spn;
			}
		} else if (defIs.available() > 0) {
			return spn;
		}

		return null;
	}

       	public void doFinish() throws IOException {
        }


	/**
	 * {@inheritDoc}
	 *
	 * <BR><B>Note</B>: this function may block if the expected data is not there.
	 *
	 * @return {@inheritDoc}
	 */
	public NetReceiveBuffer receiveByteBuffer(int expectedLength) throws IOException {
                if (buf != null) {
                        NetReceiveBuffer temp = buf;
                        buf = null;
                        return temp;
                }

                NetReceiveBuffer buf = receive();
		return buf;
	}

        public synchronized void doClose(Integer num) throws IOException {
                if (spn == num) {
                        try {
                                defIs.close();
                        } catch (IOException e) {
                                throw new Error(e);
                        }

                        spn = null;
                }
        }


	/**
	 * {@inheritDoc}
	 */
	public void doFree() throws IOException {
                if (defIs != null) {
			defIs.close();
                }

                spn = null;
	}
}
