package com.orbvpn.api.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.time.LocalDate;
import java.util.Locale;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileEdit {
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private String city;
    private String country;
    private String postalCode;
    private Locale language;
    private String telegramUsername;
    private String telegramChatId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;
}