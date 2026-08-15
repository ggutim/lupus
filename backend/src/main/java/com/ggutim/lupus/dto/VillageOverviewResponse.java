package com.ggutim.lupus.dto;

import java.util.List;

/**
 * The public village roster: every player's nickname and alive/dead
 * status, with no roles. Visible to any player and the master alike —
 * it's exactly what's visible around the physical table once a death
 * has been revealed, nothing more.
 */
public record VillageOverviewResponse(List<PlayerResponse> players) {
}
