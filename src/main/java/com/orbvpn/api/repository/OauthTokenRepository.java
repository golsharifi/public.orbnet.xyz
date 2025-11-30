package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.OauthToken;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.SocialMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface OauthTokenRepository extends JpaRepository<OauthToken, Integer> {

    @Modifying
    @Transactional
    void deleteByUser(User user);

    Optional<OauthToken> findByUserId(Integer userId);

    Optional<OauthToken> findByUserIdAndSocialMedia(Integer userId, SocialMedia socialMedia);

    List<OauthToken> findAllByUserId(Integer userId);

    @Modifying
    @Transactional
    void deleteByIdAndUserId(Integer id, Integer userId);

}
