package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.railway.security.file.FileDownloadTicketService;
import com.railway.security.shared.cache.LocalTemporaryValueStore;
import com.railway.security.shared.web.BusinessException;
import org.junit.jupiter.api.Test;

class FileDownloadTicketServiceTest {
    private final FileDownloadTicketService service =
            new FileDownloadTicketService(new LocalTemporaryValueStore());

    @Test
    void issuedTicketOnlyAuthorizesItsAttachment() {
        String ticket = service.issue(59L);

        assertDoesNotThrow(() -> service.validate(ticket, 59L));
        assertThrows(BusinessException.class, () -> service.validate(ticket, 59L));
        assertThrows(BusinessException.class, () -> service.validate(ticket, 60L));
        assertThrows(BusinessException.class, () -> service.validate("invalid", 59L));
    }
}
