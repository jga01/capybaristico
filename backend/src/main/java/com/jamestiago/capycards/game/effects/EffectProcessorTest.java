// package com.jamestiago.capycards.game.effects;

// import com.jamestiago.capycards.game.CardInstance;
// import com.jamestiago.capycards.game.Game;
// import com.jamestiago.capycards.game.Player;
// import com.jamestiago.capycards.model.Card;
// import com.jamestiago.capycards.model.Rarity;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;

// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.Map;

// import static org.assertj.core.api.Assertions.assertThat;

// /*
//  * NOTE: If your IDE still shows errors on imports like `org.junit.jupiter...` or `org.assertj...`,
//  * it's likely a project configuration issue. Follow the IDE-specific steps below to fix it.
//  */
// class EffectProcessorTest {
//     private Game testGame;
//     private Player player1;
//     private Player player2;
//     private EffectProcessor effectProcessor;

//     // Card Definitions for tests
//     private Card buffingCardDef;
//     private Card targetCardDef;
//     private Card damagingCardDef;
//     private Card swettieDef;

//     @BeforeEach
//     void setUp() {
//         // Simple card definitions for testing purposes
//         buffingCardDef = new Card("TEST001", "Buff Bot", "Mech", 10, 1, 1, "Gives stuff.", "[]", Rarity.COMMON, "", "");
//         targetCardDef = new Card("TEST002", "Test Dummy", "Mech", 20, 5, 5, "Takes stuff.", "[]", Rarity.COMMON, "",
//                 "");
//         damagingCardDef = new Card("TEST003", "Damage Bot", "Mech", 10, 3, 1, "Deals stuff.", "[]", Rarity.COMMON, "",
//                 "");
//         swettieDef = new Card("CAP005", "Swettie", "European", 18, 3, 3, "Has two sides.",
//                 "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"SWETTIE\"}}]",
//                 Rarity.SUPER_RARE, "", "");

//         // Create players with empty decks initially
//         player1 = new Player("P1", new ArrayList<>());
//         player2 = new Player("P2", new ArrayList<>());

//         testGame = new Game(player1, player2);
//         testGame.setGameState(Game.GameState.PLAYER_1_TURN); // Set a playable state
//         effectProcessor = new EffectProcessor(testGame);
//     }

//     @Test
//     void testModifyStat_appliesTemporaryAttackBuff() {
//         CardInstance sourceCard = new CardInstance(buffingCardDef);
//         CardInstance targetCard = new CardInstance(targetCardDef);
//         player1.getFieldInternal().set(0, sourceCard);
//         player1.getFieldInternal().set(1, targetCard);

//         int initialAttack = targetCard.getCurrentAttack();

//         // JSON equivalent: { "action": "MODIFY_STAT", "params": { "targets": "SELF",
//         // "stat": "ATK", "amount": 5, "isPermanent": false } }
//         // We simulate the parsed map directly
//         Map<String, Object> effectConfig = new HashMap<>();
//         effectConfig.put("action", "MODIFY_STAT");
//         Map<String, Object> params = new HashMap<>();
//         params.put("targets", "SELF"); // In this test, we'll make the target card its own source for simplicity
//         params.put("stat", "ATK");
//         params.put("amount", 5);
//         params.put("isPermanent", false);
//         effectConfig.put("params", params);

//         // Directly execute the action for a clean unit test
//         runExecuteAction(effectConfig, targetCard, player1, new HashMap<>());

//         assertThat(targetCard.getCurrentAttack()).isEqualTo(initialAttack + 5);

//         // Verify it's temporary by resetting turn state
//         targetCard.resetTurnSpecificState();
//         assertThat(targetCard.getCurrentAttack()).isEqualTo(initialAttack);
//     }

//     @Test
//     void testDealDamage_reducesTargetLife() {
//         CardInstance sourceCard = new CardInstance(damagingCardDef);
//         CardInstance targetCard = new CardInstance(targetCardDef);
//         player1.getFieldInternal().set(0, sourceCard);
//         player2.getFieldInternal().set(0, targetCard);

//         int initialLife = targetCard.getCurrentLife();

//         // JSON: { "action": "DEAL_DAMAGE", "params": { "targets":
//         // "ACTIVATION_CONTEXT_TARGET", "amount": 7 } }
//         Map<String, Object> effectConfig = new HashMap<>();
//         effectConfig.put("action", "DEAL_DAMAGE");
//         Map<String, Object> params = new HashMap<>();
//         params.put("targets", "ACTIVATION_CONTEXT_TARGET");
//         params.put("amount", 7);
//         effectConfig.put("params", params);

//         Map<String, Object> context = new HashMap<>();
//         context.put("targetCardInstanceId", targetCard.getInstanceId());

//         runExecuteAction(effectConfig, sourceCard, player1, context);

