package com.jamestiago.capycards;

import com.jamestiago.capycards.game.CardInstance;
import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.dto.ActionResultDTO;
import com.jamestiago.capycards.game.dto.GameActionRequest;
import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.model.Rarity;
import com.jamestiago.capycards.repository.CardRepository;
import com.jamestiago.capycards.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
public class GameIntegrationTest {

    @Autowired
    private GameService gameService;

    // The @MockBean warning is expected as of Spring Boot 3.4, but it is still the
    // idiomatic way to mock a bean in a full application context test.
    @MockBean
    private CardRepository cardRepository;

    private Card vanillaCardDef;
    private Card onPlayDamageCardDef;

    @BeforeEach
    void setUp() {
        // Define a simple vanilla card for testing deck generation and basic plays
        vanillaCardDef = new Card("C001", "Vanilla Capy", "Capybara", 10, 5, 5, "It's a capybara.", "[]", Rarity.COMMON,
                "", "");

        // Define a card with an ON_PLAY effect
        String onPlayEffectJson = "[{\"trigger\":\"ON_PLAY\",\"action\":\"DEAL_DAMAGE\",\"params\":{\"targets\":\"ALL_ENEMY_CARDS_ON_FIELD\",\"amount\":2}}]";
        onPlayDamageCardDef = new Card("C002", "Bomber Capy", "Capybara", 8, 3, 3,
                "Deals 2 damage to all enemies on play.", onPlayEffectJson, Rarity.UNCOMMON, "", "");

        // Mock the repository to return a predictable set of cards for deck generation
        List<Card> mockCardList = IntStream.range(0, 20)
                .mapToObj(i -> (i % 5 == 0) ? onPlayDamageCardDef : vanillaCardDef)
                .collect(Collectors.toList());
        when(cardRepository.findAll()).thenReturn(mockCardList);

        // Re-initialize card definitions in the service with our mocked data
        gameService.initializeCardDefinitions();
    }

    @Test
    void testGameCreationAndInitialState() {
        Game game = gameService.createNewGame("Player One", "Player Two");

        assertThat(game).isNotNull();
        assertThat(game.getGameId()).isNotNull();
        assertThat(game.getGameState()).isEqualTo(Game.GameState.PLAYER_1_TURN);
        assertThat(game.getTurnNumber()).isEqualTo(1);

        Player p1 = game.getPlayer1();
        Player p2 = game.getPlayer2();

        assertThat(p1.getDisplayName()).isEqualTo("Player One");
        assertThat(p2.getDisplayName()).isEqualTo("Player Two");

        // Both players should have drawn 5 cards initially
        assertThat(p1.getHand()).hasSize(5);
        assertThat(p2.getHand()).hasSize(5);

        // Decks should have 15 cards left (20 - 5)
        assertThat(p1.getDeck().size()).isEqualTo(15);
        assertThat(p2.getDeck().size()).isEqualTo(15);

        assertThat(game.getCurrentPlayer()).isEqualTo(p1);
    }

