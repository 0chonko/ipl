package ibis.rmi.impl;

import ibis.ipl.Ibis;
import ibis.ipl.IbisException;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.NoMatchingIbisException;
import ibis.ipl.PortType;
import ibis.ipl.ReadMessage;
import ibis.ipl.ReceivePort;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.SendPort;
import ibis.ipl.StaticProperties;
import ibis.ipl.Upcall;
import ibis.ipl.WriteMessage;
import ibis.rmi.AlreadyBoundException;
import ibis.rmi.NotBoundException;
import ibis.rmi.Remote;
import ibis.rmi.RemoteException;
import ibis.rmi.StubNotFoundException;
import ibis.rmi.server.ExportException;
import ibis.rmi.server.RemoteRef;
import ibis.rmi.server.RemoteStub;
import ibis.rmi.server.SkeletonNotFoundException;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

public final class RTS {

    /** Sent when a remote invocation resulted in an exception. */
    public final static byte EXCEPTION    = 0;

    /** Sent when a remote invocation did not result in an exception. */
    public final static byte RESULT       = 1;

    public final static boolean DEBUG = false;

    //keys - impl objects, values - skeletons for those objects
    
    /**
     * Maps objects to their skeletons.
     * In fact, it maps hashcodes to skeletons. The reason for this is that some
     * objects have strange implementations for hashCode()!
     */
    private static HashMap skeletons;

    /**
     * Maps objects to their stubs.
     * In fact, it maps hashcodes to stubs. The reason for this is that some
     * objects have strange implementations for hashCode()!
     */
    private static HashMap stubs;

    /**
     * Maps ReceivePortIdentifiers to sendports that have a connection to that
     * receiveport.
     */
    private static HashMap sendports;

    /**
     * Maps an URL to a skeleton. We need this because a ReceivePortIdentifier no
     * longer uniquely defines the skeleton. In fact, a skeleton is now identified
     * by a number. Unfortunately, the Ibis registry can only handle ReceivePortIdentifiers.
     */
    private static Hashtable urlHash;	// No HashMap, this one should be synchronized.

    /**
     * This array maps skeleton ids to the corresponding skeleton.
     */
    private static ArrayList skeletonArray;

    /**
     * Cache receiveports from stubs, hashed with an IbisIdentifier of an Ibis
     * that has a connection to it.
     */
    private static HashMap receiveports;

    static String hostname;

    private static PortType requestPortType;
    private static PortType replyPortType;

    private static Ibis ibis;
    private static IbisIdentifier localID;
    private static ibis.ipl.Registry ibisRegistry;

    private static ThreadLocal clientHost;

    private static ReceivePort skeletonReceivePort = null;

    private static class UpcallHandler implements Upcall {

	public void upcall(ReadMessage r) throws IOException {
	    Skeleton skel;
	    int id = r.readInt();

	    if (id == -1) {
		String url = r.readString();
		skel = (Skeleton) urlHash.get(url);
	    }
	    else skel = (Skeleton)(skeletonArray.get(id));

	    int method = r.readInt();
	    int stubID = r.readInt();
	    try {
		skel.upcall(r, method, stubID);
	    } catch (RemoteException e) {
		WriteMessage w = skel.stubs[stubID].newMessage();
		w.writeByte(EXCEPTION);
		w.writeObject(e);
		w.finish();
	    }
	}
    }

    private static UpcallHandler upcallHandler;

