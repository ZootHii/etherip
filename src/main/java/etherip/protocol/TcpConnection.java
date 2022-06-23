/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import etherip.util.Hexdump;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;

import static etherip.EtherNetIP.logger;
import static etherip.protocol.Encapsulation.Command.UnRegisterSession;

/**
 * Connection to EtherNet/IP device via TCP
 * <p>
 * Network connection as well as buffer and session info that's used for the duration of a connection.
 *
 * @author Kay Kasemir, László Pataki
 */
public class TcpConnection extends Connection
{
    private Socket socket;
    private BufferedInputStream inputStream;
    private BufferedOutputStream outputStream;
    private volatile boolean connected;

    /**
     * Initialize
     *
     * @param address
     *            IP address of device
     * @param slot
     *            Slot number 0, 1, .. of the controller within PLC crate
     */
    public TcpConnection(final String address, final int slot)
    {
        super(address, slot);
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
    public TcpConnection(final String address, final int port, final int slot, final int timeout_ms, final int retries)
    {
        super(address, port, slot, timeout_ms, retries);
    }

    @Override
    public synchronized void connect() throws Exception {
        if (!isOpen()) {
            this.socket = new Socket();
            this.socket.setReuseAddress(true);
            this.socket.setSoLinger(true, 1);
            this.socket.setKeepAlive(true);
            this.socket.setSoTimeout(timeout_ms);
            this.socket.connect(new InetSocketAddress(address, port), timeout_ms);
            this.outputStream = new BufferedOutputStream(socket.getOutputStream());
            this.inputStream = new BufferedInputStream(socket.getInputStream());
            this.connected = true;
            registerSession();
        }
    }

    /**
     * Register session
     */
    private void registerSession() throws Exception
    {
        final RegisterSession register = new RegisterSession();
        write(register);
        read(register);
        setSession(register.getSession());
    }


    /**
     * Write protocol data
     *
     * @param encoder
     *            {@link ProtocolEncoder} used to <code>encode</code> buffer
     * @throws Exception
     *             on error
     */
    @Override
    public synchronized void write(final ProtocolEncoder encoder) throws Exception
    {
        final StringBuilder log = logger.isLoggable(Level.FINER)
                ? new StringBuilder() : null;
        this.buffer.clear();
        encoder.encode(this.buffer, log);
        if (log != null)
        {
            logger.finer("Protocol Encoding\n" + log.toString());
        }

        this.buffer.flip();
        if (logger.isLoggable(Level.FINEST))
        {
            logger.log(Level.FINEST, "Data sent ({0} bytes):\n{1}",
                    new Object[] { this.buffer.remaining(),
                            Hexdump.toHexdump(this.buffer) });
        }

        int to_write = this.buffer.limit();
        byte[] buf = new byte[to_write];
        buffer.get(buf, 0, to_write);
        outputStream.write(buf);
        outputStream.flush();
    }

    /**
     * Read protocol data
     *
     * @param decoder
     *            {@link ProtocolDecoder} used to <code>decode</code> buffer
     * @throws Exception
     *             on error
     */
    @Override
    public synchronized void read(final ProtocolDecoder decoder) throws Exception
    {
        // Read until protocol has enough data to decode
        this.buffer.clear();
        byte[] buf = new byte[buffer.limit()];

        do
        {
            if (inputStream.read(buf) != -1) {
                buffer.put(buf);
            } else {
                throw new Exception("EOF");
            }
        }
        while (this.buffer.position() < decoder.getResponseSize(this.buffer));

        // Prepare to decode
        this.buffer.flip();

        if (logger.isLoggable(Level.FINEST))
        {
            logger.log(Level.FINEST, "Data read ({0} bytes):\n{1}",
                    new Object[] { this.buffer.remaining(),
                            Hexdump.toHexdump(this.buffer) });
        }

        final StringBuilder log = logger.isLoggable(Level.FINER)
                ? new StringBuilder() : null;
        try
        {
            decoder.decode(this.buffer, this.buffer.remaining(), log);
        }
        finally
        { // Show log even on error
            if (log != null)
            {
                logger.finer("Protocol Decoding\n" + log.toString());
            }
        }
    }

    @Override
    public synchronized void close() throws Exception
    {
        unregisterSession();
        closeQuietly(outputStream);
        closeQuietly(inputStream);
        closeQuietly(socket);
        connected = false;
    }


    /** Unregister session (device will close connection) */
    private void unregisterSession()
    {
        if (getSession() == 0 || !isOpen())
        {
            return;
        }
        try
        {
            Encapsulation unregisterSession = new Encapsulation(UnRegisterSession,
                    getSession(), new ProtocolAdapter());
            write(unregisterSession);
            // Cannot read after this point because PLC will close the connection
        }
        catch (final Exception ex)
        {
            logger.log(Level.WARNING,
                    "Error un-registering session: " + ex.getLocalizedMessage(),
                    ex);
        }
    }

    @Override
    public boolean isOpen()
    {
        return connected;
    }
}