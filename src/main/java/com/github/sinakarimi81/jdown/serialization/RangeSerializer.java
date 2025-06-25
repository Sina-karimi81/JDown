package com.github.sinakarimi81.jdown.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.github.sinakarimi81.jdown.dataObjects.Range;

import java.io.IOException;

public class RangeSerializer extends JsonSerializer<Range> {

    @Override
    public void serialize(Range value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeFieldName(value.rangeString());
    }
}
