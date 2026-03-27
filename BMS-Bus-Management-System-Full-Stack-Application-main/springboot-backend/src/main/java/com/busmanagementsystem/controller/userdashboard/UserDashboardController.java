package com.busmanagementsystem.controller.userdashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.busmanagementsystem.dto.userdashboard.UserDashboardResponseDto;
import com.busmanagementsystem.service.userdashboard.UserDashboardService;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/user-dashboard")
public class UserDashboardController {

    private final UserDashboardService userDashboardService;

    @Autowired
    public UserDashboardController(UserDashboardService userDashboardService) {
        this.userDashboardService = userDashboardService;
    }

    @GetMapping
    public ResponseEntity<UserDashboardResponseDto> getDashboardData() {
        return ResponseEntity.ok(userDashboardService.getDashboardData());
    }
}
