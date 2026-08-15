package com.ggutim.lupus.service;

import com.ggutim.lupus.dto.GameUpdatedMessage;
import com.ggutim.lupus.dto.MasterGameStateResponse;
import com.ggutim.lupus.dto.MasterPlayerView;
import com.ggutim.lupus.dto.PlayerResponse;
import com.ggutim.lupus.dto.VillageOverviewResponse;
import com.ggutim.lupus.exception.InvalidGamePhaseException;
import com.ggutim.lupus.exception.InvalidRulesetException;
import com.ggutim.lupus.exception.PlayerNotFoundException;
import com.ggutim.lupus.exception.RoomNotFoundException;
import com.ggutim.lupus.model.Alignment;
import com.ggutim.lupus.model.GamePhase;
import com.ggutim.lupus.model.Player;
import com.ggutim.lupus.model.Role;
import com.ggutim.lupus.model.Room;
import com.ggutim.lupus.model.RoomStatus;
import com.ggutim.lupus.repository.PlayerRepository;
import com.ggutim.lupus.repository.RoomRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives the master's "deck of cards" once a game has started: role
 * assignment, phase transitions, night/vote selections, and win
 * detection. Kept separate from {@link PlayerService} (pre-game
 * lobby: joining/kicking) and {@link RoomService} (room creation and
 * master-token validation), which this class depends on.
 */
