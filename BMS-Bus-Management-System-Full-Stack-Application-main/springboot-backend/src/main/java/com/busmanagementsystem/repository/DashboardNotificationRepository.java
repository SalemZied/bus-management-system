package com.busmanagementsystem.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.busmanagementsystem.entity.DashboardNotification;

@Repository
public interface DashboardNotificationRepository extends JpaRepository<DashboardNotification, Long> {
    List<DashboardNotification> findByActiveTrueOrderByIdDesc();
}
