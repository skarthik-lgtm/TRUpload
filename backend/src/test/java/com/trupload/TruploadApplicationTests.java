package com.trupload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "SQLSERVER_HOST=localhost",
        "SQLSERVER_USERNAME=test_user",
        "SQLSERVER_PASSWORD=test_password",
        "JWT_SECRET=test-only-jwt-secret-with-at-least-32-characters",
        "spring.sql.init.mode=never"
})
@Disabled("Requires a reachable SQL Server with the TRUpload upload tables")
class TRUploadApplicationTests {

    @Test
    void contextLoads() {
    }
}
