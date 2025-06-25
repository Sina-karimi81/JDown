package com.github.sinakarimi81.jdown.serialization;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.github.sinakarimi81.jdown.dataObjects.Range;

import java.io.IOException;

public class RangeDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        return Range.valueOf(key);
    }
}