    static {
	try {
	    skeletons = new HashMap();
	    stubs = new HashMap();
	    sendports = new HashMap();
	    urlHash = new Hashtable();
	    receiveports = new HashMap();
	    skeletonArray = new ArrayList();

	    upcallHandler = new UpcallHandler();

	    String h = InetAddress.getLocalHost().getHostName();
	    InetAddress adres = InetAddress.getByName(h);
	    adres = InetAddress.getByName(adres.getHostAddress());
	    hostname = adres.getHostName();

	    if (DEBUG) {
		System.out.println(hostname + ": init RMI RTS");
		System.out.println(hostname + ": creating ibis");
	    }

	    StaticProperties reqprops = new StaticProperties();
	    reqprops.add("serialization", "object");
	    reqprops.add("worldmodel", "open");
	    reqprops.add("communication", "OneToOne, ManyToOne, Reliable, AutoUpcalls, ExplicitReceipt");

	    try {
		ibis = Ibis.createIbis(reqprops, null);
	    } catch(NoMatchingIbisException e) {
		System.err.println("Could not find an Ibis that can run this RMI implementation");
		System.exit(1);
	    }

	    if (DEBUG) {
		System.out.println(hostname + ": ibis created");
	    }

	    localID      = ibis.identifier();
	    ibisRegistry = ibis.registry();

	    StaticProperties requestProps = new StaticProperties();
	    requestProps.add("serialization", "object");
	    requestProps.add("worldmodel", "open");
	    requestProps.add("communication", "OneToOne, ManyToOne, Reliable, AutoUpcalls");
	    requestPortType = ibis.createPortType("RMI", requestProps);

	    StaticProperties replyProps = new StaticProperties();
	    replyProps.add("serialization", "object");
	    replyProps.add("worldmodel", "open");
	    replyProps.add("communication", "OneToOne, Reliable, ExplicitReceipt");
	    requestPortType = ibis.createPortType("RMI request", requestProps);
	    replyPortType = ibis.createPortType("RMI reply", replyProps);

	    skeletonReceivePort = requestPortType.createReceivePort("//" + hostname + "/rmi_skeleton" + (new java.rmi.server.UID()).toString(), upcallHandler);
	    skeletonReceivePort.enableConnections();
	    skeletonReceivePort.enableUpcalls();

	    clientHost = new ThreadLocal();

	    if(DEBUG) {
		System.out.println(hostname + ": RMI RTS init done");
	    }

	} catch (Exception e) {
	    System.err.println(hostname + ": Could not init RMI RTS " + e);
	    e.printStackTrace();
	    System.exit(1);
	}

	/****
	 * This is only supported in SDK 1.4 and upwards. Comment out
	 * if you run an older SDK.
	 */
	Runtime.getRuntime().addShutdownHook(new Thread() {
	    public void run() {
		try {
		    ibis.end();
		    // System.err.println("Ended Ibis");
		} catch (IOException e) {
		    System.err.println("ibis.end throws " + e);
		}
	    }
	});
	/* End of 1.4-specific code */
    }

    private static String get_skel_name(Class c) {
	String class_name = c.getName();
	Package pkg = c.getPackage();
	String package_name = pkg != null ? pkg.getName() : null;
	if (package_name == null || package_name.equals("")) {
	    return "rmi_skeleton_" + class_name;
	}
	return package_name + ".rmi_skeleton_" + 
	    class_name.substring(class_name.lastIndexOf('.') + 1);
    }

    private static String get_stub_name(Class c) {
	String class_name = c.getName();
	Package pkg = c.getPackage();
	String package_name = pkg != null ? pkg.getName() : null;
	if (package_name == null || package_name.equals("")) {
	    return "rmi_stub_" + class_name;
	}
	return package_name + ".rmi_stub_" + 
	    class_name.substring(class_name.lastIndexOf('.') + 1);
    }

    private synchronized static Skeleton createSkel(Remote obj) throws SkeletonNotFoundException {
	try {
	    Skeleton skel;
	    Class c = obj.getClass();
	    ReceivePort rec;

	    String skel_name = get_skel_name(c);
	    // System.out.println("skel_name = " + skel_name);

	    // Use the classloader of the original class!
	    // Fix is by Fabrice Huet.
	    ClassLoader loader = c.getClassLoader();

	    Class skel_c = null;
	    if (loader != null) {
		skel_c = loader.loadClass(skel_name);
	    }
	    else {
		skel_c = Class.forName(skel_name);
	    }
	    skel = (Skeleton) skel_c.newInstance();

	    int skelId = skeletonArray.size();
	    skeletonArray.add(skel);
	    skel.init(skelId, obj);

	    skeletons.put(new Integer(System.identityHashCode(obj)), skel);

	    return skel;
	} catch (ClassNotFoundException ec) {
	    throw new SkeletonNotFoundException("Cannot find skeleton class", ec);
	} catch (InstantiationException en) {
	    throw new SkeletonNotFoundException("Cannot instantiate skeleton", en);
	} catch (IllegalAccessException el) {
	    throw new SkeletonNotFoundException("Cannot access skeleton", el);
	}
    }

