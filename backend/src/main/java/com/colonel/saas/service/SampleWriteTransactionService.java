package com.colonel.saas.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
public class SampleWriteTransactionService {

    @Transactional(rollbackFor = Exception.class)
    public <T> T execute(Supplier<T> action) {
        return action.get();
    }
}
