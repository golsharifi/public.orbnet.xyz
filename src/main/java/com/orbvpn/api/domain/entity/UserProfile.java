package com.orbvpn.api.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.orbvpn.api.domain.entity.converter.LocaleConverter;
import com.orbvpn.api.utils.Utilities;
import java.util.Locale;

@Entity
@Table(name = "user_profile")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter
  private int id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", unique = true, nullable = false)
  @Setter
  private User user;

  @Column
  private String firstName;

  @Column
  private String lastName;

  /**
   * Set first name with automatic title case conversion
   */
  public void setFirstName(String firstName) {
    this.firstName = Utilities.toTitleCase(firstName);
  }

  /**
   * Set last name with automatic title case conversion
   */
  public void setLastName(String lastName) {
    this.lastName = Utilities.toTitleCase(lastName);
  }

  @Column
  @Setter
  private String phone;

  @Column
  @Setter
  private String address;

  @Column
  @Setter
  private String city;

  @Column
  @Setter
  private String country;

  @Column
  @Setter
  private String postalCode;

  @Column
  @Setter
  private LocalDate birthDate;

  @Column
  @CreatedDate
  @Setter
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  @Setter
  private LocalDateTime updatedAt;

  @Column
  @Setter
  private String telegramChatId;

  @Column
  @Setter
  private String telegramUsername;

  @Column
  @Convert(converter = LocaleConverter.class)
  @Setter
  private Locale language;
}
