package ibis.impl.nameServer.tcp;

import ibis.impl.nameServer.NSProps;
import ibis.io.DummyInputStream;
import ibis.io.DummyOutputStream;
import ibis.ipl.ConnectionRefusedException;
import ibis.ipl.ConnectionTimedOutException;
import ibis.ipl.Ibis;
import ibis.ipl.IbisConfigurationException;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.IbisRuntimeException;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.StaticProperties;
import ibis.util.IPUtils;
import ibis.util.IbisSocketFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;


public class NameServerClient extends ibis.impl.nameServer.NameServer implements Runnable, Protocol {
	static final boolean DEBUG = false;

	private PortTypeNameServerClient portTypeNameServerClient;
	private ReceivePortNameServerClient receivePortNameServerClient;
	private ElectionClient electionClient;

	private ServerSocket serverSocket;	
	private Ibis ibisImpl;
	private	IbisIdentifier id;

	private	volatile boolean stop = false;
	private InetAddress serverAddress;
	private String server;
	private int port;
	private String poolName;
	private InetAddress myAddress;

	static IbisSocketFactory socketFactory =
	    IbisSocketFactory.createFactory();

	public NameServerClient() {
		/* do nothing */
	}

	protected void init(Ibis ibisImpl) throws IOException, IbisConfigurationException {
		this.ibisImpl   = ibisImpl;
		this.id     = ibisImpl.identifier();

		Properties p = System.getProperties();

		myAddress = IPUtils.getAlternateLocalHostAddress();

		server = p.getProperty(NSProps.s_host);
		if (server == null) {
			throw new IbisConfigurationException("property ibis.name_server.host is not specified");
		}

		if (server.equals("localhost")) {
		    server = myAddress.getHostName();
		}

		poolName = p.getProperty(NSProps.s_key);
		if (poolName == null) {
			throw new IbisConfigurationException("property ibis.name_server.key is not specified");
		}

		String nameServerPortString = p.getProperty(NSProps.s_port);
		port = NameServer.TCP_IBIS_NAME_SERVER_PORT_NR;
		if (nameServerPortString != null) {
			try {
				port = Integer.parseInt(nameServerPortString);
				if(DEBUG) {
					System.err.println("Using nameserver port: " + port);
				}
			} catch (Exception e) {
				System.err.println("illegal nameserver port: " + nameServerPortString + ", using default");
			}
		}

		serverAddress = InetAddress.getByName(server);

		if (myAddress.equals(serverAddress)) {
			// Try and start a nameserver ...
			NameServer n = NameServer.createNameServer(true, false, true, false);
			if (n != null) {
			    n.setDaemon(true);
			    n.start();
			}
		}

		if(DEBUG) {
			System.err.println("Found nameServerInet " + serverAddress);
		}

		Socket s = null;
		int cnt = 0;
		while(s == null) {
		    try {
			cnt++;
			s = socketFactory.createSocket(serverAddress, 
				port, myAddress, -1);
			// myAddress = s.getLocalAddress();
		    } catch (ConnectionTimedOutException e) {
			if(cnt == 10) {
			    // Rather arbitrary, 10 seconds, print warning
			    System.err.println("Nameserver client failed"
			       + " to connect to nameserver\n at " 
			       + serverAddress + ":" + port 
			       + ", will keep trying");
			}
			else if(cnt == 60) {
			    // Rather arbitrary, 1 minute
			    System.err.println("Nameserver client failed"
			       + " to connect to nameserver\n at " 
			       + serverAddress + ":" + port);
			    System.err.println("Gave up after 60 seconds");
			    throw e;
			}
			try {
			    Thread.sleep(1000);
			} catch (InterruptedException e2) { 
			    // don't care
			}
		    }
		}

		serverSocket = socketFactory.createServerSocket(0, myAddress, true);

		DummyOutputStream dos = new DummyOutputStream(s.getOutputStream());
		ObjectOutputStream out =
			new ObjectOutputStream(new BufferedOutputStream(dos));

		if (DEBUG) { 
			System.out.println("NameServerClient: contacting nameserver");
		}
		out.writeByte(IBIS_JOIN);
		out.writeUTF(poolName);
		out.writeObject(id);
		out.writeObject(myAddress);
		out.writeInt(serverSocket.getLocalPort());
		out.flush();

		DummyInputStream di = new DummyInputStream(s.getInputStream());
		ObjectInputStream in  = new ObjectInputStream(new BufferedInputStream(di));

		int opcode = in.readByte();

		if (DEBUG) { 
			System.out.println("NameServerClient: nameserver reply, opcode " + opcode);
		}

		switch (opcode) { 
		case IBIS_REFUSED:
			socketFactory.close(in, out, s);
			throw new ConnectionRefusedException("NameServerClient: " + id.name() + " is not unique!");
		case IBIS_ACCEPTED:
			// read the ports for the other name servers and start the receiver thread...
			int temp = in.readInt(); /* Port for the PortTypeNameServer */
			portTypeNameServerClient = new PortTypeNameServerClient(myAddress, serverAddress, temp);
			
			temp = in.readInt(); /* Port for the ReceivePortNameServer */
			receivePortNameServerClient = new ReceivePortNameServerClient(myAddress, serverAddress, temp);

			temp = in.readInt(); /* Port for the ElectionServer */
			electionClient = new ElectionClient(myAddress, serverAddress, temp);

			int poolSize = in.readInt();
			if (DEBUG) { 
				System.out.println("NameServerClient: accepted by nameserver, poolsize " + poolSize);
			}
			for(int i=0; i<poolSize; i++) {
				IbisIdentifier newid;
				try {
					newid = (IbisIdentifier) in.readObject();
				} catch (ClassNotFoundException e) {
					throw new IOException("Receive IbisIdent of unknown class " + e);
				}
				if(DEBUG) {
					System.out.println("NameServerClient: join of " + newid);
				}
				ibisImpl.joined(newid);
				if(DEBUG) {
					System.out.println("NameServerClient: join of " + newid + " DONE");
				}
			}

			// at least read the tobedeleted stuff!
			int tmp = in.readInt();
			for (int i = 0; i < tmp; i++) {
			    try {
				in.readObject();
			    } catch(ClassNotFoundException e) {
				throw new IOException("Receive IbisIdent of unknown class " + e);
			    }
			}

			// Should we join ourselves?
			ibisImpl.joined(id);

			socketFactory.close(in, out, s);
			Thread t = new Thread(this, "NameServerClient accept thread");
			t.setDaemon(true);
			t.start();
			break;
		default:
			socketFactory.close(in, out, s);

			throw new StreamCorruptedException("NameServerClient: got illegal opcode " + opcode);
		}
	} 