@Service
public class GameService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameService(RoomRepository roomRepository, PlayerRepository playerRepository, RoomService roomService,
                        SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Assigns roles to every joined player and moves the room into its
     * first phase. Called by {@link PlayerService#startGame} once it has
     * validated the master token and the minimum player count.
     */
    @Transactional
    public void startGame(Room room, List<Player> players) {
        assignRoles(room, players);

        room.start();
        room.setRoundNumber(1);
        room.setPhase(GamePhase.ROLES_ASSIGNED);
        roomRepository.save(room);

        broadcastGameUpdated(room);
    }

    public MasterGameStateResponse getGameState(String code, String masterToken) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        ensureGameStarted(room);
        return buildMasterGameState(room);
    }

    /**
     * The public village roster (nicknames and alive/dead status, no
     * roles) visible to any player or the master. Uses the same
     * MORNING_REVEAL-delayed visibility as {@link PlayerResponse#visibleDuring},
     * so a player checking the overview can't spoil the master's reveal.
     */
    public VillageOverviewResponse getVillageOverview(String code) {
        Room room = roomRepository.findByCode(code)
                .orElseThrow(() -> new RoomNotFoundException(code));

        List<PlayerResponse> players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .map(player -> PlayerResponse.visibleDuring(player, room.getPhase()))
                .toList();

        return new VillageOverviewResponse(players);
    }

    @Transactional
    public MasterGameStateResponse selectWerewolfVictim(String code, String masterToken, Long targetId) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        requirePhase(room, GamePhase.WEREWOLVES_SELECT_VICTIM);

        Player target = requireAlivePlayerInRoom(room, targetId);
        if (target.getRole() == Role.WEREWOLF) {
            throw new InvalidGamePhaseException("Werewolves cannot select another werewolf as their victim");
        }
        room.setPendingWerewolfVictimId(target.getId());
        roomRepository.save(room);

        broadcastGameUpdated(room);
        return buildMasterGameState(room);
    }

    @Transactional
    public MasterGameStateResponse selectPriestTarget(String code, String masterToken, Long targetId) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        requirePhase(room, GamePhase.PRIEST_SELECT_TARGET);

        Player target = requireAlivePlayerInRoom(room, targetId);
        room.setPendingPriestTargetId(target.getId());
        room.setPriestCheckResult(target.getRole().getAlignment());
        roomRepository.save(room);

        broadcastGameUpdated(room);
        return buildMasterGameState(room);
    }

    @Transactional
    public MasterGameStateResponse selectVoteVictim(String code, String masterToken, Long targetId) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        requirePhase(room, GamePhase.VOTE_SELECT_TARGET);

        if (targetId == null) {
            room.setPendingVoteVictimId(null);
        } else {
            Player target = requireAlivePlayerInRoom(room, targetId);
            room.setPendingVoteVictimId(target.getId());
        }
        roomRepository.save(room);

        broadcastGameUpdated(room);
        return buildMasterGameState(room);
    }

    @Transactional
    public MasterGameStateResponse advancePhase(String code, String masterToken) {
        Room room = roomService.findRoomForMaster(code, masterToken);
        ensureGameStarted(room);

        switch (room.getPhase()) {
            case ROLES_ASSIGNED -> enterPhase(room, GamePhase.NIGHT_START);
            case NIGHT_START -> enterPhase(room, GamePhase.WEREWOLVES_WAKE_UP);
            case WEREWOLVES_WAKE_UP -> enterPhase(room, GamePhase.WEREWOLVES_SELECT_VICTIM);
            case WEREWOLVES_SELECT_VICTIM -> {
                requireSelectionMade(room.getPendingWerewolfVictimId(),
                        "Select who the werewolves killed before advancing");
                boolean priestSurvives = hasAlivePriestExcluding(room, room.getPendingWerewolfVictimId());
                enterPhase(room, priestSurvives ? GamePhase.PRIEST_WAKE_UP : GamePhase.MORNING_REVEAL);
            }
            case PRIEST_WAKE_UP -> enterPhase(room, GamePhase.PRIEST_SELECT_TARGET);
            case PRIEST_SELECT_TARGET -> {
                requireSelectionMade(room.getPendingPriestTargetId(),
                        "Select who the priest inspects before advancing");
                enterPhase(room, GamePhase.MORNING_REVEAL);
            }
            case MORNING_REVEAL -> enterPhase(room, GamePhase.DISCUSSION);
            case DISCUSSION -> enterPhase(room, GamePhase.VOTE_SELECT_TARGET);
            case VOTE_SELECT_TARGET -> resolveVoteAndAdvance(room);
            case GAME_OVER -> throw new InvalidGamePhaseException("The game has already ended");
        }

        roomRepository.save(room);
        broadcastGameUpdated(room);
        return buildMasterGameState(room);
    }

    private void assignRoles(Room room, List<Player> players) {
        int werewolfCount = room.getRoleCounts().getOrDefault(Role.WEREWOLF, 0);
        int priestCount = room.getRoleCounts().getOrDefault(Role.PRIEST, 0);

        if (werewolfCount + priestCount > players.size()) {
            throw new InvalidRulesetException(
                    "werewolfCount and priestCount cannot exceed the number of joined players");
        }

        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int index = 0;
        for (int i = 0; i < werewolfCount; i++) {
            shuffled.get(index++).setRole(Role.WEREWOLF);
        }
        for (int i = 0; i < priestCount; i++) {
            shuffled.get(index++).setRole(Role.PRIEST);
        }
        while (index < shuffled.size()) {
            shuffled.get(index++).setRole(Role.VILLAGER);
        }

        playerRepository.saveAll(shuffled);
    }

    /**
     * Applies phase-entry side effects and moves the room into the given
     * phase. Resolving the night's werewolf kill happens here, when
     * entering {@link GamePhase#MORNING_REVEAL}, so the reveal card
     * always reflects the outcome of that night. If that kill ends the
     * game, {@code phase} is overridden to {@link GamePhase#GAME_OVER}.
     */
    private void enterPhase(Room room, GamePhase phase) {
        if (phase == GamePhase.MORNING_REVEAL) {
            Long victimId = room.getPendingWerewolfVictimId();
            if (victimId != null) {
                Player victim = playerRepository.findById(victimId)
                        .orElseThrow(() -> new PlayerNotFoundException(victimId));
                victim.kill();
                playerRepository.save(victim);
            }
            room.setLastNightVictimId(victimId);
            room.setPendingWerewolfVictimId(null);

            Alignment winner = checkWinner(room);
            if (winner != null) {
                endGame(room, winner);
                return;
            }
        }

        room.setPhase(phase);
    }

    private void resolveVoteAndAdvance(Room room) {
        Long voteVictimId = room.getPendingVoteVictimId();
        if (voteVictimId != null) {
            Player voted = playerRepository.findById(voteVictimId)
                    .orElseThrow(() -> new PlayerNotFoundException(voteVictimId));
            voted.kill();
            playerRepository.save(voted);
        }

        room.setPendingVoteVictimId(null);
        room.setPendingPriestTargetId(null);
        room.setPriestCheckResult(null);
        room.setLastNightVictimId(null);

        Alignment winner = checkWinner(room);
        if (winner != null) {
            endGame(room, winner);
        } else {
            room.setRoundNumber(room.getRoundNumber() + 1);
            enterPhase(room, GamePhase.NIGHT_START);
        }
    }

    private void endGame(Room room, Alignment winner) {
        room.setWinner(winner);
        room.setPhase(GamePhase.GAME_OVER);
    }

    private Alignment checkWinner(Room room) {
        List<Player> alivePlayers = playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId());

        long aliveEvil = alivePlayers.stream()
                .filter(player -> player.getRole().getAlignment() == Alignment.EVIL)
                .count();
        long aliveGood = alivePlayers.size() - aliveEvil;

        if (aliveEvil == 0) {
            return Alignment.GOOD;
        }
        if (aliveEvil >= aliveGood) {
            return Alignment.EVIL;
        }
        return null;
    }

    /**
     * Whether a priest would still be alive after applying tonight's
     * pending werewolf kill (which is only actually persisted when
     * entering {@link GamePhase#MORNING_REVEAL}).
     */
    private boolean hasAlivePriestExcluding(Room room, Long excludedPlayerId) {
        return playerRepository.findByRoomIdAndAliveTrueOrderByJoinedAtAsc(room.getId()).stream()
                .filter(player -> !player.getId().equals(excludedPlayerId))
                .anyMatch(player -> player.getRole() == Role.PRIEST);
    }

    private Player requireAlivePlayerInRoom(Room room, Long playerId) {
        Player player = playerRepository.findById(playerId)
                .filter(p -> p.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> new PlayerNotFoundException(playerId));

        if (!player.isAlive()) {
            throw new InvalidGamePhaseException("Player " + playerId + " is not alive");
        }
        return player;
    }

    private void requirePhase(Room room, GamePhase expected) {
        ensureGameStarted(room);
        if (room.getPhase() != expected) {
            throw new InvalidGamePhaseException(
                    "This action requires phase " + expected + " but the room is in " + room.getPhase());
        }
    }

    private void requireSelectionMade(Long pendingId, String message) {
        if (pendingId == null) {
            throw new InvalidGamePhaseException(message);
        }
    }

    private void ensureGameStarted(Room room) {
        if (room.getStatus() != RoomStatus.STARTED || room.getPhase() == null) {
            throw new InvalidGamePhaseException("The game has not started yet");
        }
    }

    private MasterGameStateResponse buildMasterGameState(Room room) {
        List<MasterPlayerView> players = playerRepository.findByRoomIdOrderByJoinedAtAsc(room.getId()).stream()
                .map(MasterPlayerView::from)
                .toList();

        return new MasterGameStateResponse(
                room.getCode(),
                room.getPhase(),
                room.getRoundNumber(),
                players,
                room.getPendingWerewolfVictimId(),
                room.getPendingPriestTargetId(),
                room.getPriestCheckResult(),
                room.getLastNightVictimId(),
                room.getPendingVoteVictimId(),
                room.getWinner());
    }

    private void broadcastGameUpdated(Room room) {
        messagingTemplate.convertAndSend("/topic/rooms/" + room.getCode() + "/game",
                new GameUpdatedMessage(room.getCode()));
    }
}