    @Test
    void testPlayerPlayCardAndAttackFlow() {
        Game game = gameService.createNewGame("Player One", "Player Two");
        Player p1 = game.getPlayer1();
        Player p2 = game.getPlayer2();

        // === TURN 1: Player 1 ===
        // P1 plays a card to slot 0
        GameActionRequest playCard1 = createAction(game.getGameId(), p1.getPlayerId(),
                GameActionRequest.ActionType.PLAY_CARD);
        playCard1.setHandCardIndex(0);
        playCard1.setTargetFieldSlot(0);
        gameService.handleGameAction(playCard1);

        assertThat(p1.getField().get(0)).isNotNull();

        // P1 ends turn
        GameActionRequest endTurn1 = createAction(game.getGameId(), p1.getPlayerId(),
                GameActionRequest.ActionType.END_TURN);
        gameService.handleGameAction(endTurn1);
        assertThat(game.getCurrentPlayer()).isEqualTo(p2);

        // === TURN 1: Player 2 ===
        // P2 plays a card to slot 0
        GameActionRequest playCard2 = createAction(game.getGameId(), p2.getPlayerId(),
                GameActionRequest.ActionType.PLAY_CARD);
        playCard2.setHandCardIndex(0);
        playCard2.setTargetFieldSlot(0);
        gameService.handleGameAction(playCard2);

        assertThat(p2.getField().get(0)).isNotNull();
        CardInstance p2Defender = p2.getField().get(0);
        int defenderInitialLife = p2Defender.getCurrentLife();

        // P2 ends turn
        GameActionRequest endTurn2 = createAction(game.getGameId(), p2.getPlayerId(),
                GameActionRequest.ActionType.END_TURN);
        gameService.handleGameAction(endTurn2);
        assertThat(game.getCurrentPlayer()).isEqualTo(p1);

        // === TURN 2: Player 1 ===
        assertThat(game.getTurnNumber()).isEqualTo(2);
        CardInstance p1Attacker = p1.getField().get(0);
        assertThat(p1Attacker.isExhausted()).isFalse(); // Should be un-exhausted at start of turn

        // P1 attacks P2's card
        GameActionRequest attackRequest = createAction(game.getGameId(), p1.getPlayerId(),
                GameActionRequest.ActionType.ATTACK);
        attackRequest.setAttackerFieldIndex(0);
        attackRequest.setDefenderFieldIndex(0);

        ActionResultDTO result = gameService.handleGameAction(attackRequest);
        assertThat(result.isSuccess()).isTrue();

        int expectedDamage = Math.max(0, p1Attacker.getCurrentAttack() - p2Defender.getCurrentDefense());
        assertThat(p2Defender.getCurrentLife()).isEqualTo(defenderInitialLife - expectedDamage);
        assertThat(p1Attacker.isExhausted()).isTrue();
        assertThat(p1.getAttacksDeclaredThisTurn()).isEqualTo(1);
    }

    @Test
    void testOnPlayEffectTriggers() {
        Game game = gameService.createNewGame("Player One", "Player Two");
        Player p1 = game.getPlayer1();
        Player p2 = game.getPlayer2();

        // === TURN 1: Player 1 ===
        // P1 does nothing and ends turn
        gameService.handleGameAction(
                createAction(game.getGameId(), p1.getPlayerId(), GameActionRequest.ActionType.END_TURN));

        // === TURN 1: Player 2 ===
        // P2 plays a vanilla card to set up the target
        gameService.handleGameAction(createPlayCardAction(game.getGameId(), p2.getPlayerId(), 0, 0));
        CardInstance p2Card = p2.getField().get(0);
        int p2CardInitialLife = p2Card.getCurrentLife();
        gameService.handleGameAction(
                createAction(game.getGameId(), p2.getPlayerId(), GameActionRequest.ActionType.END_TURN));

        // === TURN 2: Player 1 ===
        // Find the "Bomber Capy" in P1's hand
        int bomberIndexInHand = -1;
        for (int i = 0; i < p1.getHand().size(); i++) {
            if (p1.getHand().get(i).getDefinition().getCardId().equals(onPlayDamageCardDef.getCardId())) {
                bomberIndexInHand = i;
                break;
            }
        }
        assertThat(bomberIndexInHand).as("Bomber card must be in initial hand for this test").isNotEqualTo(-1);

        // P1 plays the Bomber Capy
        ActionResultDTO result = gameService
                .handleGameAction(createPlayCardAction(game.getGameId(), p1.getPlayerId(), bomberIndexInHand, 1));
        assertThat(result.isSuccess()).isTrue();

        // Check if P2's card took 2 damage from the ON_PLAY effect
        assertThat(p2.getField().get(0).getCurrentLife()).isEqualTo(p2CardInitialLife - 2);
    }

    // Helper to create a basic action request
    private GameActionRequest createAction(String gameId, String playerId, GameActionRequest.ActionType type) {
        GameActionRequest request = new GameActionRequest();
        request.setGameId(gameId);
        request.setPlayerId(playerId);
        request.setActionType(type);
        return request;
    }

    // Helper to create a play card action request
    private GameActionRequest createPlayCardAction(String gameId, String playerId, int handIndex, int fieldSlot) {
        GameActionRequest request = createAction(gameId, playerId, GameActionRequest.ActionType.PLAY_CARD);
        request.setHandCardIndex(handIndex);
        request.setTargetFieldSlot(fieldSlot);
        return request;
    }
}