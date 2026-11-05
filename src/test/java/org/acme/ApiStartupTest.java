package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ApiStartupTest {
    @Test
    void testOpenApiIsAvailable() {
        given()
          .when().get("/openapi")
          .then()
             .statusCode(200);
    }

}
