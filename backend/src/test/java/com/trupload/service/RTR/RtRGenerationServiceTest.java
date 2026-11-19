package com.trupload.service.RTR;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.trupload.config.RtrProperties;

@ExtendWith(MockitoExtension.class)
class RtRGenerationServiceTest {

    @Mock
    private RemoteExecutionService remoteExecutionService;

    private RtrProperties properties;
    private RtRGenerationService service;

    @BeforeEach
    void setUp() {
        properties = new RtrProperties();
        properties.setRemoteServer("optmsctxman01qv");
        properties.setRemoteExecutablePath("D:/Program Files/Fleet Management/bin/ie.exe");
        properties.setOutputDirectory("D:/RTE/");
        properties.setFileExtension("rtr");
        properties.setPowerShellExecutable("powershell.exe");
        properties.setDatabaseUsername("csa");
        properties.setDatabasePassword("configured-secret");
        properties.setConnectionTimeoutSeconds(30);
        properties.setExecutionTimeoutSeconds(300);
        service = new RtRGenerationService(properties, remoteExecutionService);
    }

    @Test
    void buildsConfiguredRemoteOutputPathAndUsesSelectedDatabaseAsDsn() {
        when(remoteExecutionService.generateRtrFile(
                properties, "CSTRSB_QA", "0729S16", "D:\\RTE\\0729S16.rtr", "testuser"))
                .thenReturn("D:\\RTE\\0729S16.rtr");

        assertEquals("D:\\RTE\\0729S16.rtr", service.generate("CSTRSB_QA", "0729S16", "testuser"));

        verify(remoteExecutionService).generateRtrFile(
                properties, "CSTRSB_QA", "0729S16", "D:\\RTE\\0729S16.rtr", "testuser");
    }

    @Test
    void rejectsPathLikeRouteGroupBeforeRemoteExecution() {
        assertThrows(IllegalArgumentException.class,
                () -> service.generate("CSTRSB_QA", "..\\outside", "testuser"));

        verifyNoInteractions(remoteExecutionService);
    }

    @Test
    void requiresDatabaseCredentialsBeforeRemoteExecution() {
        properties.setDatabasePassword("");

        assertThrows(IllegalStateException.class,
                () -> service.generate("CSTRSB_QA", "0729S16", "testuser"));

        verifyNoInteractions(remoteExecutionService);
    }
}
