package cc.blynk.integration.tcp;

import cc.blynk.integration.IntegrationBase;
import cc.blynk.integration.model.tcp.ClientPair;
import cc.blynk.integration.model.tcp.TestHardClient;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.device.Status;
import cc.blynk.server.core.model.device.Tag;
import cc.blynk.server.core.protocol.model.messages.ResponseMessage;
import cc.blynk.server.core.protocol.model.messages.common.HardwareMessage;
import cc.blynk.server.notifications.push.android.AndroidGCMMessage;
import cc.blynk.server.notifications.push.enums.Priority;
import cc.blynk.server.servers.BaseServer;
import cc.blynk.server.servers.application.AppAndHttpsServer;
import cc.blynk.server.servers.hardware.HardwareAndHttpAPIServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

import static cc.blynk.server.core.protocol.enums.Command.HARDWARE;
import static cc.blynk.server.core.protocol.enums.Response.DEVICE_NOT_IN_NETWORK;
import static cc.blynk.server.core.protocol.model.messages.MessageFactory.produce;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/2/2015.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceWorkflowTest extends IntegrationBase {

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
    public void testDeleteGraphCommandWorks() throws Exception {
        clientPair.appClient.send("getgraphdata 1-0 d 8 del");

        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(1)));
    }

    @Test
    public void testSendHardwareCommandToMultipleDevices() throws Exception {
        Device device0 = new Device(0, "My Dashboard", "UNO");
        device0.status = Status.ONLINE;
        Device device1 = new Device(1, "My Device", "ESP8266");
        device1.status = Status.OFFLINE;

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.reset();

        clientPair.appClient.send("getDevices 1");
        Device[] devices = clientPair.appClient.getDevices();
        assertNotNull(devices);
        assertEquals(2, devices.length);

        assertEqualDevice(device0, devices[0]);
        assertEqualDevice(device1, devices[1]);

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(devices[1].token);
        hardClient2.verifyResult(ok(1));
        device1.status = Status.ONLINE;

        clientPair.appClient.send("hardware 1 vw 100 100");
        clientPair.hardwareClient.verifyResult(hardware(2, "vw 100 100"));
        hardClient2.never(hardware(2, "vw 1 100"));

        clientPair.appClient.send("hardware 1-0 vw 100 101");
        clientPair.hardwareClient.verifyResult(hardware(3, "vw 100 101"));
        hardClient2.never(hardware(3, "vw 1 101"));

        clientPair.appClient.send("hardware 1-1 vw 100 102");
        hardClient2.verifyResult(hardware(4, "vw 100 102"));
        clientPair.hardwareClient.never(hardware(4, "vw 100 102"));
    }

    @Test
    public void testDeviceWentOfflineMessage() throws Exception {
        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(device.token);
        hardClient2.verifyResult(ok(1));

        hardClient2.stop().await();

        ArgumentCaptor<AndroidGCMMessage> objectArgumentCaptor = ArgumentCaptor.forClass(AndroidGCMMessage.class);
        verify(gcmWrapper, timeout(500).times(1)).send(objectArgumentCaptor.capture(), any(), any());
        AndroidGCMMessage message = objectArgumentCaptor.getValue();

        String expectedJson = new AndroidGCMMessage("token", Priority.normal, "Your My Device went offline.", 1).toJson();
        assertEquals(expectedJson, message.toJson());
    }

    @Test
    public void testSendHardwareCommandToAppFromMultipleDevices() throws Exception {
        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.reset();

        clientPair.appClient.send("getDevices 1");
        Device[] devices = clientPair.appClient.getDevices();

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(devices[1].token);
        hardClient2.verifyResult(ok(1));
        clientPair.appClient.verifyResult(hardwareConnected(1, "1-1"));

        clientPair.hardwareClient.send("hardware vw 100 101");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(1, b("1-0 vw 100 101"))));

        hardClient2.send("hardware vw 100 100");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(2, b("1-1 vw 100 100"))));
    }

    @Test
    public void testSendDeviceSpecificPMMessage() throws Exception {
        clientPair.appClient.createWidget(1, "{\"id\":188, \"width\":1, \"height\":1, \"deviceId\":1, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"BUTTON\", \"pinType\":\"DIGITAL\", \"pin\":1}");
        clientPair.appClient.verifyResult(ok(1));

        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice(2);
        assertNotNull(device);
        assertNotNull(device.token);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createDevice(2, device)));

        TestHardClient hardClient = new TestHardClient("localhost", tcpHardPort);
        hardClient.start();

        hardClient.login(device.token);
        hardClient.verifyResult(ok(1));
        clientPair.appClient.verifyResult(hardwareConnected(1, "1-1"));

        String expectedBody = "pm 1 out";
        verify(hardClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE, b(expectedBody))));
        verify(hardClient.responseMock, times(2)).channelRead(any(), any());
        hardClient.stop().awaitUninterruptibly();
    }

    @Test
    public void testSendPMOnActivateForMultiDevices() throws Exception {
        clientPair.appClient.createWidget(1, "{\"id\":188, \"width\":1, \"height\":1, \"deviceId\":1, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"BUTTON\", \"pinType\":\"DIGITAL\", \"pin\":33}");
        clientPair.appClient.verifyResult(ok(1));

        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice(2);
        assertNotNull(device);
        assertNotNull(device.token);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createDevice(2, device)));

        TestHardClient hardClient = new TestHardClient("localhost", tcpHardPort);
        hardClient.start();

        hardClient.login(device.token);
        hardClient.verifyResult(ok(1));
        clientPair.appClient.verifyResult(hardwareConnected(1, "1-1"));

        verify(hardClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE, b("pm 33 out"))));
        verify(hardClient.responseMock, times(2)).channelRead(any(), any());

        clientPair.appClient.deactivate(1);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(ok(3)));

        hardClient.reset();
        clientPair.hardwareClient.reset();

        clientPair.appClient.activate(1);
        clientPair.appClient.verifyResult(ok(4));

        verify(hardClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE, b("pm 33 out"))));
        verify(hardClient.responseMock, times(1)).channelRead(any(), any());

        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE, b("pm 1 out 2 out 3 out 5 out 6 in 7 in 30 in 8 in"))));
        verify(clientPair.hardwareClient.responseMock, times(1)).channelRead(any(), any());


        hardClient.stop().awaitUninterruptibly();
    }

    @Test
    public void testActivateForMultiDevices() throws Exception {
        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.deactivate(1);
        clientPair.appClient.verifyResult(ok(2));

        clientPair.appClient.activate(1);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(ok(3)));

        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, DEVICE_NOT_IN_NETWORK)));
    }

    @Test
    public void testTagWorks() throws Exception {
        Tag tag = new Tag(100_000, "My New Tag");
        tag.deviceIds = new int[] {1};

        clientPair.appClient.createTag(1, tag);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(1, tag)));

        clientPair.appClient.createWidget(1, "{\"id\":188, \"width\":1, \"height\":1, \"deviceId\":100000, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"BUTTON\", \"pinType\":\"DIGITAL\", \"pin\":33, \"value\":1}");
        clientPair.appClient.verifyResult(ok(2));

        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice(3);
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(3, device));

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(device.token);
        hardClient2.verifyResult(ok(1));

        clientPair.appClient.reset();

        clientPair.appClient.send("hardware 1-100000 dw 33 1");
        verify(clientPair.hardwareClient.responseMock, timeout(500).times(0)).channelRead(any(), eq(new HardwareMessage(3, b("dw 33 10"))));
        verify(hardClient2.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(1, b("dw 33 1"))));

        tag.deviceIds = new int[] {0, 1};

        clientPair.appClient.updateTag(1, tag);
        clientPair.appClient.verifyResult(ok(2));

        clientPair.appClient.send("hardware 1-100000 dw 33 10");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(3, b("dw 33 10"))));
        verify(hardClient2.responseMock, timeout(500)).channelRead(any(), eq(new HardwareMessage(3, b("dw 33 10"))));
    }

    @Test
    public void testActivateAndGetSyncForMultiDevices() throws Exception {
        clientPair.appClient.createWidget(1, "{\"id\":188, \"width\":1, \"height\":1, \"deviceId\":1, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"BUTTON\", \"pinType\":\"DIGITAL\", \"pin\":33, \"value\":1}");
        clientPair.appClient.verifyResult(ok(1));

        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice(2);

        assertNotNull(device);
        assertNotNull(device.token);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createDevice(2, device)));

        clientPair.appClient.reset();
        clientPair.appClient.activate(1);

        verify(clientPair.appClient.responseMock, timeout(500).times(13)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync(b("1-0 dw 1 1")));
        clientPair.appClient.verifyResult(appSync(b("1-0 dw 2 1")));
        clientPair.appClient.verifyResult(appSync(b("1-0 aw 3 0")));
        clientPair.appClient.verifyResult(appSync(b("1-0 dw 5 1")));
        clientPair.appClient.verifyResult(appSync(b("1-0 vw 4 244")));
        clientPair.appClient.verifyResult(appSync(b("1-0 aw 7 3")));
        clientPair.appClient.verifyResult(appSync(b("1-0 aw 30 3")));
        clientPair.appClient.verifyResult(appSync(b("1-0 vw 0 89.888037459418")));
        clientPair.appClient.verifyResult(appSync(b("1-0 vw 1 -58.74774244674501")));
        clientPair.appClient.verifyResult(appSync(b("1-0 vw 13 60 143 158")));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(appSync(b("1-1 dw 33 1"))));
    }

    @Test
    public void testOfflineOnlineStatusForMultiDevices() throws Exception {
        Device device0 = new Device(0, "My Dashboard", "UNO");
        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(device.token);
        hardClient2.verifyResult(ok(1));

        device0.status = Status.ONLINE;
        device1.status = Status.ONLINE;

        clientPair.appClient.send("getDevices 1");
        Device[] devices = clientPair.appClient.getDevices(3);
        assertNotNull(devices);
        assertEquals(2, devices.length);

        assertEqualDevice(device0, devices[0]);
        assertEqualDevice(device1, devices[1]);

        hardClient2.stop().await();
        device1.status = Status.OFFLINE;

        clientPair.appClient.reset();
        clientPair.appClient.send("getDevices 1");

        devices = clientPair.appClient.getDevices();
        assertNotNull(devices);
        assertEquals(2, devices.length);

        assertEqualDevice(device0, devices[0]);
        assertEqualDevice(device1, devices[1]);
    }

    @Test
    public void testCorrectOnlineStatusForDisconnect() throws Exception {
        Device device0 = new Device(0, "My Dashboard", "UNO");
        device0.status = Status.ONLINE;

        clientPair.appClient.send("getDevices 1");
        Device[] devices = clientPair.appClient.getDevices();
        assertNotNull(devices);
        assertEquals(1, devices.length);

        assertEqualDevice(device0, devices[0]);

        clientPair.hardwareClient.stop().await();
        device0.status = Status.OFFLINE;

        clientPair.appClient.send("getDevices 1");
        devices = clientPair.appClient.getDevices(2);

        assertNotNull(devices);
        assertEquals(1, devices.length);

        assertEqualDevice(device0, devices[0]);
        assertEquals(System.currentTimeMillis(), devices[0].disconnectTime, 5000);
    }

    @Test
    public void testCorrectConnectTime() throws Exception {
        long now = System.currentTimeMillis();
        clientPair.appClient.send("getDevices 1");
        Device[] devices = clientPair.appClient.getDevices();
        assertNotNull(devices);
        assertEquals(1, devices.length);
        assertEquals(now, devices[0].connectTime, 10000);
    }

    @Test
    public void testCorrectOnlineStatusForReconnect() throws Exception {
        Device device0 = new Device(0, "My Dashboard", "UNO");
        device0.status = Status.ONLINE;

        clientPair.appClient.send("getDevices 1");
        Device[] devices = clientPair.appClient.getDevices();
        assertNotNull(devices);
        assertEquals(1, devices.length);

        assertEqualDevice(device0, devices[0]);

        clientPair.hardwareClient.stop().await();

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(devices[0].token);
        hardClient2.verifyResult(ok(1));

        clientPair.appClient.reset();

        clientPair.appClient.send("getDevices 1");
        devices = clientPair.appClient.getDevices();

        assertNotNull(devices);
        assertEquals(1, devices.length);

        assertEqualDevice(device0, devices[0]);
    }


    @Test
    public void testHardwareChannelClosedOnDashRemoval() throws Exception {
        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(device.token);
        hardClient2.verifyResult(ok(1));

        clientPair.appClient.deleteDash(1);
        clientPair.appClient.verifyResult(ok(2));

        long tries = 0;
        //waiting for channel to be closed.
        //but only limited amount if time
        while (!clientPair.hardwareClient.isClosed() && tries < 100) {
            sleep(10);
            tries++;
        }

        assertTrue(clientPair.hardwareClient.isClosed());
        assertTrue(hardClient2.isClosed());
    }

    @Test
    public void testHardwareChannelClosedOnDeviceRemoval() throws Exception {
        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(createDevice(1, device)));

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(device.token);
        hardClient2.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(hardwareConnected(1, "1-1")));

        clientPair.appClient.send("deleteDevice 1\0" + "1");
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(2)));

        assertFalse(clientPair.hardwareClient.isClosed());
        assertTrue(hardClient2.isClosed());
    }

    @Test
    public void testTemporaryTokenWorksAsExpected() throws Exception {
        Device device1 = new Device(1, "My Device", "ESP8266");

        clientPair.appClient.send("getProvisionToken 1\0" + device1.toString());
        device1 = clientPair.appClient.getDevice(1);
        assertNotNull(device1);
        assertEquals(1, device1.id);
        assertEquals(32, device1.token.length());

        clientPair.appClient.send("loadProfileGzipped 1");
        DashBoard dash = clientPair.appClient.getDash(2);
        assertNotNull(dash);
        assertEquals(1, dash.devices.length);

        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(device1.token);
        hardClient2.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(hardwareConnected(1, "1-1")));

        clientPair.appClient.send("loadProfileGzipped 1");
        dash = clientPair.appClient.getDash(4);
        assertNotNull(dash);
        assertEquals(2, dash.devices.length);

        clientPair.appClient.reset();

        hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient2.login(device1.token);
        hardClient2.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(hardwareConnected(1, "1-1")));

        clientPair.appClient.send("loadProfileGzipped 1");
        dash = clientPair.appClient.getDash(2);
        assertNotNull(dash);
        assertEquals(2, dash.devices.length);
    }


    private static void assertEqualDevice(Device expected, Device real) {
        assertEquals(expected.id, real.id);
        //assertEquals(expected.name, real.name);
        assertEquals(expected.boardType, real.boardType);
        assertNotNull(real.token);
        assertEquals(expected.status, real.status);
    }

}
