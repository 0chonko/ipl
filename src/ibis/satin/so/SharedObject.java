package ibis.satin.so;

import ibis.satin.impl.Satin;

public class SharedObject implements java.io.Serializable {

    public String objectId;

    static int sharedObjectsCounter = 0;

    protected SharedObject() {}

    public void exportObject() {
	Satin satin = Satin.getSatin();

	if (satin != null) {
	    //create identifier
	    sharedObjectsCounter++;
	    objectId = "satin_shared_object" + sharedObjectsCounter 
		+ "@" + Satin.getSatinIdent().name();
	    
	    //add yourself to the sharedObjects hashtable
	    satin.addObject(this);
	}
	
	if (satin != null) {
	    synchronized (satin) {
		satin.broadcastSharedObject(this);
	    }
	}	
    }

}