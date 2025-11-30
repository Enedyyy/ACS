package com.acs.finance.repository;

import com.acs.finance.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {
    
    Optional<Session> findBySid(String sid);
    
    void deleteBySid(String sid);
}
