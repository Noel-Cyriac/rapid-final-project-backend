package com.nc.FinalProject.repository;

import com.nc.FinalProject.dto.UserLoginProjection;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<Users, Long> {

    // Original method — used in other parts of the code
    Optional<Users> findByEmail(String email);

    @Query("SELECT u.email AS email, u.password AS password FROM Users u WHERE u.email = :email")
    Optional<UserLoginProjection> findUserForLogin(@Param("email") String email);
}