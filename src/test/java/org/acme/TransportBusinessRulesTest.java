package org.acme;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TransportBusinessRulesTest {
    @Inject
    EntityManager entityManager;

    @Test
    void scaleReadingWithValidTokenIsAcceptedForKafkaDelivery() throws InterruptedException {
        Number branchResponse = given()
                .contentType("application/json")
                .body("""
                        {"code":"BR-SCALE","name":"Filial da balança"}
                        """)
                .when().post("/api/branches")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType("application/json")
                .body("""
                        {"externalId":"SCALE-POST","branchId":%d,"secret":"secret-test"}
                        """.formatted(branchResponse.longValue()))
                .when().post("/api/scales")
                .then().statusCode(201);

        given()
                .contentType("application/json")
                .header("X-Scale-Token", "secret-test")
                .body("""
                        {"id":"SCALE-POST","plate":"ABC1112","weight":1232.56,"readingId":"test-reading-id"}
                        """)
                .when().post("/api/readings")
                .then().statusCode(202);

        boolean consumed = false;
        for (int attempt = 0; attempt < 50; attempt++) {
            Number count = (Number) entityManager.createNativeQuery(
                    "select count(*) from processed_reading where reading_id = 'test-reading-id'")
                    .getSingleResult();
            if (count.longValue() > 0) {
                consumed = true;
                break;
            }
            Thread.sleep(100);
        }
        org.junit.jupiter.api.Assertions.assertTrue(consumed, "Kafka event should be persisted by consumer");
    }

    @Test
    void transportCapturesGrainPriceAndTruckCannotHaveConcurrentActiveTransport() {
        Number branchResponse = given()
                .contentType("application/json")
                .body("""
                        {"code":"BR-RULES","name":"Filial de teste"}
                        """)
                .when().post("/api/branches")
                .then().statusCode(201)
                .extract().path("id");
        long branchId = branchResponse.longValue();

        Number truckResponse = given()
                .contentType("application/json")
                .body("""
                        {"plate":"ABC1234","tareKg":5000}
                        """)
                .when().post("/api/trucks")
                .then().statusCode(201)
                .extract().path("id");
        long truckId = truckResponse.longValue();

        Number grainResponse = given()
                .contentType("application/json")
                .body("""
                        {"name":"Milho de teste","purchasePricePerTon":1250.50}
                        """)
                .when().post("/api/grains")
                .then().statusCode(201)
                .extract().path("id");
        long grainId = grainResponse.longValue();

        given()
                .contentType("application/json")
                .body("""
                        {"truckId":%d,"branchId":%d,"grainId":%d}
                        """.formatted(truckId, branchId, grainId))
                .when().post("/api/transports")
                .then().statusCode(201)
                .body("purchasePricePerTon", equalTo(1250.50f));

        given()
                .contentType("application/json")
                .body("""
                        {"truckId":%d,"branchId":%d,"grainId":%d}
                        """.formatted(truckId, branchId, grainId))
                .when().post("/api/transports")
                .then().statusCode(409);
    }
}
