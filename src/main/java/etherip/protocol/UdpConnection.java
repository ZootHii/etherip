/*******************************************************************************
 * Copyright (c) 2017 NETvisor Ltd.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import static etherip.EtherNetIP.logger;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.logging.Level;

import etherip.util.Hexdump;

/**
 * Connection to EtherNet/IP device via UDP
 * <p>
 * Network connection as well as buffer and session info that's used for the duration of a connection.
 *
 * @author Kay Kasemir, László Pataki
 */
public class UdpConnection extends Connection
{
    private DatagramSocket datagramSocket;
    private InetSocketAddress inetSocketAddress;
    private volatile boolean isOpen;

    /**
     * Initialize
     *
     * @param address
     *            IP address of device
     * @param slot
     *            Slot number 0, 1, .. of the controller within PLC crate
     * @throws Exception
     */
    public UdpConnection(final String address, final int slot) throws Exception
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
     */
    public UdpConnection(final String address, final int port, final int slot, final int timeout_ms, final int retries) {
        super(address, port, slot, timeout_ms, retries);
    }

    @Override
    public synchronized void connect() throws Exception {
        if (!isOpen()) {
            inetSocketAddress = new InetSocketAddress(address, port);
            datagramSocket = new DatagramSocket();
            datagramSocket.setReceiveBufferSize(1024);
            datagramSocket.setSendBufferSize(1024);
            datagramSocket.setSoTimeout(timeout_ms);
            datagramSocket.connect(inetSocketAddress);
            isOpen = true;
        }
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

        DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length, inetSocketAddress);
        datagramSocket.send(datagramPacket);
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
            DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
            datagramSocket.receive(datagramPacket);
            buffer.put(buf);
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
        datagramSocket.close();
        isOpen = false;
    }

    @Override
    public boolean isOpen()
    {
        return isOpen;
    }
}