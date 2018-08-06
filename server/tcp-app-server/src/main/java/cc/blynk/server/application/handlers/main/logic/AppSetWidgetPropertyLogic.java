package cc.blynk.server.application.handlers.main.logic;

import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.enums.WidgetProperty;
import cc.blynk.server.core.model.widgets.Widget;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.utils.StringUtils;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static cc.blynk.server.internal.CommonByteBufUtil.illegalCommandBody;
import static cc.blynk.server.internal.CommonByteBufUtil.ok;

/**
 * Handler that allows to change widget properties from hardware side.
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
public final class AppSetWidgetPropertyLogic {

    private static final Logger log = LogManager.getLogger(AppSetWidgetPropertyLogic.class);

    private AppSetWidgetPropertyLogic(SessionDao sessionDao) {
    }

    public static void messageReceived(ChannelHandlerContext ctx, User user, StringMessage message) {
        String[] bodyParts = message.body.split(StringUtils.BODY_SEPARATOR_STRING);

        if (bodyParts.length != 4) {
            log.debug("AppSetWidgetProperty command body has wrong format. {}", message.body);
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }

        int dashId = Integer.parseInt(bodyParts[0]);
        long widgetId = Long.parseLong(bodyParts[1]);
        String property = bodyParts[2];
        String propertyValue = bodyParts[3];

        if (property.length() == 0 || propertyValue.length() == 0) {
            log.debug("AppSetWidgetProperty command body has wrong format. {}", message.body);
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }

        WidgetProperty widgetProperty = WidgetProperty.getProperty(property);

        if (widgetProperty == null) {
            log.debug("Unsupported app set property {}.", property);
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }

        DashBoard dash = user.profile.getDashByIdOrThrow(dashId);
        //for now supporting only virtual pins
        Widget widget = dash.getWidgetById(widgetId);
        if (widget == null) {
            widget = dash.getWidgetByIdInDeviceTilesOrThrow(widgetId);
        }

        try {
            widget.setProperty(widgetProperty, propertyValue);
            dash.updatedAt = System.currentTimeMillis();
        } catch (Exception e) {
            log.debug("Error setting widget property. Reason : {}", e.getMessage());
            ctx.writeAndFlush(illegalCommandBody(message.id), ctx.voidPromise());
            return;
        }
        ctx.writeAndFlush(ok(message.id), ctx.voidPromise());
    }

}
