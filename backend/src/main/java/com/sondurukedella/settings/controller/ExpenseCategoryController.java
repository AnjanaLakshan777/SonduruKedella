package com.sondurukedella.settings.controller;

import com.sondurukedella.settings.dto.ExpenseCategoryCreateRequest;
import com.sondurukedella.settings.dto.ExpenseCategoryResponse;
import com.sondurukedella.settings.entity.ExpenseGroup;
import com.sondurukedella.settings.service.ExpenseCategoryService;
import com.sondurukedella.settings.service.LookupAddResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService service;

    @GetMapping
    public List<ExpenseCategoryResponse> list(@RequestParam(required = false) ExpenseGroup group) {
        return service.listActive(group);
    }

    /** 201 for a new category, 200 when a removed category with the same name was reactivated. */
    @PostMapping
    public ResponseEntity<ExpenseCategoryResponse> add(@Valid @RequestBody ExpenseCategoryCreateRequest request) {
        LookupAddResult<ExpenseCategoryResponse> result = service.add(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.item());
    }

    /** Removes the category from the list (deactivates it); existing expenses keep it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id) {
        service.deactivate(id);
    }
}
