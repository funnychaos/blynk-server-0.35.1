package cc.blynk.integration.tcp;

import cc.blynk.integration.IntegrationBase;
import cc.blynk.integration.model.tcp.ClientPair;
import cc.blynk.server.core.model.Profile;
import cc.blynk.server.core.model.device.Device;
import cc.blynk.server.core.model.device.Status;
import cc.blynk.server.servers.BaseServer;
import cc.blynk.server.servers.application.AppAndHttpsServer;
import cc.blynk.server.servers.hardware.HardwareAndHttpAPIServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static cc.blynk.server.core.protocol.enums.Command.GET_ENERGY;
import static cc.blynk.server.core.protocol.enums.Command.HARDWARE;
import static cc.blynk.server.core.protocol.model.messages.MessageFactory.produce;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
public class AppSyncWorkflowTest extends IntegrationBase {

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
    public void testLCDOnActivateSendsCorrectBodySimpleMode() throws Exception {
        clientPair.appClient.createWidget(1, "{\"type\":\"LCD\",\"id\":1923810267,\"x\":0,\"y\":6,\"color\":600084223,\"width\":8,\"height\":2,\"tabId\":0,\"" +
                "pins\":[" +
                "{\"pin\":10,\"pinType\":\"VIRTUAL\",\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":1023, \"value\":\"10\"}," +
                "{\"pin\":11,\"pinType\":\"VIRTUAL\",\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":1023, \"value\":\"11\"}]," +
                "\"advancedMode\":false,\"textLight\":false,\"textLightOn\":false,\"frequency\":1000}");

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.reset();
        clientPair.appClient.sync(1);

        verify(clientPair.appClient.responseMock, timeout(500).times(13)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 vw 10 10"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 11 11"));


