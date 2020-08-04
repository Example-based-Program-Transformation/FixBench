/*
 * Copyright 2010-2012 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.server;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.raw.ODatabaseRaw;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelBinary;
import com.orientechnologies.orient.server.config.OServerUserConfiguration;
import com.orientechnologies.orient.server.network.protocol.ONetworkProtocol;
import com.orientechnologies.orient.server.network.protocol.ONetworkProtocolData;

public class OClientConnection {
  public final int                         id;
  public final ONetworkProtocol            protocol;
  public final long                        since;
  public volatile ODatabaseDocumentTx      database;
  public volatile ODatabaseRaw             rawDatabase;
  public volatile OServerUserConfiguration serverUser;

  public ONetworkProtocolData              data = new ONetworkProtocolData();

  public OClientConnection(final int iId, final ONetworkProtocol iProtocol) throws IOException {
    this.id = iId;
    this.protocol = iProtocol;
    this.since = System.currentTimeMillis();
  }

  public void close() {
    if (database != null) {
      database.close();
      database = null;
    }
  }

  @Override
  public String toString() {
    return "OClientConnection [id=" + id + ", source="
        + (protocol != null && protocol.getChannel() != null ? protocol.getChannel().socket.getRemoteSocketAddress() : "?")
        + ", since=" + since + "]";
  }

  /**
   * Returns the remote network address in the format <ip>:<port>.
   */
  public String getRemoteAddress() {
    final InetSocketAddress remoteAddress = (InetSocketAddress) protocol.getChannel().socket.getRemoteSocketAddress();
    return remoteAddress.getAddress().getHostAddress() + ":" + remoteAddress.getPort();
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    final OClientConnection other = (OClientConnection) obj;
    if (id != other.id)
      return false;
    return true;
  }

  public OChannelBinary getChannel() {
    return (OChannelBinary) protocol.getChannel();
  }

  public ONetworkProtocol getProtocol() {
    return protocol;
  }
}
