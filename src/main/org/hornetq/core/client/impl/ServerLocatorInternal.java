/*
 * Copyright 2010 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.core.client.impl;

import java.util.concurrent.Executor;

import org.hornetq.api.core.Pair;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.ServerLocator;

/**
 * A ServerLocatorInternal
 *
 * @author Tim Fox
 *
 *
 */
public interface ServerLocatorInternal extends ServerLocator
{
   void start(Executor executor) throws Exception;
   
   Executor getExecutor();
   
   void factoryClosed(final ClientSessionFactory factory);
   
   AfterConnectInternalListener getAfterConnectInternalListener();
   
   void setAfterConnectionInternalListener(AfterConnectInternalListener listener);
   
   /** Used to better identify Cluster Connection Locators on logs while debugging logs */
   void setIdentity(String identity);

   void setNodeID(String nodeID);

   String getNodeID();
   
   void cleanup();

   ClientSessionFactoryInternal connect() throws  Exception;

   void notifyNodeUp(long uniqueEventID, String nodeID, Pair<TransportConfiguration, TransportConfiguration> connectorPair, boolean last);

   /**
    * 
    * @param uniqueEventID 0 means get the previous ID +1
    * @param nodeID
    */
   void notifyNodeDown(long uniqueEventID, String nodeID);

   void setClusterConnection(boolean clusterConnection);

   boolean isClusterConnection();

   TransportConfiguration getClusterTransportConfiguration();

   void setClusterTransportConfiguration(TransportConfiguration tc);

   boolean isBackup();
   
   void setBackup(boolean backup);

   Topology getTopology();
}