/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.inbound.endpoint.protocol.http.core.impl;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundRequestProcessor;
import org.wso2.carbon.inbound.endpoint.protocol.http.utils.InboundConfiguration;
import org.wso2.carbon.inbound.endpoint.protocol.http.utils.InboundConstants;

import java.net.InetSocketAddress;


/**
 * Listener class for Http Inbound Endpoints
 */
public class InboundHttpListener implements InboundRequestProcessor {
    protected Log log = LogFactory.getLog(this.getClass());

    private String injectingSequence;
    private String onErrorSequence;
    private SynapseEnvironment synapseEnvironment;
    private String port;
    private InboundConfiguration inboundConfiguration;

    public InboundHttpListener(InboundProcessorParams params) {

        this.port = params.getProperties().
                getProperty(InboundConstants.INBOUND_ENDPOINT_PARAMETER_HTTP_PORT);
        this.injectingSequence = params.getInjectingSeq();
        this.onErrorSequence = params.getOnErrorSeq();
        this.synapseEnvironment = params.getSynapseEnvironment();
        this.inboundConfiguration = new InboundConfiguration();

    }

    @Override
    public void init() {
        InboundHttpSourceHandler inboundHttpSourceHandler = new InboundHttpSourceHandler(this.inboundConfiguration, synapseEnvironment, injectingSequence, onErrorSequence);
        InboundHttpGlobalConfiguration.addInboundHttpSourceHandler(Integer.parseInt(this.port),inboundHttpSourceHandler);
        start();
    }

    public void start() {
        startIOReactor();
        startEndpoint();
    }

    @Override
    public void destroy() {
       InboundHttpGlobalConfiguration.closeEndpoint(Integer.parseInt(this.port));
    }

    /**
     * start endpoints for ports specified
     */
    private void startEndpoint() {
        InetSocketAddress inetSocketAddress = new InetSocketAddress(Integer.parseInt(port));
        InboundHttpGlobalConfiguration.startEndpoint(inetSocketAddress);
    }


    private void startIOReactor() {
        InboundHttpGlobalConfiguration.startIoReactor();
    }
}
