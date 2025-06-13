package org.egov.id.service;


import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.idgen.*;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class IdGenerationServiceTest {

    @Test
    void testGenerateIdResponse() throws Exception {

        IdGenerationService idGenerationService = new IdGenerationService();

        ArrayList<IdRequest> idRequestList = new ArrayList<>();
        idRequestList.add(new IdRequest());

        IdGenerationRequest idGenerationRequest = new IdGenerationRequest();
        idGenerationRequest.setIdRequests(idRequestList);
        assertThrows(CustomException.class, () -> idGenerationService.generateIdResponse(idGenerationRequest));
    }

    @Test
    void testGenerateIdResponseWithArguments() throws Exception {

        IdGenerationService idGenerationService = new IdGenerationService();

        ArrayList<IdRequest> idRequestList = new ArrayList<>();
        idRequestList.add(new IdRequest());

        IdRequest idRequest = new IdRequest("Id Name", "42", "Format", 3);
        idRequest.setFormat("\\[(.*?)\\]");

        ArrayList<IdRequest> idRequestList1 = new ArrayList<>();
        idRequestList1.add(idRequest);
        IdGenerationRequest idGenerationRequest = mock(IdGenerationRequest.class);
        when(idGenerationRequest.getIdRequests()).thenReturn(idRequestList1);
        when(idGenerationRequest.getRequestInfo()).thenReturn(new RequestInfo());
        doNothing().when(idGenerationRequest).setIdRequests((List<IdRequest>) any());
        idGenerationRequest.setIdRequests(idRequestList);
        assertThrows(CustomException.class, () -> idGenerationService.generateIdResponse(idGenerationRequest));
        verify(idGenerationRequest).getIdRequests();
        verify(idGenerationRequest).getRequestInfo();
        verify(idGenerationRequest).setIdRequests((List<IdRequest>) any());
    }

    @Test
    void testGenerateIdResponseTenantIdNull() throws Exception {

        IdGenerationService idGenerationService = new IdGenerationService();

        ArrayList<IdRequest> idRequestList = new ArrayList<>();
        idRequestList.add(new IdRequest());

        IdRequest idRequest = new IdRequest("Id Name", null, "Format", 3);
        idRequest.setFormat("\\[(.*?)\\]");

        ArrayList<IdRequest> idRequestList1 = new ArrayList<>();
        idRequestList1.add(idRequest);
        IdGenerationRequest idGenerationRequest = mock(IdGenerationRequest.class);
        when(idGenerationRequest.getIdRequests()).thenReturn(idRequestList1);
        when(idGenerationRequest.getRequestInfo()).thenReturn(new RequestInfo());
        doNothing().when(idGenerationRequest).setIdRequests((List<IdRequest>) any());
        idGenerationRequest.setIdRequests(idRequestList);
        assertThrows(CustomException.class, () -> idGenerationService.generateIdResponse(idGenerationRequest));
        verify(idGenerationRequest).getIdRequests();
        verify(idGenerationRequest).getRequestInfo();
        verify(idGenerationRequest).setIdRequests((List<IdRequest>) any());
    }

    @Test
    void testGenerateIdResponseNullCount() throws Exception {

        IdGenerationService idGenerationService = new IdGenerationService();

        ArrayList<IdRequest> idRequestList = new ArrayList<>();
        idRequestList.add(new IdRequest());

        IdRequest idRequest = new IdRequest("Id Name", "42", "Format", null);
        idRequest.setFormat("\\[(.*?)\\]");

        ArrayList<IdRequest> idRequestList1 = new ArrayList<>();
        idRequestList1.add(idRequest);
        IdGenerationRequest idGenerationRequest = mock(IdGenerationRequest.class);
        when(idGenerationRequest.getIdRequests()).thenReturn(idRequestList1);
        when(idGenerationRequest.getRequestInfo()).thenReturn(new RequestInfo());
        doNothing().when(idGenerationRequest).setIdRequests((List<IdRequest>) any());
        idGenerationRequest.setIdRequests(idRequestList);
        assertThrows(CustomException.class, () -> idGenerationService.generateIdResponse(idGenerationRequest));
        verify(idGenerationRequest).getIdRequests();
        verify(idGenerationRequest).getRequestInfo();
        verify(idGenerationRequest).setIdRequests((List<IdRequest>) any());
    }

    @Test
    void testGenerateIdResponseIdnull() throws Exception {

        IdGenerationService idGenerationService = new IdGenerationService();

        ArrayList<IdRequest> idRequestList = new ArrayList<>();
        idRequestList.add(new IdRequest());

        IdRequest idRequest = new IdRequest(null, "42", "Format", 3);
        idRequest.setFormat("\\[(.*?)\\]");

        ArrayList<IdRequest> idRequestList1 = new ArrayList<>();
        idRequestList1.add(idRequest);
        IdGenerationRequest idGenerationRequest = mock(IdGenerationRequest.class);
        when(idGenerationRequest.getIdRequests()).thenReturn(idRequestList1);
        when(idGenerationRequest.getRequestInfo()).thenReturn(new RequestInfo());
        doNothing().when(idGenerationRequest).setIdRequests((List<IdRequest>) any());
        idGenerationRequest.setIdRequests(idRequestList);
        assertThrows(CustomException.class, () -> idGenerationService.generateIdResponse(idGenerationRequest));
        verify(idGenerationRequest).getIdRequests();
        verify(idGenerationRequest).getRequestInfo();
        verify(idGenerationRequest).setIdRequests((List<IdRequest>) any());
    }

}