//         assertThat(targetCard.getCurrentLife()).isEqualTo(initialLife - 7);
//         assertThat(targetCard.isDestroyed()).isFalse();
//     }

//     @Test
//     void testCondition_valueComparisonGreaterThan() {
//         CardInstance sourceCard = new CardInstance(targetCardDef); // Use Test Dummy, life is 20
//         player1.getFieldInternal().set(0, sourceCard);

//         // Condition: "is my life greater than 10?"
//         Map<String, Object> condition = Map.of(
//                 "type", "VALUE_COMPARISON",
//                 "params", Map.of(
//                         "sourceValue", Map.of(
//                                 "source", "STAT",
//                                 "statName", "LIFE",
//                                 "cardContext", "SELF"),
//                         "operator", "GREATER_THAN",
//                         "targetValue", 10));

//         Map<String, Object> effectConfig = new HashMap<>();
//         effectConfig.put("condition", condition);

//         boolean result = runCheckCondition(effectConfig, sourceCard, player1, new HashMap<>());
//         assertThat(result).isTrue();

//         // Now, set life to 5 and check again.
//         sourceCard.setCurrentLife(5);
//         result = runCheckCondition(effectConfig, sourceCard, player1, new HashMap<>());
//         assertThat(result).isFalse();
//     }

//     @Test
//     void testCustomLogic_swettieFirstDeathSurvives() {
//         CardInstance swettie = new CardInstance(swettieDef);
//         player1.getFieldInternal().set(0, swettie);

//         assertThat(swettie.getBooleanEffectFlag("swettie_side_b_used")).isFalse();

//         // Simulate taking lethal damage
//         swettie.takeDamage(100, null);
//         assertThat(swettie.isDestroyed()).isTrue();

//         // Process the death trigger
//         Map<String, Object> deathContext = new HashMap<>();
//         deathContext.put("isActuallyDead", true); // Default assumption
//         effectProcessor.processTrigger(EffectTrigger.ON_DEATH, swettie, player1, deathContext);

//         // Check the outcome
//         assertThat(swettie.getBooleanEffectFlag("swettie_side_b_used")).isTrue();
//         // The custom logic should have healed her back to full life
//         assertThat(swettie.getCurrentLife()).isEqualTo(swettie.getDefinition().getInitialLife());
//         assertThat(swettie.isDestroyed()).isFalse();
//         // The context flag should be flipped to prevent the Game loop from removing her
//         assertThat(deathContext.get("isActuallyDead")).isEqualTo(false);
//     }

//     @Test
//     void testCustomLogic_swettieSecondDeathIsFinal() {
//         CardInstance swettie = new CardInstance(swettieDef);
//         player1.getFieldInternal().set(0, swettie);
//         swettie.setEffectFlag("swettie_side_b_used", true); // Simulate her already being on her second life

//         // Simulate taking lethal damage
//         swettie.takeDamage(100, null);
//         assertThat(swettie.isDestroyed()).isTrue();

//         // Process the death trigger
//         Map<String, Object> deathContext = new HashMap<>();
//         deathContext.put("isActuallyDead", false); // Default assumption
//         effectProcessor.processTrigger(EffectTrigger.ON_DEATH, swettie, player1, deathContext);

//         // Check the outcome
//         // The custom logic for the second death should NOT heal her and should confirm
//         // she is dead
//         assertThat(swettie.getCurrentLife()).isEqualTo(0);
//         assertThat(swettie.isDestroyed()).isTrue();
//         // The context flag should be flipped to let the Game loop remove her
//         assertThat(deathContext.get("isActuallyDead")).isEqualTo(true);
//     }

//     // Helper method to access private `executeAction` for clean testing
//     private void runExecuteAction(Map<String, Object> effectConfig, CardInstance source, Player owner,
//             Map<String, Object> context) {
//         try {
//             java.lang.reflect.Method method = EffectProcessor.class.getDeclaredMethod("executeAction", Map.class,
//                     CardInstance.class, Player.class, Map.class);
//             method.setAccessible(true);
//             method.invoke(effectProcessor, effectConfig, source, owner, context);
//         } catch (Exception e) {
//             throw new RuntimeException(e);
//         }
//     }

//     // Helper method to access private `checkCondition`
//     private boolean runCheckCondition(Map<String, Object> effectConfig, CardInstance source, Player owner,
//             Map<String, Object> context) {
//         try {
//             java.lang.reflect.Method method = EffectProcessor.class.getDeclaredMethod("checkCondition", Map.class,
//                     CardInstance.class, Player.class, Map.class);
//             method.setAccessible(true);
//             return (boolean) method.invoke(effectProcessor, effectConfig, source, owner, context);
//         } catch (Exception e) {
//             throw new RuntimeException(e);
//         }
//     }
// }