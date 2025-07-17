package com.github.sinakarimi.jdown.serialization;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.github.sinakarimi.jdown.dataObjects.Range;

import java.io.IOException;

/**
 * Used to Serialize the key of a Map that is of type Range
 */
public class RangeDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        return Range.valueOf(key);
    }
}
