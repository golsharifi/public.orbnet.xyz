package com.orbvpn.api.domain.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TransactionPage {
    private List<TransactionView> content;
    private int totalElements;
    private int size;
    private int number;
}
