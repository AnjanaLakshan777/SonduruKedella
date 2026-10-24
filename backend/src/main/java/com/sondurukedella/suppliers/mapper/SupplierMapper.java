package com.sondurukedella.suppliers.mapper;

import com.sondurukedella.suppliers.dto.SupplierResponse;
import com.sondurukedella.suppliers.entity.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SupplierMapper {

    public SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getSupplies(),
                supplier.getCreditDays(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt(),
                supplier.getVersion());
    }
}
