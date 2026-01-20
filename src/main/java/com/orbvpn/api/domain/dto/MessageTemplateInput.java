package com.orbvpn.api.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class MessageTemplateInput {
    private String name;
    private String content;
    private List<String> variables;
}