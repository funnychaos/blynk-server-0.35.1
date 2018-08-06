package cc.blynk.server.application.handlers.main.logic.reporting;

import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.dao.ReportingDao;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.widgets.Target;
import cc.blynk.server.core.protocol.exceptions.IllegalCommandException;
import cc.blynk.server.core.protocol.exceptions.NoDataException;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.reporting.GraphPinRequest;
import cc.blynk.utils.StringUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

import static cc.blynk.server.core.protocol.enums.Command.GET_GRAPH_DATA_RESPONSE;
import static cc.blynk.server.internal.CommonByteBufUtil.makeBinaryMessage;
import static cc.blynk.server.internal.CommonByteBufUtil.noData;
import static cc.blynk.server.internal.CommonByteBufUtil.ok;
import static cc.blynk.server.internal.CommonByteBufUtil.serverError;
import static cc.blynk.utils.ByteUtils.compress;
import static cc.blynk.utils.StringUtils.split2Device;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class GetGraphDataLogic {

    private static final Logger log = LogManager.getLogger(GetGraphDataLogic.class);

    private final BlockingIOProcessor blockingIOProcessor;
    private final ReportingDao reportingDao;

    public GetGraphDataLogic(ReportingDao reportingDao, BlockingIOProcessor blockingIOProcessor) {
        this.reportingDao = reportingDao;
        this.blockingIOProcessor = blockingIOProcessor;
    }

    public void messageReceived(ChannelHandlerContext ctx, User user, StringMessage message) {
        //warn: split may be optimized
        //todo remove space after app migration
        String[] messageParts = message.body.split(StringUtils.BODY_SEPARATOR_STRING);

        if (messageParts.length < 3) {
            throw new IllegalCommandException("Wrong income message format.");
        }

        String[] dashIdTargetId = split2Device(messageParts[0]);
        int dashId = Integer.parseInt(dashIdTargetId[0]);
        int targetId = 0;
        if (dashIdTargetId.length == 2) {
            targetId = Integer.parseInt(dashIdTargetId[1]);
        }

        DashBoard dash = user.profile.getDashByIdOrThrow(dashId);

        Target target = dash.getTarget(targetId);
        if (target == null) {
            log.debug("No assigned target for received command.");
            ctx.writeAndFlush(noData(message.id), ctx.voidPromise());
            return;
        }

        //history graph could be assigned only to device or device selector
        final int deviceId = target.getDeviceId();

        //special case for delete command
        if (messageParts.length == 4) {
            deleteGraphData(messageParts, user, dashId, deviceId);
            ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
        } else {
            process(ctx.channel(), dashId, deviceId,
                    Arrays.copyOfRange(messageParts, 1, messageParts.length), user, message.id, 4);
        }
    }

    private void process(Channel channel, int dashId, int deviceId, String[] messageParts,
                         User user, int msgId, int valuesPerPin) {
        int numberOfPins = messageParts.length / valuesPerPin;

        GraphPinRequest[] requestedPins = new GraphPinRequest[numberOfPins];

        for (int i = 0; i < numberOfPins; i++) {
            requestedPins[i] = new GraphPinRequest(dashId, deviceId, messageParts, i, valuesPerPin);
        }

        readGraphData(channel, user, requestedPins, msgId);
    }

    private void readGraphData(Channel channel, User user, GraphPinRequest[] requestedPins, int msgId) {
        blockingIOProcessor.executeHistory(() -> {
            try {
                byte[][] data = reportingDao.getReportingData(user, requestedPins);
                byte[] compressed = compress(requestedPins[0].dashId, data);

                if (compressed.length > Short.MAX_VALUE * 2) {
                    log.error("Data set for history graph is too large {}, for {}.", compressed.length, user.email);
                    channel.writeAndFlush(serverError(msgId), channel.voidPromise());
                } else {
                    if (channel.isWritable()) {
                        channel.writeAndFlush(
                                makeBinaryMessage(GET_GRAPH_DATA_RESPONSE, msgId, compressed),
                                channel.voidPromise()
                        );
                    }
                }
            } catch (NoDataException noDataException) {
                channel.writeAndFlush(noData(msgId), channel.voidPromise());
            } catch (Exception e) {
                log.error("Error reading reporting data. For user {}", user.email);
                channel.writeAndFlush(serverError(msgId), channel.voidPromise());
            }
        });
    }

    private void deleteGraphData(String[] messageParts, User user, int dashId, int deviceId) {
        try {
            PinType pinType = PinType.getPinType(messageParts[1].charAt(0));
            byte pin = Byte.parseByte(messageParts[2]);
            //String cmd = messageParts[3];
            //this check is not necessary actually
            //if (!"del".equals(cmd)) {
            //    throw new IllegalCommandBodyException("Wrong body format. Expecting 'del'.");
            //}
            reportingDao.delete(user, dashId, deviceId, pinType, pin);
        } catch (NumberFormatException e) {
            throw new IllegalCommandException("HardwareLogic command body incorrect.");
        }
    }

}
