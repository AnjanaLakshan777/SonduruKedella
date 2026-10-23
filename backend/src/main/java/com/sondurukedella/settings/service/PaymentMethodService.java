package com.sondurukedella.settings.service;

import com.sondurukedella.settings.entity.PaymentMethod;
import com.sondurukedella.settings.mapper.SettingsMapper;
import com.sondurukedella.settings.repository.PaymentMethodRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentMethodService extends SimpleLookupService<PaymentMethod> {

    public PaymentMethodService(PaymentMethodRepository repository, SettingsMapper mapper) {
        super(repository, mapper, PaymentMethod::new, PaymentMethod.NAME_MAX_LENGTH, "Payment method");
    }
}
