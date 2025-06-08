package com.jamestiago.capycards.game;

import com.jamestiago.capycards.game.dto.CardInstanceDTO;
import com.jamestiago.capycards.game.dto.PlayerStateDTO;
import com.jamestiago.capycards.game.dto.GameStateResponse;
import java.util.stream.Collectors;

public class GameStateMapper {

    public static GameStateResponse createGameStateResponse(Game game, String forWhosePlayerId) {
        GameStateResponse response = new GameStateResponse();
        response.setSuccess(true);
        response.setGameId(game.getGameId());
        response.setMessage("Initial game state.");
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
        }
        dto.setCurrentLife(cardInstance.getCurrentLife());
        dto.setCurrentAttack(cardInstance.getCurrentAttack());
        dto.setCurrentDefense(cardInstance.getCurrentDefense());
        dto.setIsExhausted(cardInstance.isExhausted());
        return dto;
    }
}