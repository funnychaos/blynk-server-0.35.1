package cc.blynk.server.hardware.handlers;

import cc.blynk.server.core.BlockingIOProcessor;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.Profile;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.widgets.notifications.Mail;
import cc.blynk.server.core.protocol.enums.Command;
import cc.blynk.server.core.protocol.exceptions.IllegalCommandException;
import cc.blynk.server.core.protocol.exceptions.NotAllowedException;
import cc.blynk.server.core.protocol.model.messages.MessageFactory;
import cc.blynk.server.core.protocol.model.messages.StringMessage;
import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.server.hardware.handlers.hardware.logic.MailLogic;
import cc.blynk.server.notifications.mail.MailWrapper;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 07.04.15.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class MailHandlerTest {

    @Mock
    private BlockingIOProcessor blockingIOProcessor;

    @Mock
    private MailWrapper mailWrapper;

    private final MailLogic mailHandler = new MailLogic(blockingIOProcessor, mailWrapper, 1);

	@Mock
	private ChannelHandlerContext ctx;

    @Mock
    private User user;

    @Mock
    private Profile profile;

    @Mock
    private DashBoard dashBoard;

    @Mock
    private Device device;

    @Test(expected = NotAllowedException.class)
	public void testNoEmailWidget() throws InterruptedException {
        StringMessage mailMessage = (StringMessage) MessageFactory.produce(1, Command.EMAIL, "body");

        user.profile = profile;
        when(profile.getDashByIdOrThrow(1)).thenReturn(dashBoard);
        when(dashBoard.getWidgetByType(Mail.class)).thenReturn(null);

        HardwareStateHolder state = new HardwareStateHolder(user, dashBoard, device);
        mailHandler.messageReceived(ctx, state, mailMessage);
    }

    @Test(expected = IllegalCommandException.class)
	public void testNoToBody() throws InterruptedException {
        StringMessage mailMessage = (StringMessage) MessageFactory.produce(1, Command.EMAIL, "".replaceAll(" ", "\0"));

        user.profile = profile;
        when(profile.getDashByIdOrThrow(1)).thenReturn(dashBoard);
        Mail mail = new Mail();
        when(dashBoard.getWidgetByType(Mail.class)).thenReturn(mail);
        dashBoard.isActive = true;

        HardwareStateHolder state = new HardwareStateHolder(user, dashBoard, device);
        mailHandler.messageReceived(ctx, state, mailMessage);
    }

    @Test(expected = IllegalCommandException.class)
	public void testNoBody() throws InterruptedException {
        StringMessage mailMessage = (StringMessage) MessageFactory.produce(1, Command.EMAIL, "body".replaceAll(" ", "\0"));

        user.profile = profile;
        when(profile.getDashByIdOrThrow(1)).thenReturn(dashBoard);
        when(dashBoard.getWidgetByType(Mail.class)).thenReturn(new Mail());
        dashBoard.isActive = true;

        HardwareStateHolder state = new HardwareStateHolder(user, dashBoard, device);
        mailHandler.messageReceived(ctx, state, mailMessage);
    }

}
