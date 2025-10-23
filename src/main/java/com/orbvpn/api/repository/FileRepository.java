package com.orbvpn.api.repository;

import com.orbvpn.api.domain.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<File, Integer> {

}
