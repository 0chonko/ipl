package ibis.impl.nio;

import ibis.impl.nameServer.NameServer;
import ibis.ipl.Ibis;
import ibis.ipl.IbisException;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.IbisRuntimeException;
import ibis.ipl.PortType;
import ibis.ipl.Registry;
import ibis.ipl.StaticProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

public final class NioIbis extends Ibis implements Config {

    private NioIbisIdentifier ident;

    NameServer nameServer;
    private int poolSize;

    private Hashtable portTypeList = new Hashtable();

    private boolean open = false;
    private ArrayList joinedIbises = new ArrayList();
    private ArrayList leftIbises = new ArrayList();
    private ArrayList toBeDeletedIbises = new ArrayList();

    ChannelFactory factory;
    private boolean ended = false;

    private SendReceiveThread sendReceiveThread = null;

    public NioIbis() throws IbisException {
	try {
	    Runtime.getRuntime().addShutdownHook(new NioShutdown());
	} catch (Exception e) {
	    if (DEBUG) {
		Debug.message("general", this,
			      "!could not register nio shutdown hook");
	    }
	}
    }

    synchronized protected PortType newPortType(String name, StaticProperties p)
	throws IOException, IbisException {

	NioPortType resultPort = new NioPortType(this, name, p);
	p = resultPort.properties();

	if (nameServer.newPortType(name, p)) { 
	    /* add type to our table */
	    portTypeList.put(name, resultPort);

	    if (DEBUG) {
		Debug.message("connections", this,
			"created PortType `" + name + "'");
	    }
	}
	return resultPort;
    }

    long getSeqno(String name) throws IOException {
	return nameServer.getSeqno(name);
    }

    public Registry registry() {
	return nameServer;
    }
    
    public void sendReconfigure() throws IOException {
	nameServer.reconfigure();
    }
    
    public void sendDelete(IbisIdentifier ident) throws IOException {
	nameServer.delete(ident);
    } 

    public IbisIdentifier identifier() {
	return ident;
    }

    public String toString() {
	return ident.toString();
    }

    protected void init() throws IbisException, IOException { 
	if (DEBUG) {
	    Debug.enter("general", this, "initializing NioIbis");
	}

	poolSize = 1;

	ident = new NioIbisIdentifier(name);

	if (DEBUG) {
	    Debug.message("general", this,
				 "created IbisIdentifier" + ident);
	}

	nameServer = NameServer.loadNameServer(this);

	factory = new TcpChannelFactory();

	if (DEBUG) {
	    Debug.exit("general", this, "initialized NioIbis");
	}
    }

    /**
     * this method forwards the join to the application running on top of ibis.
     */
    public void join(IbisIdentifier joinIdent) { 
	synchronized (this) {
	    if(!open && resizeHandler != null) {
		joinedIbises.add(joinIdent);
		return;
	    }

	    if (DEBUG) {
		Debug.message("general", this,
			      "ibis '" + joinIdent.name() + "' joined"); 
	    }

	    poolSize++;
	}

	if(resizeHandler != null) {
	    resizeHandler.join(joinIdent);
	}
    }

    /**
     * this method forwards the leave to the application running on top of
     * ibis.
     */
    public void leave(IbisIdentifier leaveIdent) { 
	synchronized (this) {
	    if(!open && resizeHandler != null) {
		leftIbises.add(leaveIdent);
		return;
	    }


	    if (DEBUG) {
		Debug.message("general", this,
			    "ibis '" + leaveIdent.name() + "' left"); 
	    }

	    poolSize--;
	}

	if(resizeHandler != null) {
	    resizeHandler.leave(leaveIdent);
	}
    }
    
    public void delete(IbisIdentifier deleteIdent) {
	synchronized (this) {
	    if (!open && resizeHandler != null) {
		toBeDeletedIbises.add(deleteIdent);
		return;
	    }
	    if(DEBUG_LEVEL >= LOW_DEBUG_LEVEL) {
		System.out.println(name + ": Ibis '" + deleteIdent.name() + "' will be deleted"); 
	    }
	}
	
	if (resizeHandler != null) {
	    resizeHandler.delete(deleteIdent);
	}
    }
    
    public void reconfigure() {
	    if(DEBUG_LEVEL >= LOW_DEBUG_LEVEL) {
		System.out.println(name + ":reconfiguration");
	    }
	    if (resizeHandler != null) {
		resizeHandler.reconfigure();
	    }
    }
	
	    
    public PortType getPortType(String name) { 
	return (PortType) portTypeList.get(name);
    } 

    public void openWorld() {
	NioIbisIdentifier ident = null;

	if (DEBUG) {
	    Debug.enter("general", this, "opening world");
	}

	if(resizeHandler != null) {
	    while(true) {
		synchronized(this) {
		    if(joinedIbises.size() == 0) break;
		    poolSize++;
		    ident = (NioIbisIdentifier)joinedIbises.remove(0);
		}
		resizeHandler.join(ident); // Don't hold the lock during user upcall
	    }

	    while(true) {
		synchronized(this) {
		    if(leftIbises.size() == 0) break;
		    poolSize--;
		    ident = (NioIbisIdentifier)leftIbises.remove(0);
		}
		resizeHandler.leave(ident); // Don't hold the lock during user upcall

	    }
	}

	synchronized (this) {
	    open = true;
	}

	if (DEBUG) {
	    Debug.exit("general", this, "world opened"); 
	}
    }

    public synchronized void closeWorld() {
	open = false;
    }

    public void end() {
	synchronized(this) {
	    if(ended) {
		return;
	    }
	    ended = true;
	}
	try { 
	    if(nameServer != null) {
		nameServer.leave();
	    }
	    if(factory != null) {
		factory.quit();
	    }
	    if(sendReceiveThread != null) {
		sendReceiveThread.quit();
	    }
	} catch (Exception e) { 
	    throw new IbisRuntimeException("NioIbis: end failed ", e);
	} 
    }

    /**
     * does nothing.
     */
    public void poll() throws IOException {
    }

    /**
     * Called when the vm exits
     */
    class NioShutdown extends Thread {
	public void run() {
	    end();
	}
    }

    synchronized SendReceiveThread sendReceiveThread() throws IOException {
	if (sendReceiveThread == null) {
	    sendReceiveThread = new SendReceiveThread();
	}
	return sendReceiveThread;
    }
}
