package com.orbvpn.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsServiceCategoryView {
    private String id;
    private String name;
    private String icon;
    private String description;
    private List<DnsServiceView> services;
}
