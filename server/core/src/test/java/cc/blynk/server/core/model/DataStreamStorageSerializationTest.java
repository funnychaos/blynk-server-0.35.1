package cc.blynk.server.core.model;

import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.enums.WidgetProperty;
import cc.blynk.server.core.model.serialization.JsonParser;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 19.11.16.
 */
public class DataStreamStorageSerializationTest {

    @Test
    public void testSerialize() {
        User user = new User();
        user.email = "123";
        user.profile = new Profile();
        user.profile.dashBoards = new DashBoard[] {
                new DashBoard()
        };
        user.lastModifiedTs = 0;
        user.profile.dashBoards[0].pinsStorage = new HashMap<>();
        PinStorageKey pinStorageKey = new PinStorageKey(0, PinType.VIRTUAL, (byte) 0);
        PinStorageKey pinStorageKey2 = new PinStorageKey(0, PinType.DIGITAL, (byte) 1);
        PinPropertyStorageKey pinStorageKey3 = new PinPropertyStorageKey(0, PinType.VIRTUAL, (byte) 0, WidgetProperty.LABEL);
        user.profile.dashBoards[0].pinsStorage.put(pinStorageKey, "1");
        user.profile.dashBoards[0].pinsStorage.put(pinStorageKey2, "2");
        user.profile.dashBoards[0].pinsStorage.put(pinStorageKey3, "3");

        String result = user.toString();
        assertTrue(result.contains("0-v0"));
        assertTrue(result.contains("0-d1"));
        assertTrue(result.contains("0-v0-label"));
    }

    @Test
    public void testDeserialize() throws Exception{
        String expectedString = "{\"email\":\"123\",\"appName\":\"Blynk\",\"lastModifiedTs\":0,\"lastLoggedAt\":0,\"profile\":{\"dashBoards\":[{\"id\":0,\"createdAt\":0,\"updatedAt\":0,\"theme\":\"Blynk\",\"keepScreenOn\":false,\"isShared\":false,\"isActive\":false," +
                "\"pinsStorage\":{\"0-v0\":\"1\",\"0-d111\":\"2\", \"0-v0-label\":\"3\"}" +
                "}]},\"isFacebookUser\":false,\"energy\":2000,\"id\":\"123-Blynk\"}";

        User user = JsonParser.parseUserFromString(expectedString);
        assertNotNull(user);
        assertEquals(3, user.profile.dashBoards[0].pinsStorage.size());

        PinStorageKey pinStorageKey = new PinStorageKey(0, PinType.VIRTUAL, (byte) 0);
        PinStorageKey pinStorageKey2 = new PinStorageKey(0, PinType.DIGITAL, (byte) 111);
        PinPropertyStorageKey pinStorageKey3 = new PinPropertyStorageKey(0, PinType.VIRTUAL, (byte) 0, WidgetProperty.LABEL);

        assertEquals("1", user.profile.dashBoards[0].pinsStorage.get(pinStorageKey));
        assertEquals("2", user.profile.dashBoards[0].pinsStorage.get(pinStorageKey2));
        assertEquals("3", user.profile.dashBoards[0].pinsStorage.get(pinStorageKey3));
    }


}
