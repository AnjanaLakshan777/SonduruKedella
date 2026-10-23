package com.sondurukedella.settings.controller;

import com.sondurukedella.settings.dto.LookupItemCreateRequest;
import com.sondurukedella.settings.dto.LookupItemResponse;
import com.sondurukedella.settings.service.LookupAddResult;
import com.sondurukedella.settings.service.MeasurementUnitService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/units")
@RequiredArgsConstructor
public class MeasurementUnitController {

    private final MeasurementUnitService service;

    @GetMapping
    public List<LookupItemResponse> list() {
        return service.listActive();
    }

    /** 201 for a new unit, 200 when a removed unit with the same name was reactivated. */
    @PostMapping
    public ResponseEntity<LookupItemResponse> add(@Valid @RequestBody LookupItemCreateRequest request) {
        LookupAddResult<LookupItemResponse> result = service.add(request.name());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id) {
        service.deactivate(id);
    }
}
