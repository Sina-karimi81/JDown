package com.github.sinakarimi.jdown.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.github.sinakarimi.jdown.dataObjects.Range;

import java.io.IOException;

/**
 * Used to Serialize the key of a Map that is of type Range
 */
public class RangeSerializer extends JsonSerializer<Range> {

    @Override
    public void serialize(Range value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeFieldName(value.rangeString());
    }
}
