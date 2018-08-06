package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.Holder;
import cc.blynk.server.TextHolder;
import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.App;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.enums.ProvisionType;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.db.DBManager;
import cc.blynk.server.db.model.FlashedToken;
import cc.blynk.server.notifications.mail.MailWrapper;
import cc.blynk.server.notifications.mail.QrHolder;
import cc.blynk.utils.StringUtils;
import cc.blynk.utils.TokenGeneratorUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.glxn.qrgen.core.image.ImageType;
import net.glxn.qrgen.javase.QRCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.internal.CommonByteBufUtil.illegalCommandBody;
import static cc.blynk.server.internal.CommonByteBufUtil.notificationError;
import static cc.blynk.server.internal.CommonByteBufUtil.ok;

/**
 * Sends email from application.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public class MailQRsLogic {

    private static final Logger log = LogManager.getLogger(MailQRsLogic.class);
    private final TextHolder textHolder;

    private final BlockingIOProcessor blockingIOProcessor;
    private final MailWrapper mailWrapper;
    private final DBManager dbManager;

    public MailQRsLogic(Holder holder) {
        this.blockingIOProcessor = holder.blockingIOProcessor;
        this.mailWrapper =  holder.mailWrapper;
        this.dbManager = holder.dbManager;
        this.textHolder = holder.textHolder;
    }

    public void messageReceived(ChannelHandlerContext ctx, User user, StringMessage message) {
        String[] split = message.body.split(StringUtils.BODY_SEPARATOR_STRING);

        int dashId = Integer.parseInt(split[0]);
        DashBoard dash = user.profile.getDashByIdOrThrow(dashId);

        String appId = split[1];
        App app = user.profile.getAppById(appId);

        if (app == null) {
            log.debug("App with passed id not found.");
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }

        if (app.provisionType == ProvisionType.STATIC && dash.devices.length == 0) {
            log.debug("No devices in project.");
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }

        log.debug("Sending app preview email to {}, provision type {}", user.email, app.provisionType);
        makePublishPreviewEmail(ctx, dash, app.provisionType, user.email, app.name, appId, message.id);
    }

    private void makePublishPreviewEmail(ChannelHandlerContext ctx, DashBoard dash,
                                         ProvisionType provisionType, String to,
                                         String publishAppName, String publishAppId, int msgId) {
        String subj = publishAppName + " - App details";
        Channel channel = ctx.channel();
        if (provisionType == ProvisionType.DYNAMIC) {
            blockingIOProcessor.execute(() -> {
                try {
                    String newToken = TokenGeneratorUtil.generateNewToken();
                    QrHolder qrHolder = new QrHolder(dash.id, -1, null, newToken,
                                QRCode.from(newToken).to(ImageType.JPG).stream().toByteArray());
                    FlashedToken flashedToken = new FlashedToken(to, newToken, publishAppId, dash.id, -1);

                    if (!dbManager.insertFlashedTokens(flashedToken)) {
                        throw new Exception("App Publishing Preview requires enabled DB.");
                    }

                    String finalBody = textHolder.dynamicMailBody
                            .replace("{project_name}", dash.name);

                    mailWrapper.sendWithAttachment(to, subj, finalBody, qrHolder);
                    channel.writeAndFlush(ok(msgId), channel.voidPromise());
                } catch (Exception e) {
                    log.error("Error sending dynamic email from application. For user {}. Error: ", to, e);
                    channel.writeAndFlush(notificationError(msgId), channel.voidPromise());
                }
            });
        } else {
            blockingIOProcessor.execute(() -> {
                try {
                    QrHolder[] qrHolders = makeQRs(to, publishAppId, dash);
                    StringBuilder sb = new StringBuilder();
                    for (QrHolder qrHolder : qrHolders) {
                        qrHolder.attach(sb);
                    }

                    String finalBody = textHolder.staticMailBody
                            .replace("{project_name}", dash.name)
                            .replace("{device_section}", sb.toString());

                    mailWrapper.sendWithAttachment(to, subj, finalBody, qrHolders);
                    channel.writeAndFlush(ok(msgId), channel.voidPromise());
                } catch (Exception e) {
                    log.error("Error sending static email from application. For user {}. Reason: {}", to, e);
                    channel.writeAndFlush(notificationError(msgId), channel.voidPromise());
                }
            });
        }
    }


    private QrHolder[] makeQRs(String username, String appId, DashBoard dash) throws Exception {
        int tokensCount = dash.devices.length;
        QrHolder[] qrHolders = new QrHolder[tokensCount];
        FlashedToken[] flashedTokens = new FlashedToken[tokensCount];

        int i = 0;
        for (Device device : dash.devices) {
            String newToken = TokenGeneratorUtil.generateNewToken();
            qrHolders[i] = new QrHolder(dash.id, device.id, device.name, newToken,
                    QRCode.from(newToken).to(ImageType.JPG).stream().toByteArray());
            flashedTokens[i++] = new FlashedToken(username, newToken, appId, dash.id, device.id);
        }

        if (!dbManager.insertFlashedTokens(flashedTokens)) {
            throw new Exception("App Publishing Preview requires enabled DB.");
        }

        return qrHolders;
    }
}
