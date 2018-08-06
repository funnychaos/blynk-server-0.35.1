package cc.blynk.server.core.model;

import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.enums.WidgetProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

import java.io.IOException;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 19.11.16.
 */
public class PinStorageKeyDeserializer extends KeyDeserializer {

    @Override
    public PinStorageKey deserializeKey(String key, DeserializationContext ctx) throws IOException {
        //parsing "123-v24"
        String[] split = key.split("-");

        int deviceId = Integer.parseInt(split[0]);
        PinType pinType = PinType.getPinType(split[1].charAt(0));
        byte pin = 0;
        try {
            pin = Byte.parseByte(split[1].substring(1, split[1].length()));
        } catch (NumberFormatException e) {
            //special case for outdated data format.
            return new PinStorageKey(deviceId, pinType, pin);
        }
        if (split.length == 3) {
            WidgetProperty widgetProperty = WidgetProperty.getProperty(split[2]);
            if (widgetProperty == null) {
                widgetProperty = WidgetProperty.LABEL;
            }
            return new PinPropertyStorageKey(deviceId, pinType, pin, widgetProperty);
        } else {
            return new PinStorageKey(deviceId, pinType, pin);
        }
    }
}