    public static RemoteStub exportObject(Remote obj, RemoteRef r)
	    throws RemoteException
    {
	Stub stub;
	Class c = obj.getClass();
	Skeleton skel;
	String classname = c.getName();

	String class_name = classname.substring(classname.lastIndexOf('.') + 1);
	synchronized(RTS.class) {
	    skel = (Skeleton) skeletons.get(new Integer(System.identityHashCode(obj)));
	}
	if (skel == null) {
	    //create a skeleton
	    skel = createSkel(obj);
	} else {
	    throw new ExportException("object already exported");
	}

	//create a stub
	// Use the classloader of the original class!
	// Fix is by Fabrice Huet.
	try {
	    ClassLoader loader = obj.getClass().getClassLoader();

	    Class stub_c = null;
	    if (loader != null) {
		stub_c = loader.loadClass(get_stub_name(c));
	    }
	    else {
		stub_c = Class.forName(get_stub_name(c));
	    }
	    stub = (Stub) stub_c.newInstance();

	    stub.init(null, null, 0, skel.skeletonId, skeletonReceivePort.identifier(), false, r);

	} catch(ClassNotFoundException e) {
	    throw new StubNotFoundException("class " + get_stub_name(c) + " not found", e);
	} catch(InstantiationException e2) {
	    throw new StubNotFoundException("could not instantiate class " + get_stub_name(c), e2);
	} catch(IllegalAccessException e3) {
	    throw new StubNotFoundException("illegal access of class " + get_stub_name(c), e3);
	} catch(IOException e4) {
	    throw new StubNotFoundException("could not initialize stub " + get_stub_name(c), e4);
	}

	if (DEBUG) {
	    System.out.println(hostname + ": Created stub of type rmi_stub_" + classname);
	}

	stubs.put(new Integer(System.identityHashCode(obj)), stub);

	return (RemoteStub) stub;
    }

    public static synchronized Object getStub(Object o) {
	return stubs.get(new Integer(System.identityHashCode(o)));
    }



    public static synchronized void bind(String url, Remote o)
	throws AlreadyBoundException, IbisException, IOException, InstantiationException, IllegalAccessException
    {
	//	String url = "//" + RTS.hostname + "/" + name;

	if (DEBUG) {
	    System.out.println(hostname + ": Trying to bind object to " + url);
	}

	ReceivePortIdentifier dest = null;

	try {
	    dest = ibisRegistry.lookup(url, 1);
	} catch(IOException e) {
	}

	if (dest != null) {
	    throw new AlreadyBoundException(url + " already bound");
	}

	Skeleton skel = (Skeleton) skeletons.get(new Integer(System.identityHashCode(o)));
	if (skel == null) {
	    //		    throw new RemoteException("object not exported");
	    //or just export it???

	    skel = createSkel(o);
	}

	//new method
	ibisRegistry.bind(url, skeletonReceivePort.identifier());

	urlHash.put(url, skel);

	if (DEBUG) {
	    System.out.println(hostname + ": Bound to object " + url);
	}

    }

    public static synchronized void rebind(String url, Remote o)
	throws IbisException, IOException, InstantiationException, IllegalAccessException
    {
	if (DEBUG) {
	    System.out.println(hostname + ": Trying to rebind object to " + url);
	}

	Skeleton skel = (Skeleton) skeletons.get(new Integer(System.identityHashCode(o)));
	if (skel == null) {
	    //		    throw new RemoteException("object not exported");
	    //or just export it???
	    skel = createSkel(o);
	}

	//new method
	ibisRegistry.rebind(url, skeletonReceivePort.identifier());

	urlHash.put(url, skel);
    }

    public static void unbind(String url)
	throws NotBoundException, ClassNotFoundException, IOException
    {
	if (DEBUG) {
	    System.out.println(hostname + ": Trying to unbind object from " + url);
	}

	ReceivePortIdentifier dest = null;

	try {
	    dest = ibisRegistry.lookup(url, 1);
	} catch (IOException e) {
	}

	if (dest == null) {
	    throw new NotBoundException(url + " not bound");
	}

	//new method
	ibisRegistry.unbind(url);
    }


