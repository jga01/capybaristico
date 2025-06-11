package com.jamestiago.capycards.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestiago.capycards.game.dto.AbilityInfoDTO;
import com.jamestiago.capycards.game.dto.CardInstanceDTO;
import com.jamestiago.capycards.game.dto.PlayerStateDTO;
import com.jamestiago.capycards.game.dto.GameStateResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameStateMapper {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static GameStateResponse createGameStateResponse(Game game, String forWhosePlayerId, String message) {
        GameStateResponse response = new GameStateResponse();
        response.setSuccess(true);
        response.setGameId(game.getGameId());
        response.setMessage(message);
        response.setCurrentGameState(game.getGameState());
        if (game.getCurrentPlayer() != null) {
            response.setCurrentPlayerId(game.getCurrentPlayer().getPlayerId());
        }
        response.setTurnNumber(game.getTurnNumber());
        response.setViewingPlayerPerspectiveId(forWhosePlayerId);

        response.setPlayer1State(
                mapPlayerToDTO(game.getPlayer1(), game.getPlayer1().getPlayerId().equals(forWhosePlayerId)));
        response.setPlayer2State(
                mapPlayerToDTO(game.getPlayer2(), game.getPlayer2().getPlayerId().equals(forWhosePlayerId)));

        return response;
    }

    private static PlayerStateDTO mapPlayerToDTO(Player player, boolean isThisPlayerTheViewer) {
        if (player == null)
            return null;
        PlayerStateDTO dto = new PlayerStateDTO();
        dto.setPlayerId(player.getPlayerId());
        dto.setDisplayName(player.getDisplayName());
        dto.setDeckSize(player.getDeck().size());
        dto.setDiscardPileSize(player.getDiscardPile().size());
        dto.setHandSize(player.getHand().size());
        dto.setAttacksDeclaredThisTurn(player.getAttacksDeclaredThisTurn());

        if (isThisPlayerTheViewer) {
            dto.setHand(player.getHand().stream()
                    .map(GameStateMapper::mapCardInstanceToDTO)
                    .collect(Collectors.toList()));
        } else {
            dto.setHand(java.util.Collections.emptyList());
        }

        dto.setField(player.getField().stream()
                .map(GameStateMapper::mapCardInstanceToDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    public static CardInstanceDTO mapCardInstanceToDTO(CardInstance cardInstance) {
        if (cardInstance == null)
            return null;

        CardInstanceDTO dto = new CardInstanceDTO();
        dto.setInstanceId(cardInstance.getInstanceId());
        com.jamestiago.capycards.model.Card def = cardInstance.getDefinition();
        if (def != null) {
            dto.setCardId(def.getCardId());
            dto.setName(def.getName());
            dto.setType(def.getType());
            dto.setEffectText(def.getEffectText());
            dto.setRarity(def.getRarity());
            dto.setImageUrl(def.getImageUrl());
            dto.setAbilities(parseAbilities(def.getEffectConfiguration()));
        }

        // MODIFIED: Populate all base and current stats
        dto.setBaseAttack(cardInstance.getBaseAttack());
        dto.setBaseDefense(cardInstance.getBaseDefense());
        dto.setBaseLife(cardInstance.getBaseLife());

        dto.setCurrentLife(cardInstance.getCurrentLife());
        dto.setCurrentAttack(cardInstance.getCurrentAttack());
        dto.setCurrentDefense(cardInstance.getCurrentDefense());

        dto.setIsExhausted(cardInstance.isExhausted());
        dto.setEffectFlags(cardInstance.getAllEffectFlags());

        return dto;
    }

    public static List<AbilityInfoDTO> parseAbilities(String effectConfiguration) {
        if (effectConfiguration == null || effectConfiguration.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<AbilityInfoDTO> abilities = new ArrayList<>();
        try {
            List<Map<String, Object>> effectConfigs = objectMapper.readValue(effectConfiguration,
                    new TypeReference<>() {
                    });

            for (Map<String, Object> config : effectConfigs) {
                if ("ACTIVATED".equalsIgnoreCase((String) config.get("trigger"))) {
                    // Extract data directly from the JSON config.
                    // This requires the JSON to be structured with this data.
                    Integer index = (Integer) config.get("abilityOptionIndex");
                    String name = (String) config.get("name");
                    String description = (String) config.get("description");
                    String requiresTarget = (String) config.get("requiresTarget");

                    if (index != null && name != null) {
                        abilities.add(new AbilityInfoDTO(index, name, description, requiresTarget));
                    }
                }
            }
        } catch (IOException e) {
            // Log this error properly in a real application
            System.err.println("Failed to parse abilities from effectConfiguration: " + e.getMessage());
        }
        return abilities;
    }
}