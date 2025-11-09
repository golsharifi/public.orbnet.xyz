package com.orbvpn.api.domain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GithubProfile {

    private String email;
    private boolean primary;
    private boolean verified;
    private String visibility;

}
