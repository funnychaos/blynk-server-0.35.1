package cc.blynk.integration.tcp;

import cc.blynk.integration.IntegrationBase;
import cc.blynk.integration.model.tcp.ClientPair;
import cc.blynk.integration.model.tcp.TestAppClient;
import cc.blynk.integration.model.tcp.TestHardClient;
import cc.blynk.server.core.model.Profile;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.widgets.notifications.Notification;
import cc.blynk.server.notifications.push.android.AndroidGCMMessage;
import cc.blynk.server.notifications.push.enums.Priority;
import cc.blynk.server.servers.BaseServer;
import cc.blynk.server.servers.application.AppAndHttpsServer;
import cc.blynk.server.servers.hardware.HardwareAndHttpAPIServer;
import io.netty.channel.ChannelFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/2/2015.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class NotificationsLogicTest extends IntegrationBase {

    private BaseServer appServer;
    private BaseServer hardwareServer;
    private ClientPair clientPair;

    @Before
    public void init() throws Exception {
        this.hardwareServer = new HardwareAndHttpAPIServer(holder).start();
        this.appServer = new AppAndHttpsServer(holder).start();

        this.clientPair = initAppAndHardPair();
    }

    @After
    public void shutdown() {
        this.appServer.close();
        this.hardwareServer.close();
        this.clientPair.stop();
    }

    @Test
    public void addPushTokenWrongInput()  throws Exception  {
        TestAppClient appClient = new TestAppClient("localhost", tcpAppPort, properties);

        appClient.start();

        appClient.register("test@test.com", "1");
        appClient.verifyResult(ok(1));

        appClient.login("test@test.com", "1", "Android", "RC13");
        appClient.verifyResult(ok(2));

        appClient.createDash("{\"id\":1, \"createdAt\":1, \"name\":\"test board\"}");
        appClient.verifyResult(ok(3));

        appClient.send("addPushToken 1\0uid\0token");
        verify(appClient.responseMock, timeout(500)).channelRead(any(), eq(notAllowed(4)));
    }

    @Test
    public void addPushTokenWorksForAndroid() throws Exception {
        clientPair.appClient.send("addPushToken 1\0uid1\0token1");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = clientPair.appClient.getProfile(2);

        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        assertNotNull(notification);
        assertEquals(2, notification.androidTokens.size());
        assertEquals(0, notification.iOSTokens.size());

        assertTrue(notification.androidTokens.containsKey("uid1"));
        assertTrue(notification.androidTokens.containsValue("token1"));
    }

    @Test
    public void addPushTokenNotOverridedOnProfileSave() throws Exception {
        clientPair.appClient.send("addPushToken 1\0uid1\0token1");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = clientPair.appClient.getProfile(2);

        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        assertNotNull(notification);
        assertEquals(2, notification.androidTokens.size());
        assertEquals(0, notification.iOSTokens.size());

        assertTrue(notification.androidTokens.containsKey("uid1"));
        assertTrue(notification.androidTokens.containsValue("token1"));

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(3));

        clientPair.appClient.send("loadProfileGzipped");
        profile = clientPair.appClient.getProfile(4);

        notification = profile.getDashById(1).getWidgetByType(Notification.class);
        assertNotNull(notification);
        assertEquals(2, notification.androidTokens.size());
        assertEquals(0, notification.iOSTokens.size());

        assertTrue(notification.androidTokens.containsKey("uid1"));
        assertTrue(notification.androidTokens.containsValue("token1"));
    }

    @Test
    public void addPushTokenWorksForIos() throws Exception {
        TestAppClient appClient = new TestAppClient("localhost", tcpAppPort, properties);

        appClient.start();

        appClient.login(DEFAULT_TEST_USER, "1", "iOS", "1.10.2");
        appClient.verifyResult(ok(1));

        appClient.send("addPushToken 1\0uid2\0token2");
        appClient.verifyResult(ok(2));

        appClient.reset();

        appClient.send("loadProfileGzipped");
        Profile profile = appClient.getProfile();

        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        assertNotNull(notification);
        assertEquals(1, notification.androidTokens.size());
        assertEquals(1, notification.iOSTokens.size());
        Map.Entry<String, String> entry = notification.iOSTokens.entrySet().iterator().next();
        assertEquals("uid2", entry.getKey());
        assertEquals("token2", entry.getValue());
    }

    @Test
    public void testHardwareDeviceWentOffline() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = false;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.stop();
        clientPair.appClient.verifyResult(deviceOffline(0, "1-0"));
    }

    @Test
    public void testHardwareDeviceWentOfflineForSecondDeviceSameToken() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = false;
        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        clientPair.appClient.getToken(1);
        String token = clientPair.appClient.getBody();

        TestHardClient newHardClient = new TestHardClient("localhost", tcpHardPort);
        newHardClient.start();
        newHardClient.login(token);
        verify(newHardClient.responseMock, timeout(500)).channelRead(any(), eq(ok(1)));

        newHardClient.stop();
        verify(clientPair.appClient.responseMock, timeout(1500)).channelRead(any(), eq(deviceOffline(0, "1-0")));
    }

    @Test
    public void testHardwareDeviceWentOfflineForSecondDeviceNewToken() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = false;
        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.reset();

        Device device1 = new Device(1, "Name", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.getToken(1, 1);
        String token = clientPair.appClient.getBody(2);

        TestHardClient newHardClient = new TestHardClient("localhost", tcpHardPort);
        newHardClient.start();
        newHardClient.login(token);
        newHardClient.verifyResult(ok(1));
        clientPair.appClient.verifyResult(hardwareConnected(1, "1-1"));

        newHardClient.stop();
        clientPair.appClient.verifyResult(deviceOffline(0, "1-1"));
    }

    @Test
    public void testHardwareDeviceWentOfflineAndPushWorks() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));

        ChannelFuture channelFuture = clientPair.hardwareClient.stop();
        channelFuture.await();

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, timeout(500).times(1)).send(objectArgumentCaptor.capture(), any(), any());
        AndroidGCMMessage message = objectArgumentCaptor.getValue();

        String expectedJson = new AndroidGCMMessage("token", Priority.normal, "Your My Device went offline.", 1).toJson();
        assertEquals(expectedJson, message.toJson());
    }

    @Test
    public void testHardwareDeviceWentOfflineAndPushNotWorksForLogoutUser() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.send("logout");
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.verifyResult(ok(2));

        ChannelFuture channelFuture = clientPair.hardwareClient.stop();
        channelFuture.await();

        verify(gcmWrapper, after(500).never()).send(any(), any(), any());

        clientPair.appClient.send("logout");
        verify(clientPair.appClient.responseMock, after(500).never()).channelRead(any(), eq(ok(3)));
    }

    @Test
    public void testHardwareDeviceWentOfflineAndPushNotWorksForLogoutUserWithUID() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.send("logout uid");
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.verifyResult(ok(2));

        ChannelFuture channelFuture = clientPair.hardwareClient.stop();
        channelFuture.await();

        verify(gcmWrapper, after(500).never()).send(any(), any(), any());

        clientPair.appClient.send("logout");
        verify(clientPair.appClient.responseMock, after(500).never()).channelRead(any(), eq(ok(3)));
    }

    @Test
    public void testHardwareDeviceWentOfflineAndPushNotWorksForLogoutUserWithWrongUID() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.send("logout uidxxx");
        clientPair.appClient.verifyResult(ok(1));
        clientPair.appClient.verifyResult(ok(2));

        ChannelFuture channelFuture = clientPair.hardwareClient.stop();
        channelFuture.await();

        verify(gcmWrapper, timeout(500)).send(any(), any(), eq("uid"));
    }

    @Test
    public void testHardwareDeviceWentOfflineAndPushNotWorksForLogoutUser2() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.getToken(1);
        String token = clientPair.appClient.getBody(2);

        clientPair.appClient.send("logout");
        clientPair.appClient.verifyResult(ok(3));

        clientPair.hardwareClient.stop().await();

        verify(gcmWrapper, after(500).never()).send(any(), any(), any());

        TestAppClient appClient = new TestAppClient("localhost", tcpAppPort, properties);
        appClient.start();
        appClient.login("dima@mail.ua", "1", "Android", "1.10.4");
        appClient.verifyResult(ok(1));

        TestHardClient hardClient = new TestHardClient("localhost", tcpHardPort);
        hardClient.start();

        hardClient.login(token);
        hardClient.verifyResult(ok(1));

        appClient.send("addPushToken 1\0uid\0token");
        appClient.verifyResult(ok(2));

        hardClient.stop().await();

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, timeout(500).times(1)).send(objectArgumentCaptor.capture(), any(), eq("uid"));
        AndroidGCMMessage message = objectArgumentCaptor.getValue();

        String expectedJson = new AndroidGCMMessage("token", Priority.normal, "Your My Device went offline.", 1).toJson();
        assertEquals(expectedJson, message.toJson());
    }

    @Test
    public void testLoginWith2AppsAndLogoutFrom1() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.getToken(1);
        String token = clientPair.appClient.getBody(2);

        TestAppClient appClient = new TestAppClient("localhost", tcpAppPort, properties);
        appClient.start();
        appClient.login("dima@mail.ua", "1", "Android", "1.10.4");
        appClient.verifyResult(ok(1));

        appClient.send("addPushToken 1\0uid2\0token2");
        appClient.verifyResult(ok(2));

        clientPair.appClient.send("logout uid");
        clientPair.appClient.verifyResult(ok(3));

        clientPair.hardwareClient.stop().await();

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, timeout(500).times(1)).send(objectArgumentCaptor.capture(), any(), eq("uid2"));
        AndroidGCMMessage message = objectArgumentCaptor.getValue();

        String expectedJson = new AndroidGCMMessage("token2", Priority.normal, "Your My Device went offline.", 1).toJson();
        assertEquals(expectedJson, message.toJson());
    }

    @Test
    public void testLoginWith2AppsAndLogoutFrom2() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.getToken(1);
        String token = clientPair.appClient.getBody(2);

        TestAppClient appClient = new TestAppClient("localhost", tcpAppPort, properties);
        appClient.start();
        appClient.login("dima@mail.ua", "1", "Android", "1.10.4");
        appClient.verifyResult(ok(1));

        appClient.send("addPushToken 1\0uid2\0token2");
        appClient.verifyResult(ok(2));

        clientPair.appClient.send("logout");
        clientPair.appClient.verifyResult(ok(3));

        clientPair.hardwareClient.stop().await();

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, after(500).never()).send(objectArgumentCaptor.capture(), any(), eq("uid2"));
    }

    @Test
    public void testLoginWithSharedAppAndLogoutFrom() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.reset();
        clientPair.appClient.send("getShareToken 1");

        String token = clientPair.appClient.getBody();

        TestAppClient appClient = new TestAppClient("localhost", tcpAppPort, properties);
        appClient.start();
        appClient.send("shareLogin " + "dima@mail.ua " + token + " Android 24");

        appClient.send("addPushToken 1\0uid2\0token2");
        appClient.verifyResult(ok(2));

        appClient.send("logout uid2");
        appClient.verifyResult(ok(3));

        clientPair.hardwareClient.stop().await();

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, after(500).never()).send(objectArgumentCaptor.capture(), any(), eq("uid2"));
    }

    @Test
    public void testHardwareDeviceWentOfflineAndPushDelayedWorks() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;
        notification.notifyWhenOfflineIgnorePeriod = 1000;

        long now = System.currentTimeMillis();

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.stop();

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);

        verify(gcmWrapper, timeout(2000).times(1)).send(objectArgumentCaptor.capture(), any(), any());
        AndroidGCMMessage message = objectArgumentCaptor.getValue();
        assertTrue(System.currentTimeMillis() - now > notification.notifyWhenOfflineIgnorePeriod );

        String expectedJson = new AndroidGCMMessage("token", Priority.normal, "Your My Device went offline.", 1).toJson();
        assertEquals(expectedJson, message.toJson());
    }

    @Test
    public void testHardwareDeviceWentOfflineAndPushDelayedNotTriggeredDueToReconnect() throws Exception {
        Profile profile = parseProfile(readTestUserProfile());
        Notification notification = profile.getDashById(1).getWidgetByType(Notification.class);
        notification.notifyWhenOffline = true;
        notification.notifyWhenOfflineIgnorePeriod = 1000;

        clientPair.appClient.updateDash(profile.getDashById(1));
        clientPair.appClient.verifyResult(ok(1));

        ChannelFuture channelFuture = clientPair.hardwareClient.stop();
        channelFuture.await();


        clientPair.appClient.getToken(1);
        String token = clientPair.appClient.getBody(2);

        TestHardClient newHardClient = new TestHardClient("localhost", tcpHardPort);
        newHardClient.start();
        newHardClient.login(token);
        newHardClient.verifyResult(ok(1));

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, after(1500).never()).send(objectArgumentCaptor.capture(), any(), any());
    }

    @Test
    public void testCreateNewNotificationWidget() throws Exception  {
        clientPair.appClient.deleteWidget(1, 9);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.createWidget(1, "{\"id\":9, \"x\":1, \"y\":1, \"width\":1, \"height\":1, \"type\":\"NOTIFICATION\", \"notifyWhenOfflineIgnorePeriod\":0, \"priority\":\"high\", \"notifyWhenOffline\":true}");
        clientPair.appClient.verifyResult(ok(2));

        clientPair.appClient.send("addPushToken 1\0uid1\0token1");
        clientPair.appClient.verifyResult(ok(3));

        clientPair.appClient.updateWidget(1, "{\"id\":9, \"x\":1, \"y\":1, \"width\":1, \"height\":1, \"type\":\"NOTIFICATION\", \"notifyWhenOfflineIgnorePeriod\":0, \"priority\":\"high\", \"notifyWhenOffline\":false}");
        clientPair.appClient.verifyResult(ok(2));

        clientPair.hardwareClient.send("push 123");

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);

        verify(gcmWrapper, timeout(500).times(1)).send(objectArgumentCaptor.capture(), any(), any());
        AndroidGCMMessage message = objectArgumentCaptor.getValue();

        String expectedJson = new AndroidGCMMessage("token1", Priority.high, "123", 1).toJson();
        assertEquals(expectedJson, message.toJson());
    }

    @Test
    public void testPushWhenHardwareOffline() throws Exception {
        ChannelFuture channelFuture = clientPair.hardwareClient.stop();
        channelFuture.await();

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, timeout(500).times(1)).send(objectArgumentCaptor.capture(), any(), any());
        AndroidGCMMessage message = objectArgumentCaptor.getValue();

        String expectedJson = new AndroidGCMMessage("token", Priority.normal, "Your My Device went offline.", 1).toJson();
        assertEquals(expectedJson, message.toJson());
    }

    @Test
    public void testPushHandler() throws Exception {
        clientPair.hardwareClient.send("push Yo!");

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, timeout(500).times(1)).send(objectArgumentCaptor.capture(), any(), any());
        AndroidGCMMessage message = objectArgumentCaptor.getValue();

        String expectedJson = new AndroidGCMMessage("token", Priority.normal, "Yo!", 1).toJson();
        assertEquals(expectedJson, message.toJson());
    }

    @Test
    public void testOfflineMessageIsSentToBothApps()  throws Exception  {
        TestAppClient appClient = new TestAppClient("localhost", tcpAppPort, properties);
        appClient.start();

        appClient.login(DEFAULT_TEST_USER, "1", "iOS", "1.10.2");
        appClient.verifyResult(ok(1));

        clientPair.appClient.deleteWidget(1, 9);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.stop();
        clientPair.appClient.verifyResult(deviceOffline(0, "1-0"));
        appClient.verifyResult(deviceOffline(0, "1-0"));
    }


}