	public boolean newPortType(String name, StaticProperties p) throws IOException {
		return portTypeNameServerClient.newPortType(name, p);
	}

	public long getSeqno(String name) throws IOException { 
		return portTypeNameServerClient.getSeqno(name);
	}

	public void leave() throws IOException { 
		if(DEBUG) {
			System.err.println("NS client: leave");
		}
		Socket s;

		try {
		    s = socketFactory.createSocket(serverAddress, port, myAddress, 5000);
		} catch(ConnectionTimedOutException e) {
		    // Apparently, the nameserver left.
		    return;
		}
		
		DummyOutputStream dos = new DummyOutputStream(s.getOutputStream());
		ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(dos));

		out.writeByte(IBIS_LEAVE);
		out.writeUTF(poolName);
		out.writeObject(id);
		out.flush();
		if(DEBUG) {
			System.err.println("NS client: leave sent");
		}

		DummyInputStream di = new DummyInputStream(s.getInputStream());
		ObjectInputStream in  = new ObjectInputStream(new BufferedInputStream(di));
		
		int temp = in.readByte();
		if(DEBUG) {
			System.err.println("NS client: leave ack received");
		}

		socketFactory.close(in, out, s);

//		stop = true;
//		this.interrupt();
		if(DEBUG) {
			System.err.println("NS client: leave DONE");
		}

	} 

	
	public void run() {
		if (DEBUG) { 
			System.out.println("NameServerClient: stread started");
		}

		while (true) { // !stop

			Socket s;
			IbisIdentifier id;

			try {
				s = socketFactory.accept(serverSocket);

				if (DEBUG) {
					System.out.println("NameServerClient: incoming connection from " + s.toString());
				}

			} catch (Exception e) {
				if (stop) { 
					if (DEBUG) { 
						System.out.println("NameServerClient: thread dying");
					}
					try { 
						serverSocket.close();
					} catch (IOException e1) {
						/* do nothing */
					}
					return;
                                }
				throw new IbisRuntimeException("NameServerClient: got an error", e);
			}
	
			int opcode = 666;

			try {
				DummyInputStream di = new DummyInputStream(s.getInputStream());			
				ObjectInputStream in  = new ObjectInputStream(new BufferedInputStream(di));

				opcode = in.readByte();
				if (DEBUG) {
				    System.out.println("NameServerClient: opcode " + opcode);
				}
	  
				switch (opcode) {
				case (IBIS_PING): {
					DummyOutputStream dos = new DummyOutputStream(s.getOutputStream());
					DataOutputStream out =
					    new DataOutputStream(new BufferedOutputStream(dos));
					out.writeUTF(poolName);
					socketFactory.close(in, out, s);
					}
					break;
				case (IBIS_JOIN):
					id = (IbisIdentifier) in.readObject();
					if (DEBUG) {
					    System.out.println("NameServerClient: receive join request " + id);
					}
					socketFactory.close(in, null, s);
					ibisImpl.joined(id);
					break;
				case (IBIS_LEAVE):
					id = (IbisIdentifier) in.readObject();
					socketFactory.close(in, null, s);
					if(id.equals(this.id)) {
						// received an ack from the nameserver that I left.
						if (DEBUG) { 
							System.out.println("NameServerClient: thread dying");
						}
						return;
					} else {
						ibisImpl.left(id);
					}
					break;
				default: 
					System.out.println("NameServerClient: got an illegal opcode " + opcode);
				}
			} catch (Exception e1) {
				System.out.println("Got an exception in NameServerClient.run (opcode = " + opcode + ") " + e1.toString());
				if(stop) return;
				e1.printStackTrace();
	  
				if (s != null) { 
					socketFactory.close(null, null, s);
				}
	  
			}
		}
	}  

	public ReceivePortIdentifier lookupReceivePort(String name) throws IOException {
		return lookupReceivePort(name, 0);
	}

	public ReceivePortIdentifier lookupReceivePort(String name, long timeout) throws IOException {
		return receivePortNameServerClient.lookup(name, timeout);
	}

	public IbisIdentifier lookupIbis(String name) throws IOException {
		return lookupIbis(name, 0);
	} 

	public IbisIdentifier lookupIbis(String name, long timeout) throws IOException {
		/* not implemented yet */
		return null;
	} 

	public ReceivePortIdentifier [] listReceivePorts(IbisIdentifier ident)  throws IOException, ClassNotFoundException { 
		/* not implemented yet */
		return new ReceivePortIdentifier[0];
	}

	public Object elect(String election, Object candidate) throws IOException, ClassNotFoundException {
		return electionClient.elect(election, candidate);
	}

	//gosia	

	public Object reelect(String election, Object candidate, Object formerRuler) throws IOException, ClassNotFoundException {
		return electionClient.reelect(election, candidate, formerRuler);
	}
	
	public void bind(String name, ReceivePortIdentifier rpi) throws IOException {
		receivePortNameServerClient.bind(name, rpi);
	}
	
	public void rebind(String name, ReceivePortIdentifier rpi) throws IOException {
		receivePortNameServerClient.rebind(name, rpi);
	}
	
	public void unbind(String name) throws IOException {
		receivePortNameServerClient.unbind(name);
	}

	public String[] listNames(String pattern) throws IOException {
		return receivePortNameServerClient.list(pattern);
	}
	//end gosia
} 
