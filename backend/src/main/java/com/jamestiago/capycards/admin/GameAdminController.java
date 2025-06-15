package com.jamestiago.capycards.admin;

import com.jamestiago.capycards.repository.GameEventLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/games")
public class GameAdminController {
    private final GameEventLogRepository eventLogRepository;

    public GameAdminController(GameEventLogRepository eventLogRepository) {
        this.eventLogRepository = eventLogRepository;
    }

    @GetMapping("/ids")
    public List<String> getAllGameIds() {
        return eventLogRepository.findAllDistinctGameIds();
    }
}