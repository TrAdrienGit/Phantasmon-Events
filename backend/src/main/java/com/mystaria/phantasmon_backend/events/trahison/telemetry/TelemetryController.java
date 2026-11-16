package com.mystaria.phantasmon_backend.events.trahison.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mystaria.phantasmon_backend.admin.AdminService;

/** The measured step times (T3.6), for admins: the median per mechanism and per room. */
@RestController
public class TelemetryController {

	private final StepTimes times;
	private final AdminService adminService;

	public TelemetryController(StepTimes times, AdminService adminService) {
		this.times = times;
		this.adminService = adminService;
	}

	@GetMapping("/events/trahison/telemetry/step-times")
	public Map<String, Object> stepTimes(Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("mechanisms", times.byMechanism());
		out.put("rooms", times.byRoom());
		return out;
	}
}
