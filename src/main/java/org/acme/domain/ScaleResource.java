package org.acme.domain;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@Path("/api/readings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ScaleResource {
    public record Reading(
            @NotBlank String id,
            @NotBlank String plate,
            @NotNull @PositiveOrZero BigDecimal weight,
            String readingId,
            Instant timestamp) {
    }

    @Inject
    @Channel("scale-readings-out")
    Emitter<Reading> emitter;

    @POST
    public Response receive(@Valid Reading reading, @HeaderParam("X-Scale-Token") String token) {
        WeighingScale scale = WeighingScale.find(
                "externalId = ?1 and active = true",
                reading.id().trim()).firstResult();
        if (scale == null || token == null
                || !RegistrationResource.hash(token).equals(scale.credentialHash)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        Reading accepted = new Reading(
                reading.id().trim(),
                reading.plate().trim().toUpperCase(),
                reading.weight(),
                reading.readingId() == null || reading.readingId().isBlank()
                        ? UUID.randomUUID().toString()
                        : reading.readingId().trim(),
                reading.timestamp() == null ? Instant.now() : reading.timestamp());
        try {
            var metadata = OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(accepted.id())
                    .build();
            emitter.send(Message.of(accepted).addMetadata(metadata));
            return Response.accepted().build();
        } catch (Exception exception) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Fila temporariamente indisponível")
                    .build();
        }
    }
}