    public static Remote lookup(String url)  throws NotBoundException, IOException {

	Stub result;
	SendPort s = null;

	if (DEBUG) {
	    System.out.println(hostname + ": Trying to lookup object " + url);
	}

	ReceivePortIdentifier dest = null;

	synchronized(RTS.class) {
	    try {
		dest = ibisRegistry.lookup(url, 1);
		// System.err.println("ibisRegistry.lookup(" + url + ". 1) is " + dest);
	    } catch(IOException e) {
		// System.err.println("ibisRegistry.lookup(" + url + ". 1) throws " + e);
	    }
	}

	if (dest == null) {
	    throw new NotBoundException(url + " not bound");
	}

	if (DEBUG) {
	    System.out.println(hostname + ": Found skeleton " + url + " connecting");
	}

	s = getStubSendPort(dest);

	if (DEBUG) {
	    System.out.println(hostname + ": Got sendport");
	}

	ReceivePort r = getStubReceivePort(dest.ibis());

	if (DEBUG) {
	    System.out.println(hostname + ": Created receiveport for stub  -> id = " + r.identifier());
	}

	WriteMessage wm = s.newMessage();

	if (DEBUG) {
	    System.out.println(hostname + ": Created new WriteMessage");
	}

	wm.writeInt(-1);
	wm.writeString(url);
	wm.writeInt(-1);
	wm.writeInt(0);
	wm.writeObject(r.identifier());
	wm.finish();

	if (DEBUG) {
	    System.out.println(hostname + ": Sent new WriteMessage");
	}

	ReadMessage rm = r.receive();

	if (DEBUG) {
	    System.out.println(hostname + ": Received readMessage");
	}

	int stubID = rm.readInt();
	int skelID = rm.readInt();

	try {
	    result = (Stub) rm.readObject();
	} catch(ClassNotFoundException e) {
	    throw new RemoteException("ClassNotFoundException ", e);
	}
	rm.finish();

	result.init(s, r, stubID, skelID, dest, true, null);

	if (DEBUG) {
	    System.out.println(hostname + ": Found object " + url);
	}
	return (Remote) result;
    }

    public static String[] list(String url) throws IOException
    {
	int urlLength = url.length();
	String[] names = ibisRegistry.list(url /*+ ".*"*/);
	for (int i=0; i<names.length; i++) {
	    names[i] = names[i].substring(urlLength);
	}
	return names;
    }

    public static SendPort createSendPort(PortType p)
	throws IOException
    {
	SendPort s = p.createSendPort();
	s.setReplacer(new RMIReplacer());
	return s;
    }

    public static synchronized SendPort getSkeletonSendPort(ReceivePortIdentifier rpi)
	throws IOException
    {
	SendPort s = (SendPort) sendports.get(rpi);
	if (s == null) {
	    s = createSendPort(replyPortType);
	    s.connect(rpi);
	    sendports.put(rpi, s);
	    if (DEBUG) {
		System.out.println(hostname + ": New skeleton sendport for receiport: " + rpi);
	    }
	}
	else {
	    if (DEBUG) {
		System.out.println(hostname + ": Reuse skeleton sendport for receiport: " + rpi);
	    }
	}
	return s;
    }

    public static synchronized SendPort getStubSendPort(ReceivePortIdentifier rpi)
	throws IOException
    {
	SendPort s = (SendPort) sendports.get(rpi);
	if (s == null) {
	    s = createSendPort(requestPortType);
	    s.connect(rpi);
	    sendports.put(rpi, s);
	    if (DEBUG) {
		System.out.println(hostname + ": New stub sendport for receiport: " + rpi);
	    }
	}
	else {
	    if (DEBUG) {
		System.out.println(hostname + ": Reuse stub sendport for receiport: " + rpi);
	    }
	}
	return s;
    }

    public static synchronized ReceivePort getStubReceivePort(IbisIdentifier ibis)
	    throws IOException
    {
	ArrayList a = (ArrayList) receiveports.get(ibis);
	ReceivePort r;

	if (DEBUG) {
	    System.out.println("receiveport wanted for ibis " + ibis);
	}

	if (a == null || a.size() == 0) {

	    r = replyPortType.createReceivePort("//" + hostname + "/rmi_stub" + (new java.rmi.server.UID()).toString());
	    if (DEBUG) {
		System.out.println(hostname + ": New receiveport: " + r.identifier());
	    }
	    r.enableConnections();
	}
	else {
	    r = (ReceivePort) a.remove(a.size() - 1);
	    if (DEBUG) {
		System.out.println(hostname + ": Reuse receiveport: " + r.identifier());
	    }
	}
	return r;
    }

    public static synchronized void putStubReceivePort(ReceivePort r, IbisIdentifier ibis) {
	if (DEBUG) {
	    System.out.println("receiveport " + r + " returned for ibis " + ibis);
	}
	ArrayList a = (ArrayList) receiveports.get(ibis);
	if (a == null) {
	    a = new ArrayList();
	    receiveports.put(ibis, a);
	}
	a.add(r);
    }

    public static void createRegistry(int port) throws RemoteException
    {
	String url = "registry://" + hostname + ":" + port;
	try {
	    replyPortType.createReceivePort(url);
	} catch (IOException e) {
	    throw new RemoteException("there already is a registry running on port " + port);
	}
    }


    public static String getHostname() {
	return hostname;
    }

    public static void setClientHost(String s) {
	clientHost.set(s);
    }

    public static String getClientHost() {
	Object o = clientHost.get();
	if (o == null) return "UNKNOWN_HOST";
	String s = (String) o;
	return s;
    }
}
