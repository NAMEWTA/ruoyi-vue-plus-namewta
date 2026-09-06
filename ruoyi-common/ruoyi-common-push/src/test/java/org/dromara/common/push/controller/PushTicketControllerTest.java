package org.dromara.common.push.controller;

import org.dromara.common.push.security.PushTicketService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PushTicketControllerTest {

    @Test
    void putsIssuedTicketInResponseData() {
        PushTicketService service = new PushTicketService() {
            @Override
            public String issue() {
                return "ticket-123";
            }
        };

        var response = new PushTicketController(service).issue();

        assertEquals(200, response.getCode());
        assertEquals("ticket-123", response.getData());
        assertEquals("操作成功", response.getMsg());
    }
}
