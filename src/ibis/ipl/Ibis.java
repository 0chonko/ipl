/* $Id$ */

package ibis.ipl;

import java.io.InputStream;
import java.io.IOException;

/**
 * An instance of this interface can only be created by the
 * {@link ibis.ipl.IbisFactory#createIbis(Capabilities, Capabilities,
 * TypedProperties, ResizeHandler)} method, and is the starting point
 * of all Ibis communication.
 */
public interface Ibis extends IbisCapabilities {

    /**
     * When running closed-world, returns the total number of Ibis instances
     * involved in the run. Otherwise returns -1.
     * @return the number of Ibis instances
     * @exception NumberFormatException is thrown when the property
     *   ibis.pool.total_hosts is not defined or does not represent a number.
     */
    public int totalNrOfIbisesInPool();

    /**
     * Allows reception of {@link ibis.ipl.ResizeHandler ResizeHandler}
     * upcalls.
     * If a {@link ibis.ipl.ResizeHandler ResizeHandler} is installed,
     * this call blocks until its
     * {@link ibis.ipl.ResizeHandler#joined(IbisIdentifier) joined()}
     * upcall for this Ibis is invoked.
     */
    public void enableResizeUpcalls();

    /**
     * Disables reception of
     * {@link ibis.ipl.ResizeHandler ResizeHandler} upcalls.
     */
    public void disableResizeUpcalls();

    /**
     * Returns all Ibis recources to the system.
     * @exception IOException is thrown when an error occurs.
     */
    public void end() throws IOException;

    /**
     * Creates a {@link ibis.ipl.PortType PortType}.
     * Port capabilities are specified (for example ports are "OneToOne",
     * with "Object serialization").
     * If no capabilities are given, the capabilities that were
     * requested from the Ibis implementation are used.
     * The capabilities define the <code>PortType</code>.
     * If two Ibis instances want to communicate, they must both
     * create a <code>PortType</code> with the same capabilities.
     * A <code>PortType</code> can be used to create
     * {@link ibis.ipl.ReceivePort ReceivePorts} and
     * {@link ibis.ipl.SendPort SendPorts}.
     * Only <code>ReceivePort</code>s and <code>SendPort</code>s of
     * the same <code>PortType</code> can communicate.
     * Any number of <code>ReceivePort</code>s and <code>SendPort</code>s
     * can be created on a JVM (even of the same <code>PortType</code>).
     * </p>
     * @param p capabilities of the porttype.
     * @return the porttype.
     * @exception ibis.ipl.PortMismatchException if the required capabilities
     * do not match the capabilities as specified when creating the Ibis
     * instance.
     */
    public PortType createPortType(Capabilities p)
            throws PortMismatchException;

    /**
     * Creates a {@link ibis.ipl.PortType PortType}.
     * See {@link #createPortType(Capabilities)}.
     * Also sets some attributes for this porttype.
     *
     * @param p capabilities of the porttype.
     * @param tp some attributes for this port type.
     * @return the porttype.
     * @exception ibis.ipl.PortMismatchException if the required capabilities
     * do not match the capabilities as specified when creating the Ibis
     * instance.
     */
    public PortType createPortType(Capabilities p, TypedProperties tp)
            throws PortMismatchException;

    /** 
     * Returns the Ibis {@linkplain ibis.ipl.Registry Registry}.
     * @return the Ibis registry.
     */
    public Registry registry();

    /**
     * Returns the capabilities of this Ibis implementation.
     * @return the capabilities of this Ibis implementation.
     */
    public Capabilities capabilities();

    /**
     * Polls the network for new messages.
     * An upcall may be generated by the poll. 
     * There is one poll for the entire Ibis, as this
     * can sometimes be implemented more efficiently than polling per
     * port. Polling per port is provided in the receiveport itself.
     * @exception IOException is thrown when a communication error occurs.
     */
    public void poll() throws IOException;

    /**
     * Returns an Ibis {@linkplain ibis.ipl.IbisIdentifier identifier} for
     * this Ibis instance.
     * An Ibis identifier identifies an Ibis instance in the network.
     * @return the Ibis identifier of this Ibis instance.
     */
    public IbisIdentifier identifier();

    /**
     * Returns the current Ibis version.
     * @return the ibis version.
     */
    public String getVersion();

    /**
     * May print Ibis-implementation-specific statistics.
     */
    public void printStatistics();

    /**
     * Returns the attributes as provided when instantiating Ibis.
     * @return the attributes.
     */
    public TypedProperties attributes();
}
