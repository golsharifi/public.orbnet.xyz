package com.orbvpn.api.domain.entity;

import com.orbvpn.api.domain.enums.TicketCategory;
import com.orbvpn.api.domain.enums.TicketStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class Ticket {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false, columnDefinition = "text")
  private String text;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private TicketCategory category;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private TicketStatus status;

  @ManyToOne
  private User creator;

  @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
  @OrderBy("createdAt")
  private List<TicketReply> replies = new ArrayList<>();

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;
}
