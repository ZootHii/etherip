/*******************************************************************************
 * Copyright (c) 2012 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import static etherip.EtherNetIP.logger;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Connection to EtherNet/IP device
 * <p>
 * Network connection as well as buffer and session info that's used for the duration of a connection.
 *
 * @author Kay Kasemir, László Pataki
 */
@SuppressWarnings("nls")
public abstract class Connection implements AutoCloseable
{
    /** EtherIP uses little endian */
    final public static ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    final private static int BUFFER_SIZE = 600;

    final private static int DEFAULT_TIMEOUT_MS = 2000;

    final private static int DEFAULT_PORT = 0xAF12;

    final private static int DEFAULT_RETRIES = 3;

    final private static int DEFAULT_SLEEP = 500;

    protected final String address;

    protected final int port;

    protected final int slot;

    protected final int timeout_ms;

    protected final int retries;

    protected final ByteBuffer buffer;

    private volatile int session = 0;

    /**
     * Initialize
     *
     * @param address
     *            IP address of device
     * @param slot
     *            Slot number 0, 1, .. of the controller within PLC crate
     */
    public Connection(final String address, final int slot)
    {
        this(address, DEFAULT_PORT, slot, DEFAULT_TIMEOUT_MS, DEFAULT_RETRIES);
    }

    /**
     * Initialize
     *
     * @param address
     *            IP address of device
     * @param port
     *            Port number of device
     * @param slot
     *            Slot number 0, 1, .. of the controller within PLC crate
     * @param timeout_ms
     *            Timeout in ms
     * @param retries
     *            Connection retry count
     */
    public Connection(final String address, final int port, final int slot, final int timeout_ms, final int retries)
    {
        logger.log(Level.INFO, "Connecting to {0}:{1}",
                new Object[] { address, port });
        this.address = address;
        this.port = port;
        this.slot = slot;
        this.timeout_ms = timeout_ms;
        this.retries = retries;
        this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.buffer.order(BYTE_ORDER);
    }

    /** @return IP address of device */
    public String getAddress()
    {
        return this.address;
    }

    /** @return Slot number 0, 1, .. of the controller within PLC crate */
    public int getSlot()
    {
        return this.slot;
    }

    /** @return Port number of device */
    public int getPort()
    {
        return this.port;
    }

    /** @return Timeout in ms */
    public int getTimeoutMs()
    {
        return this.timeout_ms;
    }

    /** @return Session ID of this connection */
    public int getSession()
    {
        return this.session;
    }

    /**
     * @param session
     *            Session ID to be identified with this connection
     */
    protected void setSession(final int session)
    {
        this.session = session;
    }

    public abstract boolean isOpen();

    public abstract void connect() throws Exception;

    /**
     * Write protocol data
     *
     * @param encoder
     *            {@link ProtocolEncoder} used to <code>encode</code> buffer
     * @throws Exception
     *             on error
     */
    public abstract void write(final ProtocolEncoder encoder) throws Exception;

    /**
     * Read protocol data
     *
     * @param decoder
     *            {@link ProtocolDecoder} used to <code>decode</code> buffer
     * @throws Exception
     *             on error
     */
    public abstract void read(final ProtocolDecoder decoder) throws Exception;

    /**
     * Write protocol request and handle response
     *
     * @param protocol
     *            {@link Protocol}
     * @throws Exception
     *             on error
     */
    public synchronized void execute(Protocol protocol) throws Exception
    {
        final int retryLimit = retries > 0 ? retries : DEFAULT_RETRIES;
        int retryCounter = 0;
        boolean retry = true;
        while (retry) {
            try {
                if (!isOpen()) {
                    connect();
                    if (protocol instanceof Encapsulation) {
                        protocol = ((Encapsulation) protocol).withNewSession(session);
                    }
                }
                this.write(protocol);
                this.read(protocol);
                retry = false;
            } catch (IOException e) {
                close();
                if (++retryCounter >= retryLimit) {
                    throw new Exception(String.format("Failed to execute %d times", retryCounter), e);
                } else {
                    sleep(retryCounter);
                }
            }
        }
    }

    protected void closeQuietly(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException ignored) {
                //Ignore
            }
        }
    }

    protected void sleep(final int count) {
        long ms = (DEFAULT_SLEEP / 2) + (long) (ThreadLocalRandom.current().nextDouble() * DEFAULT_SLEEP * count);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            //Ignore
        }
    }
}