        clientPair.appClient.verifyResult(appSync("1-0 dw 1 1"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 2 1"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 3 0"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 5 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 4 244"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 7 3"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 30 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 0 89.888037459418"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 1 -58.74774244674501"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 13 60 143 158"));
    }

    @Test
    public void testLCDOnActivateSendsCorrectBodyAdvancedMode() throws Exception {
        clientPair.appClient.createWidget(1, "{\"type\":\"LCD\",\"id\":1923810267,\"x\":0,\"y\":6,\"color\":600084223,\"width\":8,\"height\":2,\"tabId\":0,\"" +
                "pins\":[" +
                "{\"pin\":10,\"pinType\":\"VIRTUAL\",\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":1023}," +
                "{\"pin\":11,\"pinType\":\"VIRTUAL\",\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":1023}]," +
                "\"advancedMode\":true,\"textLight\":false,\"textLightOn\":false,\"frequency\":1000}");

        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.send("hardware vw 10 p x y 10");
        clientPair.appClient.verifyResult(hardware(1, "1-0 vw 10 p x y 10"));

        clientPair.appClient.reset();
        clientPair.appClient.sync(1);

        verify(clientPair.appClient.responseMock, timeout(500).times(12)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync(1111, "1-0 vw 10 p x y 10"));


        clientPair.appClient.verifyResult(appSync("1-0 dw 1 1"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 2 1"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 3 0"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 5 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 4 244"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 7 3"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 30 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 0 89.888037459418"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 1 -58.74774244674501"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 13 60 143 158"));
    }


    @Test
    public void testTerminalSendsSyncOnActivate() throws Exception {
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = clientPair.appClient.getProfile();
        assertEquals(16, profile.dashBoards[0].widgets.length);

        clientPair.appClient.send("getEnergy");
        clientPair.appClient.verifyResult(produce(2, GET_ENERGY, "7500"));

        clientPair.appClient.createWidget(1, "{\"id\":102, \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"label\":\"Some Text\", \"type\":\"TERMINAL\", \"pinType\":\"VIRTUAL\", \"pin\":17}");
        clientPair.appClient.verifyResult(ok(3));

        clientPair.hardwareClient.send("hardware vw 17 a");
        clientPair.hardwareClient.send("hardware vw 17 b");
        clientPair.hardwareClient.send("hardware vw 17 c");
        clientPair.appClient.verifyResult(hardware(1, "1-0 vw 17 a"));
        clientPair.appClient.verifyResult(hardware(2, "1-0 vw 17 b"));
        clientPair.appClient.verifyResult(hardware(3, "1-0 vw 17 c"));

        clientPair.appClient.deactivate(1);
        clientPair.appClient.verifyResult(ok(4));

        clientPair.appClient.activate(1);
        clientPair.appClient.verifyResult(ok(5));
        clientPair.appClient.verifyResult(appSync("1-0 vw 17 a"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 17 b"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 17 c"));
    }

    @Test
    public void testTerminalStorageRemembersCommands() throws Exception {
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = clientPair.appClient.getProfile();
        assertEquals(16, profile.dashBoards[0].widgets.length);

        clientPair.appClient.send("getEnergy");
        clientPair.appClient.verifyResult(produce(2, GET_ENERGY, "7500"));

        clientPair.appClient.createWidget(1, "{\"id\":102, \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"label\":\"Some Text\", \"type\":\"TERMINAL\", \"pinType\":\"VIRTUAL\", \"pin\":17}");
        clientPair.appClient.verifyResult(ok(3));

        clientPair.hardwareClient.send("hardware vw 17 1");
        clientPair.hardwareClient.send("hardware vw 17 2");
        clientPair.hardwareClient.send("hardware vw 17 3");
        clientPair.hardwareClient.send("hardware vw 17 4");
        clientPair.hardwareClient.send("hardware vw 17 dddyyyiii");
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(hardware(5, "1-0 vw 17 dddyyyiii")));

        clientPair.appClient.activate(1);
        clientPair.appClient.verifyResult(appSync("1-0 vw 17 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 17 2"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 17 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 17 4"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 17 dddyyyiii"));
    }

    @Test
    public void testLCDSendsSyncOnActivate() throws Exception {
        clientPair.hardwareClient.send("hardware vw 20 p 0 0 Hello");
        clientPair.hardwareClient.send("hardware vw 20 p 0 1 World");

        clientPair.appClient.verifyResult(produce(1, HARDWARE, b("1-0 vw 20 p 0 0 Hello")));
        clientPair.appClient.verifyResult(produce(2, HARDWARE, b("1-0 vw 20 p 0 1 World")));

        clientPair.appClient.sync(1);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 vw 20 p 0 0 Hello"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 20 p 0 1 World"));
    }

    @Test
    public void testLCDSendsSyncOnActivate2() throws Exception {
        clientPair.hardwareClient.send("hardware vw 20 p 0 0 H1");
        clientPair.hardwareClient.send("hardware vw 20 p 0 1 H2");
        clientPair.hardwareClient.send("hardware vw 20 p 0 2 H3");
        clientPair.hardwareClient.send("hardware vw 20 p 0 3 H4");
        clientPair.hardwareClient.send("hardware vw 20 p 0 4 H5");
        clientPair.hardwareClient.send("hardware vw 20 p 0 5 H6");
        clientPair.hardwareClient.send("hardware vw 20 p 0 6 H7");

        clientPair.appClient.verifyResult(hardware(1, "1-0 vw 20 p 0 0 H1"));
        clientPair.appClient.verifyResult(hardware(2, "1-0 vw 20 p 0 1 H2"));
        clientPair.appClient.verifyResult(hardware(3, "1-0 vw 20 p 0 2 H3"));
        clientPair.appClient.verifyResult(hardware(4, "1-0 vw 20 p 0 3 H4"));
        clientPair.appClient.verifyResult(hardware(5, "1-0 vw 20 p 0 4 H5"));
        clientPair.appClient.verifyResult(hardware(6, "1-0 vw 20 p 0 5 H6"));
        clientPair.appClient.verifyResult(hardware(7, "1-0 vw 20 p 0 6 H7"));

        clientPair.appClient.sync(1);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 vw 20 p 0 1 H2"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 20 p 0 2 H3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 20 p 0 3 H4"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 20 p 0 4 H5"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 20 p 0 5 H6"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 20 p 0 6 H7"));
    }

    @Test
    public void testActivateAndGetSync() throws Exception {
        clientPair.appClient.sync(1);

        verify(clientPair.appClient.responseMock, timeout(500).times(11)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 dw 1 1"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 2 1"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 3 0"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 5 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 4 244"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 7 3"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 30 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 0 89.888037459418"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 1 -58.74774244674501"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 13 60 143 158"));
    }

    @Test
    //https://github.com/blynkkk/blynk-server/issues/443
    public void testSyncWidgetValueOverlapsWithPinStorage() throws Exception {
        clientPair.hardwareClient.send("hardware vw 125 1");
        clientPair.appClient.verifyResult(hardware(1, "1-0 vw 125 1"));
        clientPair.appClient.reset();

        clientPair.appClient.sync(1);

        verify(clientPair.appClient.responseMock, timeout(500).times(12)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 dw 1 1"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 2 1"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 3 0"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 5 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 4 244"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 7 3"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 30 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 0 89.888037459418"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 1 -58.74774244674501"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 13 60 143 158"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 125 1"));


        clientPair.appClient.createWidget(1, "{\"id\":88, \"width\":1, \"height\":1, \"deviceId\":0, \"x\":0, \"y\":0, \"label\":\"Button\", \"type\":\"BUTTON\", \"pinType\":\"VIRTUAL\", \"pin\":125}");
        clientPair.appClient.verifyResult(ok(2));
        clientPair.appClient.reset();

        clientPair.hardwareClient.send("hardware vw 125 2");
        clientPair.appClient.verifyResult(hardware(2, "1-0 vw 125 2"));
        clientPair.appClient.reset();

        clientPair.appClient.sync(1);

        verify(clientPair.appClient.responseMock, timeout(500).times(12)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 dw 1 1"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 2 1"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 3 0"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 5 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 4 244"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 7 3"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 30 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 0 89.888037459418"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 1 -58.74774244674501"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 13 60 143 158"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 125 2"));
    }

    @Test
    public void testActivateAndGetSyncForSpecificDeviceId() throws Exception {
        clientPair.appClient.sync(1, 0);

        verify(clientPair.appClient.responseMock, timeout(500).times(11)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 dw 1 1"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 2 1"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 3 0"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 5 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 4 244"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 7 3"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 30 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 0 89.888037459418"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 1 -58.74774244674501"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 13 60 143 158"));
    }

    @Test
    public void testSyncForDeviceSelectorAndSetProperty() throws Exception {
        Device device0 = new Device(0, "My Dashboard", "UNO");
        device0.status = Status.ONLINE;
        Device device1 = new Device(1, "My Device", "ESP8266");
        device1.status = Status.OFFLINE;

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.createWidget(1, "{\"id\":200000, \"width\":1, \"height\":1, \"value\":1, \"x\":0, \"y\":0, \"label\":\"Some Text\", \"type\":\"DEVICE_SELECTOR\"}");
        clientPair.appClient.createWidget(1, "{\"id\":88, \"width\":1, \"height\":1, \"deviceId\":200000, \"x\":0, \"y\":0, \"label\":\"Button\", \"type\":\"BUTTON\", \"pinType\":\"VIRTUAL\", \"pin\":88}");
        clientPair.appClient.verifyResult(ok(2));
        clientPair.appClient.verifyResult(ok(3));

        clientPair.hardwareClient.setProperty(88, "label", "newLabel");
        clientPair.appClient.verifyResult(setProperty(1, "1-0 88 label newLabel"));

        clientPair.appClient.reset();

        clientPair.appClient.sync(1);

        verify(clientPair.appClient.responseMock, timeout(500).times(12)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 dw 1 1"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 2 1"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 3 0"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 5 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 4 244"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 7 3"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 30 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 0 89.888037459418"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 1 -58.74774244674501"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 13 60 143 158"));
        clientPair.appClient.verifyResult(setProperty(1111, "1-0 88 label newLabel"));
    }

    @Test
    public void testActivateAndGetSyncForTimeInput() throws Exception {
        clientPair.appClient.createWidget(1, "{\"type\":\"TIME_INPUT\",\"id\":99, \"pin\":99, \"pinType\":\"VIRTUAL\", " +
                "\"x\":0,\"y\":0,\"width\":1,\"height\":1}");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.send("hardware 1 vw " + "99 82800 82860 Europe/Kiev 1");
        verify(clientPair.hardwareClient.responseMock, timeout(500).times(1)).channelRead(any(), eq(hardware(2, "vw 99 82800 82860 Europe/Kiev 1")));

        clientPair.appClient.reset();

        clientPair.appClient.sync(1, 0);

        verify(clientPair.appClient.responseMock, timeout(500).times(12)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-0 dw 1 1"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 2 1"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 3 0"));
        clientPair.appClient.verifyResult(appSync("1-0 dw 5 1"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 4 244"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 7 3"));
        clientPair.appClient.verifyResult(appSync("1-0 aw 30 3"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 0 89.888037459418"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 1 -58.74774244674501"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 13 60 143 158"));
        clientPair.appClient.verifyResult(appSync("1-0 vw 99 82800 82860 Europe/Kiev 1"));
    }

    @Test
    public void testActivateAndGetSyncForNonExistingDeviceId() throws Exception {
        clientPair.appClient.sync(1, 1);

        verify(clientPair.appClient.responseMock, timeout(500).times(1)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));
    }

    @Test
    public void testLCDOnActivateSendsCorrectBodySimpleModeAndAnotherDevice() throws Exception {
        Device device1 = new Device(1, "My Device", "ESP8266");
        device1.status = Status.OFFLINE;

        clientPair.appClient.createDevice(1, device1);
        Device device = clientPair.appClient.getDevice();
        assertNotNull(device);
        assertNotNull(device.token);
        clientPair.appClient.verifyResult(createDevice(1, device));

        clientPair.appClient.createWidget(1, "{\"deviceId\":1,\"type\":\"LCD\",\"id\":1923810267,\"x\":0,\"y\":6,\"color\":600084223,\"width\":8,\"height\":2,\"tabId\":0,\"" +
                "pins\":[" +
                "{\"pin\":10,\"pinType\":\"VIRTUAL\",\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":1023, \"value\":\"10\"}," +
                "{\"pin\":11,\"pinType\":\"VIRTUAL\",\"pwmMode\":false,\"rangeMappingOn\":false,\"min\":0,\"max\":1023, \"value\":\"11\"}]," +
                "\"advancedMode\":false,\"textLight\":false,\"textLightOn\":false,\"frequency\":1000}");

        clientPair.appClient.verifyResult(ok(2));

        clientPair.appClient.reset();
        clientPair.appClient.sync(1, 1);

        verify(clientPair.appClient.responseMock, timeout(500).times(3)).channelRead(any(), any());

        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.verifyResult(appSync("1-1 vw 10 10"));
        clientPair.appClient.verifyResult(appSync("1-1 vw 11 11"));
    }

}
