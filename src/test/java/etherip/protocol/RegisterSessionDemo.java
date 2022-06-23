/*******************************************************************************
 * Copyright (c) 2012 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip.protocol;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import etherip.TestSettings;

/**
 * JUnitDemo of {@link RegisterSession}
 *
 * @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class RegisterSessionDemo
{
    @Test
    public void testRegisterSession() throws Exception
    {
        TestSettings.logAll();

        try (TcpConnection tcpConnection = new TcpConnection(
                TestSettings.get("plc"), TestSettings.getInt("slot"));)
        {
            assertThat(tcpConnection.getSession(), equalTo(0));
            tcpConnection.connect();
            System.out.println("Received session 0x"
                    + Integer.toHexString(tcpConnection.getSession()));
            assertThat(tcpConnection.getSession(), not(equalTo(0)));
        }
    }
}
