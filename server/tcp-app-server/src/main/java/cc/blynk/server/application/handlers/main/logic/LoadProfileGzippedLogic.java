package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.Holder;
import cc.blynk.server.application.handlers.main.auth.AppStateHolder;
import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.dao.UserDao;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.Profile;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.serialization.JsonParser;
import cc.blynk.server.core.protocol.model.messages.MessageBase;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.db.DBManager;
import cc.blynk.server.db.model.FlashedToken;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.core.model.serialization.JsonParser.gzipDash;
import static cc.blynk.server.core.model.serialization.JsonParser.gzipDashRestrictive;
import static cc.blynk.server.core.model.serialization.JsonParser.gzipProfile;
import static cc.blynk.server.core.protocol.enums.Command.LOAD_PROFILE_GZIPPED;
import static cc.blynk.server.internal.CommonByteBufUtil.illegalCommand;
import static cc.blynk.server.internal.CommonByteBufUtil.makeBinaryMessage;
import static cc.blynk.server.internal.CommonByteBufUtil.noData;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class LoadProfileGzippedLogic {

    private static final Logger log = LogManager.getLogger(LoadProfileGzippedLogic.class);

    private final UserDao userDao;
    private final DBManager dbManager;
    private final BlockingIOProcessor blockingIOProcessor;

    public LoadProfileGzippedLogic(Holder holder) {
        this.userDao = holder.userDao;
        this.dbManager = holder.dbManager;
        this.blockingIOProcessor = holder.blockingIOProcessor;
    }

    public void messageReceived(ChannelHandlerContext ctx, AppStateHolder state, StringMessage message) {
        //load all
        String email = state.user.email;
        int msgId = message.id;

        if (message.body.length() == 0) {
            Profile profile = state.user.profile;
            write(ctx, gzipProfile(profile), msgId);
            return;
        }

        String[] parts = message.body.split(StringUtils.BODY_SEPARATOR_STRING);
        if (parts.length == 1) {
            //load specific by id
            int dashId = Integer.parseInt(message.body);
            DashBoard dash = state.user.profile.getDashByIdOrThrow(dashId);
            write(ctx, gzipDash(dash), msgId);
        } else {
            String token = parts[0];
            int dashId = Integer.parseInt(parts[1]);
            String publishingEmail = parts[2];
            //this is for simplification of testing.
            String appName = parts.length == 4 ? parts[3] : state.userKey.appName;

            blockingIOProcessor.executeDB(() -> {
                try {
                    FlashedToken flashedToken = dbManager.selectFlashedToken(token);
                    if (flashedToken != null) {
                        User publishingUser = userDao.getByName(publishingEmail, appName);
                        DashBoard dash = publishingUser.profile.getDashByIdOrThrow(dashId);
                        //todo ugly. but ok for now
                        String copyString = JsonParser.toJsonRestrictiveDashboard(dash);
                        DashBoard copyDash = JsonParser.parseDashboard(copyString, msgId);
                        copyDash.eraseValues();
                        write(ctx, gzipDashRestrictive(copyDash), msgId);
                    }
                } catch (Exception e) {
                    ctx.writeAndFlush(illegalCommand(msgId), ctx.voidPromise());
                    log.error("Error getting publishing profile.", e.getMessage());
                }
            });
        }
    }

    public static void write(ChannelHandlerContext ctx, byte[] data, int msgId) {
        if (ctx.channel().isWritable()) {
            MessageBase outputMsg = makeResponse(data, msgId);
            ctx.writeAndFlush(outputMsg, ctx.voidPromise());
        }
    }

    private static MessageBase makeResponse(byte[] data, int msgId) {
        if (data == null) {
            return noData(msgId);
        }
        return makeBinaryMessage(LOAD_PROFILE_GZIPPED, msgId, data);
    }

}
