package nl.yestelecom.phoenix.connection.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import nl.yestelecom.phoenix.connection.model.VChangeRequest;
import nl.yestelecom.phoenix.connection.model.VChangeRequestHistory;
import nl.yestelecom.phoenix.connection.service.ChangeRequestService;

@RestController
@RequestMapping("/secure/connection/changerequest")
@PreAuthorize("hasAuthority('MANAGE_CONNECTIONS_EMPLOYEE')")
public class ChangeRequestController {
    @Autowired
    private ChangeRequestService changeRequestService;

    @RequestMapping(value = "/{gssId}", method = RequestMethod.GET)
    @PreAuthorize("hasAuthority('MANAGE_CONNECTIONS_EMPLOYEE')")
    public List<VChangeRequest> getChangeRequests(@PathVariable Long gssId) {
        return changeRequestService.getChangeRequests(gssId);
    }

    @RequestMapping(value = "/{gssId}", method = RequestMethod.POST)
    @PreAuthorize("hasAuthority('MANAGE_CONNECTIONS_EMPLOYEE')")
    public Map<String, String> cancelChanges(@PathVariable Long gssId, @RequestBody List<VChangeRequest> changeRequests) {
        changeRequestService.cancelChanges(gssId, changeRequests);
        final Map<String, String> map = new HashMap<String, String>();
        map.put("success", "true");
        return map;
    }

    @RequestMapping(value = "/{gssId}/history", method = RequestMethod.GET)
    @PreAuthorize("hasAuthority('MANAGE_CONNECTIONS_EMPLOYEE')")
    public List<VChangeRequestHistory> getHistoryChangeRequests(@PathVariable Long gssId) {
        return changeRequestService.getChangeRequestHistory(gssId);
    }
}
