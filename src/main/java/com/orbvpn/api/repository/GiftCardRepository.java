package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    /**
     * Find gift card by code with pessimistic write lock to prevent concurrent
     * redemption
     *
     * @param code The gift card code
     * @return Optional of GiftCard
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GiftCard g WHERE g.code = :code")
    Optional<GiftCard> findByCodeWithLock(@Param("code") String code);

    /**
     * Find gift card by code without locking
     *
     * @param code The gift card code
     * @return Optional of GiftCard
     */
    Optional<GiftCard> findByCode(String code);

    /**
     * Find all gift cards for a specific group
     *
     * @param groupId The group ID
     * @return List of gift cards
     */
    List<GiftCard> findByGroupId(int groupId);

    List<GiftCard> findByUsedFalseAndExpirationDateAfter(LocalDateTime date);

    /**
     * Find all valid gift cards (unused and not expired)
     *
     * @param date The date to check against for expiration
     * @return List of valid gift cards
     */
    @Query("SELECT g FROM GiftCard g WHERE g.used = false AND g.cancelled = false")
    List<GiftCard> findAllActive();
}