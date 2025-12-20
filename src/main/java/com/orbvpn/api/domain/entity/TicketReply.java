package com.orbvpn.api.domain.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class TicketReply {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @ManyToOne(optional = false)
  private Ticket ticket;

  @Column(columnDefinition = "text")
  private String text;

  @ManyToOne
  private User creator;

  @Column
  @CreatedDate
  private LocalDateTime createdAt;

  @Column
  @LastModifiedDate
  private LocalDateTime updatedAt;
}
