package com.nc.FinalProject.repository;

import com.nc.FinalProject.entity.Folder;
import com.nc.FinalProject.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findByOwner(Users owner);
}