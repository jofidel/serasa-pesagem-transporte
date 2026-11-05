package org.acme.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RegistrationResource {
    private static final Pattern PLATE_PATTERN = Pattern.compile("^[A-Z0-9]{7}$");

    @PersistenceContext
    EntityManager entityManager;

    public record BranchIn(@NotBlank String code, @NotBlank String name) {
    }

    public record TruckIn(@NotBlank String plate, @NotNull @PositiveOrZero BigDecimal tareKg) {
    }

    public record GrainIn(@NotBlank String name, @NotNull @Positive BigDecimal purchasePricePerTon) {
    }

    public record ScaleIn(@NotBlank String externalId, @NotNull Long branchId, @NotBlank String secret) {
    }

    public record TransportIn(
            @NotNull Long truckId,
            @NotNull Long branchId,
            @NotNull Long grainId) {
    }

    public record IdResponse(Long id) {
    }

    public record ScaleView(Long id, String externalId, Long branchId, String branchCode, boolean active) {
    }

    @POST
    @Path("/branches")
    @Transactional
    public Response createBranch(@Valid BranchIn input) {
        Branch branch = new Branch();
        branch.code = input.code().trim();
        branch.name = input.name().trim();
        branch.persist();
        return Response.status(Response.Status.CREATED).entity(branch).build();
    }

    @GET
    @Path("/branches")
    public List<Branch> branches() {
        return Branch.listAll();
    }

    @POST
    @Path("/trucks")
    @Transactional
    public Response createTruck(@Valid TruckIn input) {
        String plate = input.plate().trim().toUpperCase();
        if (!PLATE_PATTERN.matcher(plate).matches()) {
            throw new WebApplicationException("Placa deve conter 7 letras ou números", 400);
        }
        Truck truck = new Truck();
        truck.plate = plate;
        truck.tareKg = input.tareKg();
        truck.persist();
        return Response.status(Response.Status.CREATED).entity(truck).build();
    }

    @GET
    @Path("/trucks")
    public List<Truck> trucks() {
        return Truck.listAll();
    }

    @POST
    @Path("/grains")
    @Transactional
    public Response createGrain(@Valid GrainIn input) {
        GrainType grain = new GrainType();
        grain.name = input.name().trim();
        grain.purchasePricePerTon = input.purchasePricePerTon();
        grain.persist();
        return Response.status(Response.Status.CREATED).entity(grain).build();
    }

    @GET
    @Path("/grains")
    public List<GrainType> grains() {
        return GrainType.listAll();
    }

    @POST
    @Path("/scales")
    @Transactional
    public Response createScale(@Valid ScaleIn input) {
        Branch branch = Branch.findById(input.branchId());
        if (branch == null || !branch.active) {
            throw new NotFoundException("Filial ativa não encontrada");
        }
        WeighingScale scale = new WeighingScale();
        scale.externalId = input.externalId().trim();
        scale.branch = branch;
        scale.credentialHash = hash(input.secret());
        scale.persist();
        return Response.status(Response.Status.CREATED).entity(new IdResponse(scale.id)).build();
    }

    @GET
    @Path("/scales")
    public List<ScaleView> scales() {
        return WeighingScale.<WeighingScale>listAll().stream()
                .map(scale -> new ScaleView(
                        scale.id,
                        scale.externalId,
                        scale.branch.id,
                        scale.branch.code,
                        scale.active))
                .toList();
    }

    @POST
    @Path("/transports")
    @Transactional
    public Response createTransport(@Valid TransportIn input) {
        Truck truck = Truck.findById(input.truckId());
        Branch branch = Branch.findById(input.branchId());
        GrainType grain = GrainType.findById(input.grainId());
        if (truck == null || branch == null || grain == null
                || !truck.active || !branch.active || !grain.active) {
            throw new NotFoundException("Caminhão, filial ou grão ativo não encontrado");
        }
        long activeTransports = Transport.count(
                "truck = ?1 and status in ?2",
                truck,
                List.of(TransportStatus.STARTED, TransportStatus.AWAITING_WEIGHING));
        if (activeTransports > 0) {
            throw new WebApplicationException("Caminhão já possui uma transação ativa", 409);
        }

        Transport transport = new Transport();
        transport.truck = truck;
        transport.branch = branch;
        transport.grain = grain;
        transport.purchasePricePerTon = grain.purchasePricePerTon;
        transport.persist();
        return Response.status(Response.Status.CREATED).entity(transport).build();
    }

    @GET
    @Path("/transports")
    public List<Transport> transports(@QueryParam("status") TransportStatus status) {
        return status == null
                ? Transport.find("order by startedAt desc").page(0, 100).list()
                : Transport.find("status = ?1 order by startedAt desc", status).page(0, 100).list();
    }

    @GET
    @Path("/weighings")
    public List<Weighing> weighings(
            @QueryParam("plate") String plate,
            @QueryParam("branchId") Long branchId) {
        String normalizedPlate = plate == null ? null : plate.trim().toUpperCase();
        if (normalizedPlate != null && branchId != null) {
            return Weighing.find(
                    "plate = ?1 and scale.branch.id = ?2 order by weighedAt desc",
                    normalizedPlate,
                    branchId).page(0, 100).list();
        }
        if (normalizedPlate != null) {
            return Weighing.find("plate = ?1 order by weighedAt desc", normalizedPlate)
                    .page(0, 100).list();
        }
        if (branchId != null) {
            return Weighing.find("scale.branch.id = ?1 order by weighedAt desc", branchId)
                    .page(0, 100).list();
        }
        return Weighing.find("order by weighedAt desc").page(0, 100).list();
    }

    @GET
    @Path("/reports/summary")
    public Map<String, Object> summary() {
        return Map.of(
                "weighings", Weighing.count(),
                "netKg", entityManager.createQuery(
                        "select coalesce(sum(w.netKg), 0) from Weighing w").getSingleResult(),
                "cargoCost", entityManager.createQuery(
                        "select coalesce(sum(w.cargoCost), 0) from Weighing w").getSingleResult(),
                "estimatedSalePrice", entityManager.createQuery(
                        "select coalesce(sum(w.estimatedSalePrice), 0) from Weighing w").getSingleResult());
    }

    public static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
