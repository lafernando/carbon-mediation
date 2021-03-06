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


import org.apache.http.*;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;

import org.apache.synapse.transport.passthru.Pipe;
import org.apache.synapse.transport.passthru.ProtocolState;
import org.wso2.carbon.inbound.endpoint.protocol.http.utils.InboundConfiguration;


import java.io.IOException;
import java.util.*;

public class InboundHttpSourceResponse {
    private Pipe pipe = null;
    /**
     * Transport headers
     */
    private Map<String, TreeSet<String>> headers = new HashMap<String, TreeSet<String>>();
    /**
     * Status of the response
     */
    private int status = HttpStatus.SC_OK;
    /**
     * Status line
     */
    private String statusLine = null;
    /**
     * Actual response submitted
     */
    private HttpResponse response = null;
    /**
     * Configuration of the receiver
     */
    private InboundConfiguration sourceConfiguration;
    /**
     * Version of the response
     */
    private ProtocolVersion version = HttpVersion.HTTP_1_1;
    /**
     * Connection strategy
     */
    private ConnectionReuseStrategy connStrategy = new DefaultConnectionReuseStrategy();
    /** Chunk response or not */
    // private boolean chunk = true;
    /**
     * response has an entity or not*
     */
    private boolean hasEntity = true;

    private InboundHttpSourceRequest request = null;

    /**
     * If version change required default HTTP 1.1 will be overridden
     */
    private boolean versionChangeRequired = false;

    public InboundHttpSourceResponse(InboundConfiguration config, int status, InboundHttpSourceRequest request) {
        this(config, status, null, request);
    }

    public InboundHttpSourceResponse(InboundConfiguration config, int status, String statusLine,
                                     InboundHttpSourceRequest request) {
        this.status = status;
        this.statusLine = statusLine;
        this.sourceConfiguration = config;
        this.request = request;
    }

    public void connect(Pipe pipe) {
        this.pipe = pipe;

        if (request != null && pipe != null) {
            InboundSourceContext.get(request.getConnection()).setWriter(pipe);
        }
    }

    /**
     * Starts the response by writing the headers
     *
     * @param conn connection
     * @throws java.io.IOException           if an error occurs
     * @throws org.apache.http.HttpException if an error occurs
     */
    public void start(NHttpServerConnection conn) throws IOException, HttpException {
        // create the response
        response = sourceConfiguration.getHttpResponseFactory().newHttpResponse(
                request.getVersion(), this.status,
                request.getConnection().getContext());

        if (statusLine != null) {
            response.setStatusLine(version, status, statusLine);
        }
        if (versionChangeRequired) {
            response.setStatusLine(version, status);
        } else {
            response.setStatusCode(status);
        }

        BasicHttpEntity entity = null;

        if (canResponseHaveBody(request.getRequest(), response)) {
            entity = new BasicHttpEntity();

            int contentLength = -1;
            String contentLengthHeader = null;
            if (headers.get(HTTP.CONTENT_LEN) != null && headers.get(HTTP.CONTENT_LEN).size() > 0) {
                contentLengthHeader = headers.get(HTTP.CONTENT_LEN).first();
            }

            if (contentLengthHeader != null) {
                contentLength = Integer.parseInt(contentLengthHeader);
                headers.remove(HTTP.CONTENT_LEN);
            }

            if (contentLength != -1) {
                entity.setChunked(false);
                entity.setContentLength(contentLength);
            } else {
                entity.setChunked(true);
            }

        }

        response.setEntity(entity);

        // set any transport headers
        Set<Map.Entry<String, TreeSet<String>>> entries = headers.entrySet();

        for (Map.Entry<String, TreeSet<String>> entry : entries) {
            if (entry.getKey() != null) {
                Iterator<String> i = entry.getValue().iterator();
                while (i.hasNext()) {
                    response.addHeader(entry.getKey(), i.next());
                }
            }
        }
        response.setParams(new DefaultedHttpParams(response.getParams(),
                sourceConfiguration.buildHttpParams()));

        InboundSourceContext.updateState(conn, ProtocolState.RESPONSE_HEAD);

        // Pre-process HTTP response
        conn.getContext().setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        conn.getContext().setAttribute(ExecutionContext.HTTP_RESPONSE, response);
        conn.getContext().setAttribute(ExecutionContext.HTTP_REQUEST,
                InboundSourceContext.getRequest(conn).getRequest());

        sourceConfiguration.getHttpProcessor().process(response, conn.getContext());
        conn.submitResponse(response);

        // Handle non entity body responses
        if (entity == null) {
            hasEntity = false;
            // Reset connection state
            sourceConfiguration.getSourceConnections().releaseConnection(conn);
            // Make ready to deal with a new request
            conn.requestInput();
        }
    }

//    public void checkResponseChunkDisable(MessageContext responseMsgContext) throws IOException {
//        String forceHttp10 = (String) responseMsgContext.getProperty(PassThroughConstants.FORCE_HTTP_1_0);
//
//        if ("true".equals(forceHttp10) || responseMsgContext.isPropertyTrue(PassThroughConstants.DISABLE_CHUNKING, false)) {
//            if (!responseMsgContext.isPropertyTrue(PassThroughConstants.MESSAGE_BUILDER_INVOKED,
//                    false)) {
//                try {
//                    RelayUtils.buildMessage(responseMsgContext, false);
//                    responseMsgContext.getEnvelope().buildWithAttachments();
//                } catch (Exception e) {
//                    throw new AxisFault(e.getMessage());
//                }
//            }
//
//            if("true".equals(forceHttp10)){
//                version = HttpVersion.HTTP_1_0;
//                versionChangeRequired=true;
//            }
//
//            Boolean noEntityBody =
//                    (Boolean) responseMsgContext.getProperty(PassThroughConstants.NO_ENTITY_BODY);
//
//            if (noEntityBody != null && Boolean.TRUE == noEntityBody) {
//                headers.remove(HTTP.CONTENT_TYPE);
//                return;
//            }
//
//            MessageFormatter formatter =
//                    MessageProcessorSelector.getMessageFormatter(responseMsgContext);
//            OMOutputFormat format = PassThroughTransportUtils.getOMOutputFormat(responseMsgContext);
//            ByteArrayOutputStream out = new ByteArrayOutputStream();
//            formatter.writeTo(responseMsgContext, format, out, false);
//            TreeSet<String> header = new TreeSet<String>();
//            header.add(String.valueOf(out.toByteArray().length));
//            headers.put(HTTP.CONTENT_LEN, header);
//        }
//    }

