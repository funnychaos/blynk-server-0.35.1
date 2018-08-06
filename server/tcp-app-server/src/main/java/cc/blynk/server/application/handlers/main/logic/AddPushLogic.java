package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.application.handlers.main.auth.AppStateHolder;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.widgets.notifications.Notification;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.internal.CommonByteBufUtil.notAllowed;
import static cc.blynk.server.internal.CommonByteBufUtil.ok;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public final class AddPushLogic {

    private static final Logger log = LogManager.getLogger(AddPushLogic.class);

    private AddPushLogic() {
    }

    public static void messageReceived(ChannelHandlerContext ctx, AppStateHolder state, StringMessage message) {
        String[] data = StringUtils.split3(message.body);

        int dashId = Integer.parseInt(data[0]);
        String uid = data[1];
        String token = data[2];

        DashBoard dash = state.user.profile.getDashByIdOrThrow(dashId);

        Notification notification = dash.getWidgetByType(Notification.class);

        if (notification == null) {
            log.error("No notification widget.");
            ctx.writeAndFlush(notAllowed(message.id), ctx.voidPromise());
            return;
        }

        switch (state.version.osType) {
            case ANDROID :
                notification.androidTokens.put(uid, token);
                break;
            case IOS :
                notification.iOSTokens.put(uid, token);
                break;
        }

        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }
}
