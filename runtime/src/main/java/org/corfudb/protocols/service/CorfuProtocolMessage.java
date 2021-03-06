package org.corfudb.protocols.service;

import lombok.extern.slf4j.Slf4j;
import org.corfudb.runtime.proto.RpcCommon.UuidMsg;
import org.corfudb.runtime.proto.ServerErrors.ServerErrorMsg;
import org.corfudb.runtime.proto.service.CorfuMessage.PriorityLevel;
import org.corfudb.runtime.proto.service.CorfuMessage.ProtocolVersionMsg;
import org.corfudb.runtime.proto.service.CorfuMessage.HeaderMsg;
import org.corfudb.runtime.proto.service.CorfuMessage.RequestMsg;
import org.corfudb.runtime.proto.service.CorfuMessage.RequestPayloadMsg;
import org.corfudb.runtime.proto.service.CorfuMessage.ResponseMsg;
import org.corfudb.runtime.proto.service.CorfuMessage.ResponsePayloadMsg;

import java.util.UUID;

import static org.corfudb.protocols.CorfuProtocolCommon.getUuidMsg;

/**
 * This class provides methods for creating the Protobuf objects defined
 * in corfu_message.proto. These provide the interface for obtaining headers,
 * as well as the main request and response (Protobuf) messages sent by the
 * client and server.
 */
@Slf4j
public class CorfuProtocolMessage {
    // Prevent class from being instantiated
    private CorfuProtocolMessage() {}

    /**
     * Returns a header containing information common to all service RPCs.
     *
     * @param requestId         the request id, generated by the client
     * @param priority          the priority of the message
     * @param epoch             the epoch of the message
     * @param clusterId         the cluster id
     * @param clientId          the clients own id
     * @param ignoreClusterId   indicates if the message is clusterId aware
     * @param ignoreEpoch       indicates if the message is epoch aware
     */
    public static HeaderMsg getHeaderMsg(long requestId, PriorityLevel priority, long epoch, UuidMsg clusterId,
                                         UuidMsg clientId, boolean ignoreClusterId, boolean ignoreEpoch) {
        return HeaderMsg.newBuilder()
                .setVersion(ProtocolVersionMsg.getDefaultInstance())
                .setRequestId(requestId)
                .setPriority(priority)
                .setEpoch(epoch)
                .setClusterId(clusterId)
                .setClientId(clientId)
                .setIgnoreClusterId(ignoreClusterId)
                .setIgnoreEpoch(ignoreEpoch)
                .build();
    }

    /**
     * Returns a header containing information common to all service RPCs.
     *
     * @param requestId         the request id, generated by the client
     * @param priority          the priority of the message
     * @param epoch             the epoch of the message
     * @param clusterId         the cluster id
     * @param clientId          the clients own id
     * @param ignoreClusterId   indicates if the message is clusterId aware
     * @param ignoreEpoch       indicates if the message is epoch aware
     */
    public static HeaderMsg getHeaderMsg(long requestId, PriorityLevel priority, long epoch, UUID clusterId,
                                         UUID clientId, boolean ignoreClusterId, boolean ignoreEpoch) {
        return getHeaderMsg(requestId, priority, epoch, getUuidMsg(clusterId),
                getUuidMsg(clientId), ignoreClusterId, ignoreEpoch);
    }

    /**
     * Returns a header containing information common to all service RPCs.
     * Used by the server as a convenient way to copy reusable field values.
     *
     * @param header            the original request header
     * @param ignoreClusterId   indicates if the message is clusterId aware
     * @param ignoreEpoch       indicates if the message is epoch aware
     */
    public static HeaderMsg getHeaderMsg(HeaderMsg header, boolean ignoreClusterId, boolean ignoreEpoch) {
        return HeaderMsg.newBuilder()
                .mergeFrom(header)
                .setIgnoreClusterId(ignoreClusterId)
                .setIgnoreEpoch(ignoreEpoch)
                .build();
    }

    /**
     * Returns a header containing information common to all service RPCs.
     * Use by the server to increase the priority of certain requests.
     *
     * @param header   the original request header
     */
    public static HeaderMsg getHighPriorityHeaderMsg(HeaderMsg header) {
        return HeaderMsg.newBuilder()
                .mergeFrom(header)
                .setPriority(PriorityLevel.HIGH)
                .build();
    }

    /**
     * Returns a request message sent by the clients.
     *
     * @param header    the request header
     * @param request   the request payload
     */
    public static RequestMsg getRequestMsg(HeaderMsg header, RequestPayloadMsg request) {
        return RequestMsg.newBuilder()
                .setHeader(header)
                .setPayload(request)
                .build();
    }

    /**
     * Returns a response message sent by the server.
     *
     * @param header     the response header
     * @param response   the response payload
     */
    public static ResponseMsg getResponseMsg(HeaderMsg header, ResponsePayloadMsg response) {
        return ResponseMsg.newBuilder()
                .setHeader(header)
                .setPayload(response)
                .build();
    }

    /**
     * Returns a response message containing an error sent by the server.
     *
     * @param header  the response header
     * @param error   the response error message
     */
    public static ResponseMsg getResponseMsg(HeaderMsg header, ServerErrorMsg error) {
        return ResponseMsg.newBuilder()
                .setHeader(header)
                .setPayload(ResponsePayloadMsg.newBuilder()
                        .setServerError(error)
                        .build())
                .build();
    }
}
