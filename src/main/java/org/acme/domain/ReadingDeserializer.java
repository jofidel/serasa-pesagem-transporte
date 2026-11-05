package org.acme.domain;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
public class ReadingDeserializer extends ObjectMapperDeserializer<ScaleResource.Reading> { public ReadingDeserializer(){super(ScaleResource.Reading.class);} }