    /**
     * Consume the content through the Pipe and write them to the wire
     *
     * @param conn    connection
     * @param encoder encoder
     * @return number of bytes written
     * @throws java.io.IOException if an error occurs
     */
    public int write(NHttpServerConnection conn, ContentEncoder encoder) throws IOException {
        int bytes = 0;
        if (pipe != null) {
            bytes = pipe.consume(encoder);
        } else {
            encoder.complete();
        }
        // Update connection state
        if (encoder.isCompleted()) {
            InboundSourceContext.updateState(conn, ProtocolState.RESPONSE_DONE);
            if (response != null && !this.connStrategy.keepAlive(response, conn.getContext())) {
                InboundSourceContext.updateState(conn, ProtocolState.CLOSING);

                sourceConfiguration.getSourceConnections().closeConnection(conn);
            } else if (InboundSourceContext.get(conn).isShutDown()) {
                // we need to shut down if the shutdown flag is set
                InboundSourceContext.updateState(conn, ProtocolState.CLOSING);

                sourceConfiguration.getSourceConnections().closeConnection(conn);
            } else {
                // Reset connection state
                sourceConfiguration.getSourceConnections().releaseConnection(conn);
                // Ready to deal with a new request
                conn.requestInput();
            }
        }
        return bytes;
    }

    public void addHeader(String name, String value) {
        if (headers.get(name) == null) {
            TreeSet<String> values = new TreeSet<String>();
            values.add(value);
            headers.put(name, values);
        } else {
            TreeSet<String> values = headers.get(name);
            values.add(value);
        }
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void removeHeader(String name) {
        if (headers.get(name) != null) {
            headers.remove(name);
        }
    }

    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {
        if (request != null && "HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        int status = response.getStatusLine().getStatusCode();
        return status >= HttpStatus.SC_OK
                && status != HttpStatus.SC_NO_CONTENT
                && status != HttpStatus.SC_NOT_MODIFIED
                && status != HttpStatus.SC_RESET_CONTENT;
    }

    public boolean hasEntity() {
        return this.hasEntity;
    }

}
