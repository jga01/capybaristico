package com.jamestiago.capycards.service;

import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.GameEngine;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.commands.GameCommand;
import com.jamestiago.capycards.game.events.GameEvent;
import com.jamestiago.capycards.game.events.GameStartedEvent;
import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.model.Rarity;
import com.jamestiago.capycards.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GameService {
  private static final Logger logger = LoggerFactory.getLogger(GameService.class);
  private final Map<String, Game> activeGames = new ConcurrentHashMap<>();
  private List<Card> allCardDefinitions;
  private final CardRepository cardRepository;
  private final GameEngine gameEngine;

  @Autowired
  public GameService(CardRepository cardRepository, GameEngine gameEngine) {
    this.cardRepository = cardRepository;
    this.gameEngine = gameEngine;
    logger.info("GameService initialized.");
  }

  @PostConstruct
  public void initializeCardDefinitions() {
    loadAllCardDefinitions();
  }

  private void loadAllCardDefinitions() {
    this.allCardDefinitions = cardRepository.findAll();
    if (this.allCardDefinitions.isEmpty()) {
      logger.warn("No card definitions found in the database! Attempting to seed.");
      seedPlaceholderCards();
      this.allCardDefinitions = cardRepository.findAll();
    }
  }

  private void seedPlaceholderCards() {
    logger.info("Seeding placeholder cards into the database (if empty)...");
    if (cardRepository.count() > 0) {
      logger.info("Database already contains cards. Skipping seed.");
      return;
    }

    List<Card> cardsToSeed = new ArrayList<>();

    cardsToSeed.add(new Card("CAP001", "Kahina", "Indigenous", 24, 4, 2,
        "Heals +1 life whenever another card is damaged. At 50 life, ATK becomes 50, but killing any card causes her to die. Dies instantly if she damages a Capybara.",
        """
            [
              {"trigger":"ON_DAMAGE_TAKEN_OF_ANY","action":"HEAL_TARGET","params":{"targets":"SELF","amount":1}},
              {"trigger":"ON_DAMAGE_TAKEN","condition":{"type":"VALUE_COMPARISON","params":{"sourceValue":{"source":"STAT","statName":"LIFE","cardContext":"SELF"},"operator":"GREATER_THAN","targetValue":49}}, "action":"APPLY_FLAG","params":{"targets":"SELF","flagName":"kahina_frenzy","value":true}}
            ]
            """,
        Rarity.SUPER_RARE, "kahina.png",
        "The earth remembers."));
    cardsToSeed.add(new Card("CAP002", "TrainofSkeleton", "Undead", 8, 1, 0,
        "On entry and death, deals 4 AoE damage (including self). If dies to own effect, returns next turn. Gains +10 life if damages an American (once).",
        """
            [
                  {"trigger":"ON_PLAY","action":"DEAL_DAMAGE","params":{"targets":"ALL_CARDS_ON_FIELD", "amount": 4}},
                  {"trigger":"ON_DEATH","action":"DEAL_DAMAGE","params":{"targets":"ALL_CARDS_ON_FIELD", "amount": 4}},
                  {
                    "trigger": "ON_DEATH",
                    "condition": {"type": "TRIGGER_SOURCE_IS_SELF"},
                    "action": "SCHEDULE_ACTION",
                    "params": {
                      "delayInTurns": 2,
                      "scheduledEffect": {
                        "action": "REAPPEAR",
                        "params": {}
                      }
                    }
                  }
                ]""",
        Rarity.SUPER_RARE, "train.png", "The ride never ends."));
    cardsToSeed.add(new Card("CAP003", "Leonico", "Femboy", 3, 0, 20,
        "Immune to all damage. If a European card is played, becomes ATK 1 / DEF 5 / LIFE 5. Gains +2 damage vs Indigenous and Capybara. Dies instantly if attacked by European.",
        """
            [
              {"trigger":"CONTINUOUS_DEFENSIVE","action":"MODIFY_INCOMING_DAMAGE","params":{"amount":0,"mode":"SET_ABSOLUTE"}},
              {
                "trigger": "ON_SUMMON",
                "condition": { "type": "TARGET_HAS_TYPE", "params": {"typeName": "European"} },
                "action": "CHAINED_EFFECTS",
                "params": {
                  "effects": [
                    {"action": "APPLY_FLAG", "params": {"targets": "SELF", "flagName": "transformed_by_european", "value": true}},
                    {"action": "BUFF_STAT", "params": {"targets": "SELF", "stat": "MAX_LIFE", "amount": 2, "isPermanent": true}},
                    {"action": "BUFF_STAT", "params": {"targets": "SELF", "stat": "ATK", "amount": 1, "isPermanent": true}},
                    {"action": "DEBUFF_STAT", "params": {"targets": "SELF", "stat": "DEF", "amount": 15, "isPermanent": true}}
                  ]
                }
              }
            ]
            """,
        Rarity.UNCOMMON, "leonico.png",
        "Don't judge a book..."));
    cardsToSeed.add(new Card("CAP004", "Floppy", "European", 1, 12, 0,
        "If he kills a card, can attack again (except against European/Capybara). If he kills 2 in a row, gains +4 DEF, +8 LIFE, ATK drops to 8.",
        """
            [
              {
                "trigger": "ON_DAMAGE_DEALT",
                "condition": {"type": "TARGET_IS_DESTROYED"},
                "action": "CHAINED_EFFECTS",
                "params": {
                  "effects": [
                    {
                      "action": "APPLY_FLAG",
                      "params": {"targets": "SELF", "flagName": "canAttackAgainThisTurn", "value": true, "duration": "TURN"}
                    },
                    {
                      "action": "MODIFY_FLAG",
                      "params": {"targets": "SELF", "flagName": "killStreakThisTurn", "mode": "INCREMENT", "amount": 1}
                    }
                  ]
                }
              },
              {
                "trigger": "ON_DAMAGE_DEALT",
                "condition": {
                  "type": "VALUE_COMPARISON",
                  "params": {
                    "sourceValue": {"source": "FLAG_VALUE", "flagName": "killStreakThisTurn", "cardContext": "SELF"},
                    "operator": "EQUALS",
                    "targetValue": 2
                  }
                },
                "action": "CHAINED_EFFECTS",
                "params": {
                  "effects": [
                    {"action": "BUFF_STAT", "params": {"targets": "SELF", "stat": "DEF", "amount": 4, "isPermanent": true}},
                    {"action": "BUFF_STAT", "params": {"targets": "SELF", "stat": "MAX_LIFE", "amount": 8, "isPermanent": true}},
                    {"action": "DEBUFF_STAT", "params": {"targets": "SELF", "stat": "ATK", "amount": 4, "isPermanent": true}},
                    {
                      "action": "MODIFY_FLAG",
                      "params": {"targets": "SELF", "flagName": "killStreakThisTurn", "mode": "SET", "amount": 0}
                    }
                  ]
                }
              }
            ]
            """,
        Rarity.RARE,
        "floppy.png",
        "A whirlwind of... something."));
    cardsToSeed.add(new Card("CAP005", "Swettie", "European", 18, 3, 3,
        "Has two sides. Killing one doesn’t kill the other. Can use 'Scottish Von' once: revives the other side instead of attacking, but loses half her life.",
        """
            [
              {"trigger":"ON_DEATH","condition":{"type":"SELF_HAS_FLAG","params":{"flagName":"swettie_side_b_used","mustBeAbsent":true}}, "action":"CHAINED_EFFECTS", "params": { "effects": [
                {"action":"HEAL_TARGET", "params": {"targets":"SELF", "amount": 999}},
                {"action":"APPLY_FLAG", "params": {"targets":"SELF", "flagName": "swettie_side_b_used", "value": true}}
              ]}}
            ]
            """,
        Rarity.SUPER_RARE,
        "swettie.png", "Two faces of fortitude."));
    cardsToSeed.add(new Card("CAP006", "Aop", "American,Capybara", 1, 1, 1,
        "Immune to attacks over 7 damage. Can roll a die each turn instead of attacking: roll 1 or 6 = +4 DEF, +1 ATK.",
        """
            [
              {"trigger":"CONTINUOUS_DEFENSIVE","action":"MODIFY_INCOMING_DAMAGE","condition":{"type":"VALUE_COMPARISON","params":{"sourceValue":{"source":"EVENT_DATA","key":"damageAmount"},"operator":"GREATER_THAN","targetValue":7}},"params":{"amount":0,"mode":"SET_ABSOLUTE"}},
              {"trigger":"ACTIVATED", "abilityOptionIndex":0, "name":"Roll a Die", "description":"Roll a six-sided die. On a 1 or 6, Aop gains +1 ATK and +4 DEF for the turn.", "requiresTarget":"NONE", "action":"CHOOSE_RANDOM_EFFECT","params":{"choices":[
                {"action":"CHAINED_EFFECTS", "params":{"effects": [{"action":"BUFF_STAT","params":{"targets":"SELF","stat":"DEF","amount":4,"isPermanent":false}}, {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"ATK","amount":1,"isPermanent":false}}]}},
                {"action":"GAME_LOG_MESSAGE", "params": {"message": "Aop rolls the die... nothing happens."}},
                {"action":"GAME_LOG_MESSAGE", "params": {"message": "Aop rolls the die... nothing happens."}},
                {"action":"GAME_LOG_MESSAGE", "params": {"message": "Aop rolls the die... nothing happens."}},
                {"action":"GAME_LOG_MESSAGE", "params": {"message": "Aop rolls the die... nothing happens."}},
                {"action":"CHAINED_EFFECTS", "params":{"effects": [{"action":"BUFF_STAT","params":{"targets":"SELF","stat":"DEF","amount":4,"isPermanent":false}}, {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"ATK","amount":1,"isPermanent":false}}]}}
              ]}}
            ]
            """,
        Rarity.COMMON, "aop.png", "Surprisingly resilient."));
    cardsToSeed.add(new Card("CAP007", "Aral", "Capybara", 9, 5, 3, "Does not get hit by Undead.",
        """
            [
              {"trigger":"CONTINUOUS_DEFENSIVE","action":"MODIFY_INCOMING_DAMAGE","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"Undead"}},"params":{"amount":0,"mode":"SET_ABSOLUTE"}}
            ]
            """,
        Rarity.COMMON, "aral.png", "Chill and unbothered."));
    cardsToSeed.add(new Card("CAP008", "Jamestiago", "Indigenous", 10, 3, 1,
        "With Menino-Veneno: +5 LIFE & +1 ATK. If hit by Indigenous/European, heals +1. With Totem: +10 LIFE & +1 ATK.",
        """
            [
              {"trigger":"ON_DAMAGE_TAKEN", "condition":{"type":"SOURCE_HAS_TYPE", "params":{"typeName":"European"}}, "action":"HEAL_TARGET", "params":{"targets":"SELF", "amount":1}},
              {"trigger":"ON_DAMAGE_TAKEN", "condition":{"type":"SOURCE_HAS_TYPE", "params":{"typeName":"Indigenous"}}, "action":"HEAL_TARGET", "params":{"targets":"SELF", "amount":1}},
              {"trigger":"CONTINUOUS_AURA", "condition":{"type":"FRIENDLY_CARD_IN_PLAY", "params":{"cardId":"CAP011"}}, "action":"APPLY_AURA_BUFF", "params":{"targets":"SELF", "buffs":[{"stat":"MAX_LIFE", "amount":5}, {"stat":"ATK", "amount":1}]}},
              {"trigger":"CONTINUOUS_AURA", "condition":{"type":"FRIENDLY_CARD_IN_PLAY", "params":{"cardId":"CAP009"}}, "action":"APPLY_AURA_BUFF", "params":{"targets":"SELF", "buffs":[{"stat":"MAX_LIFE", "amount":10}, {"stat":"ATK", "amount":1}]}}
            ]
            """,
        Rarity.RARE, "jamestiago.png", "The storyteller."));
    cardsToSeed.add(new Card("CAP009", "Totem", "Indigenous", 6, 2, 1,
        "If Jamestiago is in play, both get +10 LIFE and +1 ATK.",
        """
            [
              {"trigger": "CONTINUOUS_AURA", "condition": {"type": "FRIENDLY_CARD_IN_PLAY", "params": {"cardId": "CAP008"}}, "action": "APPLY_AURA_BUFF", "params": {"targets": "SELF", "buffs": [{"stat": "MAX_LIFE", "amount": 10}, {"stat": "ATK", "amount": 1}]}},
              {"trigger": "CONTINUOUS_AURA", "action": "APPLY_AURA_BUFF", "params": {"targets": "FRIENDLY_CARD_WITH_ID", "context": {"cardId": "CAP008"}, "buffs": [{"stat": "MAX_LIFE", "amount": 10}, {"stat": "ATK", "amount": 1}]}}
            ]
            """,
        Rarity.COMMON, "totem.png",
        "Ancient power awakens."));
    cardsToSeed.add(new Card("CAP010", "Rossome", "American,Capybara", 2, 2, 3,
        "+2 damage from Kahina or Swettie. +5 DEF from American/Capybara damage.",
        """
            [
              {"trigger":"CONTINUOUS_DEFENSIVE","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"American"}},"action":"MODIFY_INCOMING_DAMAGE","params":{"mode":"REDUCE_BY","amount":5}},
              {"trigger":"CONTINUOUS_DEFENSIVE","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"Capybara"}},"action":"MODIFY_INCOMING_DAMAGE","params":{"mode":"REDUCE_BY","amount":5}}
            ]
            """,
        Rarity.UNCOMMON,
        "rossome.png", "Takes one for the team."));
    cardsToSeed.add(new Card("CAP011", "Menino-Veneno", "Capybara", 9, 5, 5,
        "Can’t attack unless Caioba is in play. If attacked while Caioba is NOT in play, dies instantly. With Caioba, gets double DEF.",
        """
            [
              {"trigger":"ON_DAMAGE_TAKEN","condition":{"type":"FRIENDLY_CARD_IN_PLAY","params":{"cardId":"CAP016", "mustBeAbsent": true}}, "action":"DESTROY_CARD", "params":{"targets":"SELF"}},
              {"trigger":"CONTINUOUS_AURA", "condition":{"type":"FRIENDLY_CARD_IN_PLAY", "params":{"cardId":"CAP016"}}, "action":"APPLY_AURA_BUFF", "params":{"targets":"SELF", "buffs":[{"stat":"DEF", "amount":{"source":"STAT", "statName":"DEF", "cardContext":"SELF"}}]}},
              {
                "trigger": "CONTINUOUS_AURA",
                "condition": {"type": "FRIENDLY_CARD_IN_PLAY", "params": {"cardId": "CAP016", "mustBeAbsent": true}},
                "action": "APPLY_AURA_BUFF",
                "params": {
                  "targets": "SELF",
                  "flags": {
                    "status_cannot_attack_AURA": true
                  }
                }
              }
            ]
            """,
        Rarity.UNCOMMON,
        "menino_veneno.png", "Small but fierce... with friends."));
    cardsToSeed.add(new Card("CAP012", "Crazysoup", "European", 8, 6, 0,
        "50/50 before attack: Heads = double damage; Tails = self-damage. If Floppy is on field, can redirect damage to him.",
        """
            [
              {"trigger":"ON_ATTACK_DECLARE","action":"CHOOSE_RANDOM_EFFECT","params":{"choices":[
                {"action":"APPLY_FLAG","params":{"targets":"SELF", "flagName":"double_damage_this_attack", "value":true, "duration":"TURN"}},
                {"action":"DEAL_DAMAGE","params":{"targets":"SELF","amount":6}}
              ]}}
            ]
            """,
        Rarity.RARE, "crazysoup.png",
        "What's in this stuff?!"));
    cardsToSeed.add(new Card("CAP013", "AppleofEcho", "Undead", 1, 1, 15,
        "Cannot be targeted unless all other cards are dead. After 5 turns, vanishes and returns 3 turns later with double ATK. Ignores DEF vs Capybaras.",
        """
            [
              {
                "trigger": "CONTINUOUS_AURA",
                "condition": {
                  "type": "VALUE_COMPARISON",
                  "params": {
                    "sourceValue": {"source": "DYNAMIC_COUNT", "countType": "OTHER_FRIENDLY_CARDS"},
                    "operator": "GREATER_THAN",
                    "targetValue": 0
                  }
                },
                "action": "APPLY_AURA_BUFF",
                "params": {
                  "targets": "SELF",
                  "flags": { "status_cannot_be_targeted_AURA": true }
                }
              },
              {
                "trigger": "START_OF_TURN_SELF",
                "action": "MODIFY_FLAG",
                "params": {
                  "targets": "SELF",
                  "flagName": "turnsOnField",
                  "mode": "INCREMENT"
                }
              },
              {
                "trigger": "START_OF_TURN_SELF",
                "condition": {
                  "type": "VALUE_COMPARISON",
                  "params": {
                    "sourceValue": {
                      "source": "FLAG_VALUE",
                      "flagName": "turnsOnField",
                      "cardContext": "SELF"
                    },
                    "operator": "EQUALS",
                    "targetValue": 5
                  }
                },
                "action": "CHAINED_EFFECTS",
                "params": {
                  "effects": [
                    {"action": "VANISH", "params": {}},
                    {
                      "action": "SCHEDULE_ACTION",
                      "params": {
                        "delayInTurns": 6,
                        "scheduledEffect": {
                          "action": "REAPPEAR"
                        }
                      }
                    }
                  ]
                }
              }
            ]
            """,
        Rarity.RARE,
        "appleofecho.png", "Now you see me..."));
    cardsToSeed.add(new Card("CAP014", "Hoffman", "European", 15, 5, 0,
        "Heals when damaged. Attacks cause 2 self-damage. Immune to Train's AoE.",
        """
            [
              {"trigger":"ON_DAMAGE_TAKEN","action":"HEAL_TARGET","params":{"targets":"SELF","amount":{"source":"EVENT_DATA","key":"damageAmount"}}},
              {"trigger":"ON_ATTACK_DECLARE","action":"DEAL_DAMAGE","params":{"targets":"SELF","amount":2}},
              {"trigger":"CONTINUOUS_DEFENSIVE","action":"MODIFY_INCOMING_DAMAGE","condition":{"type":"SOURCE_HAS_CARD_ID","params":{"cardId":"CAP002"}},"params":{"amount":0,"mode":"SET_ABSOLUTE"}}
            ]
            """,
        Rarity.COMMON,
        "hoffman.png",
        "Pain is gain?"));
    cardsToSeed.add(new Card("CAP015", "PH", "Capybara", 10, 1, 0,
        "If Jamestiago or Menino-Veneno are in play, heals them +1 instead of attacking. If Olivio is on the field, PH gains +15 LIFE & +5 ATK, but Olivio dies.",
        """
            [
              {"trigger":"ON_PLAY","condition":{"type":"FRIENDLY_CARD_IN_PLAY","params":{"cardId":"CAP019"}},"action":"CHAINED_EFFECTS","params":{"effects":[
                {"action":"DESTROY_CARD","params":{"targets":"FRIENDLY_CARD_WITH_ID", "context": {"cardId": "CAP019"}}},
                {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"MAX_LIFE","amount":15,"isPermanent":true}},
                {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"ATK","amount":5,"isPermanent":true}}
              ]}},
              {"trigger":"ACTIVATED", "abilityOptionIndex":0, "name":"Heal Ally +1", "description":"Heal a friendly Jamestiago or Menino-Veneno for 1 life instead of attacking.", "requiresTarget":"OWN_FIELD_CARD", "action":"HEAL_TARGET","params":{"targets":"ACTIVATION_CONTEXT_TARGET","amount":1}}
            ]
            """,
        Rarity.RARE,
        "ph.png",
        "A noble sacrifice."));
    cardsToSeed.add(new Card("CAP016", "Caioba", "American", 1, 1, 10,
        "Can swap in for Menino when attacked. Gets +10 LIFE and +3 ATK with Menino. If Menino dies, becomes Undead with -3 ATK, -10 damage.",
        """
            [
              {"trigger":"CONTINUOUS_AURA", "condition":{"type":"FRIENDLY_CARD_IN_PLAY", "params":{"cardId":"CAP011"}}, "action":"APPLY_AURA_BUFF", "params":{"targets":"SELF", "buffs":[{"stat":"MAX_LIFE", "amount":10}, {"stat":"ATK", "amount":3}]}}
            ]
            """,
        Rarity.RARE,
        "caioba.png",
        "The protector."));

    Card tomateCard = new Card("CAP017", "Tomate", "American", 5, 1, 5,
        "Cannot be played directly. Appears when only 3 cards remain in deck. Grants +5 DEF to all 3. After 4 turns, duplicates a card.",
        "[]", Rarity.LEGENDARY, "tomate.png", "The late game surprise.");
    tomateCard.setDirectlyPlayable(false);
    cardsToSeed.add(tomateCard);

    cardsToSeed.add(new Card("CAP018", "Gloire", "American", 6, 1, 0,
        "If damaged by European, stuns attacker 1 turn. Flip: heal +5 to any card or -3 damage. If dies by flip, revives as Undead (no effect). (VARIABLE LIFE: copy the highest life in field when joining. max 6)",
        """
            [
              {"trigger":"ON_DAMAGE_TAKEN","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"European"}},"action":"APPLY_FLAG","params":{"targets":"EVENT_SOURCE","flagName":"status_silenced", "value": true, "duration": "TURN"}}
            ]
            """,
        Rarity.RARE,
        "gloire.png",
        "Everchanging fate."));
    cardsToSeed.add(new Card("CAP019", "Olivio", "Capybara", 20, 3, 6,
        "Gains +5 LIFE and +2 ATK when another Capybara dies. If PH is in play, Olivio dies instantly and PH gains +15 LIFE and +5 ATK. If Kahina is on field, she dies on turn 3. Once per game: deal 3 damage to all non-Capybara cards.",
        """
            [
              {"trigger":"ON_DEATH_OF_ANY","condition":{"type":"TARGET_HAS_TYPE","params":{"typeName":"Capybara"}},"action":"CHAINED_EFFECTS","params":{"effects":[
                {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"MAX_LIFE","amount":5,"isPermanent":true}},
                {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"ATK","amount":2,"isPermanent":true}},
                {"action":"HEAL_TARGET","params":{"targets":"SELF","amount":5}}
              ]}}
            ]
            """,
        Rarity.RARE,
        "olivio.png",
        "The Capybara King."));
    cardsToSeed.add(new Card("CAP020", "Makachu", "Capybara,Undead", 10, 2, 1,
        "Gains +1 ATK if Menino is in play. Dies instantly if Menino attacks it. Can flip to 'Makachuva': once per game Rain = -2 damage to all non-Capybaras.",
        """
            [
              {"trigger":"CONTINUOUS_AURA", "condition":{"type":"FRIENDLY_CARD_IN_PLAY", "params":{"cardId":"CAP011"}}, "action":"APPLY_AURA_BUFF", "params":{"targets":"SELF", "buffs":[{"stat":"ATK", "amount":1}]}},
              {"trigger":"ON_DEFEND","condition":{"type":"SOURCE_HAS_CARD_ID","params":{"cardId":"CAP011"}},"action":"DESTROY_CARD","params":{"targets":"SELF"}},
              {"trigger":"ACTIVATED", "abilityOptionIndex":0, "name":"Rain", "description":"Deal 2 damage to all non-Capybara cards on the field. (Once per game)", "requiresTarget":"NONE", "action":"DEAL_DAMAGE","params":{"targets":"ALL_NON_CAPYBARA_CARDS_ON_FIELD","amount":2}}
            ]
            """,
        Rarity.UNCOMMON, "makachu.png",
        "Pika-bara?"));
    cardsToSeed.add(new Card("CAP021", "Chemadam", "European,Undead", 11, 2, 0,
        "Each turn 50%: +5 to enemy or -3 to self. If Floppy is in play, copies his skill at 50%. If dies to own effect: 50% chance to become Floppy or Crazysoup.",
        """
            [
              {"trigger":"START_OF_TURN_SELF","action":"CHOOSE_RANDOM_EFFECT","params":{"choices":[
                {"action":"BUFF_STAT","params":{"targets":"ALL_ENEMY_CARDS_ON_FIELD","stat":"ATK","amount":5,"isPermanent":false}},
                {"action":"DEAL_DAMAGE","params":{"targets":"SELF","amount":3}}
              ]}},
              {
                "trigger": "ON_DEATH",
                "condition": {"type": "TRIGGER_SOURCE_IS_SELF"},
                "action": "CHOOSE_RANDOM_EFFECT",
                "params": {
                  "choices": [
                    {"action": "TRANSFORM_CARD", "params": {"targets": "SELF", "newCardId": "CAP004"}},
                    {"action": "TRANSFORM_CARD", "params": {"targets": "SELF", "newCardId": "CAP012"}}
                  ]
                }
              }
            ]
            """,
        Rarity.SUPER_RARE,
        "chemadam.png", "Unpredictable concoction."));
    cardsToSeed.add(new Card("CAP022", "Apolo", "Undead", 8, 0, 0,
        "Ignores enemy DEF. When hit: flip coin. Heads = heal +3, tails = gain +3 ATK. Max 4 triggers.",
        """
            [
              {"trigger": "CONTINUOUS_AURA", "action": "APPLY_AURA_BUFF", "params": {"targets": "SELF", "flags": {"ignores_defense_AURA": true}}},
              {"trigger":"ON_DAMAGE_TAKEN","condition":{"type":"VALUE_COMPARISON","params":{"sourceValue":{"source":"FLAG_VALUE","flagName":"apolo_triggers","cardContext":"SELF"},"operator":"LESS_THAN","targetValue":4}},"action":"CHAINED_EFFECTS","params":{"effects":[
                {"action":"MODIFY_FLAG","params":{"targets":"SELF","flagName":"apolo_triggers","mode":"INCREMENT"}},
                {"action":"CHOOSE_RANDOM_EFFECT","params":{"choices":[
                  {"action":"HEAL_TARGET","params":{"targets":"SELF","amount":3}},
                  {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"ATK","amount":3,"isPermanent":true}}
                ]}}
              ]}}
            ]
            """,
        Rarity.UNCOMMON,
        "apolo.png",
        "Ghostly strikes."));
    cardsToSeed.add(new Card("CAP023", "Realistjames", "Undead,Femboy", 4, 1, 0,
        "Each attack: +2 DEF. Can survive negative LIFE equal to DEF. Dies if DEF = 0 and LIFE < 0. Instantly dies if hit by American.",
        """
            [
              {"trigger":"ON_ATTACK_DECLARE","action":"BUFF_STAT","params":{"targets":"SELF","stat":"DEF","amount":2,"isPermanent":true}},
              {"trigger":"ON_DAMAGE_TAKEN","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"American"}},"action":"DESTROY_CARD","params":{"targets":"SELF"}}
            ]
            """,
        Rarity.UNCOMMON,
        "realistjames.png", "Defiantly undead."));
    cardsToSeed.add(new Card("CAP024", "Kizer", "American,Femboy", 6, 6, 1,
        "+1 ATK if another American in play. Ignores -1 damage from Capybaras. Can heal +1 to Kahina, Swettie, Ariel, Makachuva or Femboys instead of attacking.",
        """
            [
              {"trigger":"CONTINUOUS_DEFENSIVE","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"Capybara"}},"action":"MODIFY_INCOMING_DAMAGE","params":{"mode":"REDUCE_BY","amount":1}},
              {"trigger":"ACTIVATED", "abilityOptionIndex":0, "name":"Heal Ally +1", "description":"Heal a friendly Kahina, Swettie, Ariel, Makachu, or any Femboy for 1 life instead of attacking.", "requiresTarget":"OWN_FIELD_CARD", "action":"HEAL_TARGET","params":{"targets":"ACTIVATION_CONTEXT_TARGET","amount":1}},
              {"trigger":"CONTINUOUS_AURA", "condition":{"type":"FRIENDLY_CARD_IN_PLAY", "params":{"cardId":"CAP006"}}, "action":"APPLY_AURA_BUFF", "params":{"targets":"SELF", "buffs":[{"stat":"ATK", "amount":1}]}}
            ]
            """,
        Rarity.RARE,
        "kizer.png",
        "Stylish and supportive."));
    cardsToSeed.add(new Card("CAP025", "Ariel", "Indigenous,Femboy", 9, 2, 4,
        "“Dual Affinity”: Gains +4 DEF for each other Indigenous card on the field. If killed by a card, that attacker is silenced for 1 turn. Can heal another card +1 instead of attacking.",
        """
            [
              {"trigger":"ON_DEATH", "action":"APPLY_FLAG", "params": {"targets":"EVENT_SOURCE", "flagName":"status_silenced", "value": true, "duration": "TURN"}},
              {"trigger":"CONTINUOUS_AURA", "action":"APPLY_AURA_BUFF", "params":{"targets":"SELF", "buffs":[{"stat":"DEF", "amount":{"source":"DYNAMIC_COUNT", "countType":"FRIENDLY_CARDS_WITH_TYPE", "typeName":"Indigenous"}}]}}
            ]
            """,
        Rarity.RARE, "ariel.png",
        "Spirit of the forest."));
    cardsToSeed.add(new Card("CAP026", "Famousbuilder", "European", 8, 2, 2,
        "“Pretentious Interference”: When Famousbuilder enters the field, choose one: (a) Reduce 1 enemy card’s DEF to 0 until end of opponent’s next turn, or (b) prevent one enemy from using its effect this turn. Cannot attack if there is another European card in your field.",
        """
            [
              {
                "trigger": "CONTINUOUS_AURA",
                "condition": {
                  "type": "VALUE_COMPARISON",
                  "params": {
                    "sourceValue": {"source": "DYNAMIC_COUNT", "countType": "FRIENDLY_CARDS_WITH_TYPE", "typeName": "European"},
                    "operator": "GREATER_THAN",
                    "targetValue": 0
                  }
                },
                "action": "APPLY_AURA_BUFF",
                "params": {
                  "targets": "SELF",
                  "flags": {
                    "status_cannot_attack_AURA": true
                  }
                }
              }
            ]
            """,
        Rarity.UNCOMMON,
        "famous.png", "Master of obstruction."));
    cardsToSeed.add(new Card("CAP027", "GGaego", "Undead,European", 1, 9, 0,
        "Once per turn: reduce -2 DEF of any card (min 0), OR give +2 ATK to friendly and +1 ATK to enemy. Can heal self +3 instead of attacking.",
        """
            [
              {"trigger":"ACTIVATED", "abilityOptionIndex":0, "name":"Weaken Armor", "description":"Select a card to permanently reduce its DEF by 2.", "requiresTarget":"ANY_FIELD_CARD", "action":"DEBUFF_STAT","params":{"targets":"ACTIVATION_CONTEXT_TARGET","stat":"DEF","amount":2,"isPermanent":true}},
              {"trigger":"ACTIVATED", "abilityOptionIndex":1, "name":"War Cry", "description":"Give all your cards +2 ATK and all enemy cards +1 ATK for this turn.", "requiresTarget":"NONE", "action":"CHAINED_EFFECTS", "params":{"effects": [
                  {"action":"BUFF_STAT", "params":{"targets":"ALL_FRIENDLY_CARDS_ON_FIELD", "stat":"ATK", "amount": 2, "isPermanent": false}},
                  {"action":"BUFF_STAT", "params":{"targets":"ALL_ENEMY_CARDS_ON_FIELD", "stat":"ATK", "amount": 1, "isPermanent": false}}
              ]}},
              {"trigger":"ACTIVATED", "abilityOptionIndex":2, "name":"Drink Vigor", "description":"GGaego heals itself for 3 LIFE.", "requiresTarget":"NONE", "action":"HEAL_TARGET","params":{"targets":"SELF","amount":3}}
            ]
            """,
        Rarity.RARE, "ggaego.png", "A chaotic force."));
    cardsToSeed.add(new Card("CAP028", "Nox", "Capybara,European", 1, 8, 0,
        "All cards in same field gain +1 ATK while alive. Can mark others with 'scargot' (must have it to attack). Can use 'wine' to self-heal +3 next turn.",
        """
            [
              {"trigger":"ACTIVATED", "abilityOptionIndex":0, "name":"Mark with Scargot", "description":"Mark a card. Marked cards can be attacked.", "requiresTarget":"ANY_FIELD_CARD", "action":"APPLY_FLAG","params":{"targets":"ACTIVATION_CONTEXT_TARGET","flagName":"scargot_mark","value":true,"duration":"PERMANENT"}},
              {"trigger":"ACTIVATED", "abilityOptionIndex":1, "name":"Sip Wine", "description":"Heal self for 3 LIFE at the start of your next turn.", "requiresTarget":"NONE", "action":"APPLY_FLAG","params":{"targets":"SELF","flagName":"is_healing_next_turn","value":true,"duration":"TURN"}},
              {"trigger":"START_OF_TURN_SELF","condition":{"type":"SELF_HAS_FLAG","params":{"flagName":"is_healing_next_turn"}},"action":"CHAINED_EFFECTS","params":{"effects":[
                {"action":"HEAL_TARGET","params":{"targets":"SELF","amount":3}},
                {"action":"APPLY_FLAG","params":{"targets":"SELF","flagName":"is_healing_next_turn","value":null}}
              ]}},
              {"trigger":"CONTINUOUS_AURA", "action":"APPLY_AURA_BUFF", "params":{"targets":"ALL_FRIENDLY_CARDS_ON_FIELD", "buffs":[{"stat":"ATK", "amount":1}]}}
            ]
            """,
        Rarity.RARE,
        "nox.png",
        "The sophisticated strategist."));
    cardsToSeed.add(new Card("CAP029", "Hungrey", "American", 6, 10, 0,
        "When attacked, gains +2 ATK permanently. Then, deals damage to its attacker equal to its new ATK.",
        """
            [
              {"trigger":"ON_DAMAGE_TAKEN","action":"CHAINED_EFFECTS","params":{"effects":[
                {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"ATK","amount":2,"isPermanent":true}},
                {"action":"DEAL_DAMAGE","params":{"targets":"EVENT_SOURCE","amount":{"source":"STAT","statName":"ATK","cardContext":"SELF"}}}
              ]}}
            ]
            """,
        Rarity.UNCOMMON, "hungrey.png", "Always ready for a fight."));
    cardsToSeed.add(new Card("CAP030", "Andgames", "Capybara", 7, 8, 0,
        "If Kizer is in same field: +3 LIFE and +2 ATK (once).",
        """
            [
              {"trigger":"ON_PLAY","condition":{"type":"FRIENDLY_CARD_IN_PLAY","params":{"cardId":"CAP024"}},"action":"CHAINED_EFFECTS","params":{"effects":[
                {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"MAX_LIFE","amount":3,"isPermanent":true}},
                {"action":"BUFF_STAT","params":{"targets":"SELF","stat":"ATK","amount":2,"isPermanent":true}}
              ]}}
            ]
            """,
        Rarity.UNCOMMON,
        "andgames.png", "Stronger together."));
    cardsToSeed.add(new Card("CAP031", "98pm", "Undead", 1, 11, 0,
        "Only dies one turn after reaching 0 LIFE.",
        """
            [
                  {
                    "trigger": "ON_DEATH",
                    "action": "SCHEDULE_ACTION",
                    "params": {
                      "delayInTurns": 2,
                      "scheduledEffect": {
                        "action": "DESTROY_CARD",
                        "params": {"targets": "SELF"}
                      }
                    }
                  }
                ]""", Rarity.RARE, "98pm.png", "Not quite dead yet."));

    cardRepository.saveAll(cardsToSeed);
    logger.info("Seeded {} placeholder cards.", cardsToSeed.size());
  }

  private List<Card> generatePlayerDeck() {
    if (allCardDefinitions == null || allCardDefinitions.isEmpty()) {
      throw new IllegalStateException("Card definitions not loaded.");
    }
    // FIX: Filter out cards that are not directly playable from the deck pool
    List<Card> playableCards = allCardDefinitions.stream()
        .filter(Card::isDirectlyPlayable)
        .collect(Collectors.toList());

    List<Card> deckInProgress = new ArrayList<>();
    // Now use the filtered list to build the deck
    for (int i = 0; i < 20; i++) {
      deckInProgress.add(playableCards.get(i % playableCards.size()));
    }
    java.util.Collections.shuffle(deckInProgress);
    return deckInProgress;
  }

  public Game createNewGame(String player1DisplayName, String player2DisplayName) {
    if (allCardDefinitions == null || allCardDefinitions.isEmpty()) {
      throw new IllegalStateException("Card definitions not loaded. Cannot create game.");
    }

    Player player1 = new Player(player1DisplayName, generatePlayerDeck());
    Player player2 = new Player(player2DisplayName, generatePlayerDeck());

    Game newGame = new Game(player1, player2, allCardDefinitions);

    newGame.setGameState(Game.GameState.WAITING_FOR_PLAYERS);
    activeGames.put(newGame.getGameId(), newGame);

    MDC.put("gameId", newGame.getGameId());
    logger.info("New game object created. P1: {}, P2: {}", player1DisplayName, player2DisplayName);
    MDC.remove("gameId");

    return newGame;
  }

  public List<GameEvent> startGame(Game game) {
    GameStartedEvent startEvent = new GameStartedEvent(
        game.getGameId(),
        0,
        game.getPlayer1().getPlayerId(),
        game.getPlayer2().getPlayerId(),
        game.getPlayer1().getPlayerId() // Player 1 always starts for now
    );

    // Create a temporary list to hold all startup events
    List<GameEvent> startupEvents = new ArrayList<>();
    startupEvents.add(startEvent);

    // Apply the start event to get the initial state with drawn cards
    game.apply(startEvent);

    logger.info("Game {} started with {} initial events.", game.getGameId(), startupEvents.size());
    return startupEvents;
  }

  public Game getGame(String gameId) {
    return activeGames.get(gameId);
  }

  public List<GameEvent> handleCommand(GameCommand command) {
    Game game = activeGames.get(command.gameId);
    if (game == null) {
      logger.warn("Attempted to handle command for non-existent game: {}", command.gameId);
      return List.of();
    }

    logger.debug("[{}] Service received command: {}", command.gameId, command.getCommandType());

    // The GameEngine now returns a complete list of events, including subsequent
    // death checks.
    List<GameEvent> events = gameEngine.processCommand(game, command);

    if (events.isEmpty()) {
      logger.warn("Command {} from session for game {} resulted in NO events. Action was likely invalid.",
          command.getCommandType(), game.getGameId());
      return List.of(); // Return early if the command was invalid
    }

    logger.debug("[{}] Engine produced {} events. Applying them to game state.", game.getGameId(), events.size());

    // Apply all generated events to the real game state
    for (GameEvent event : events) {
      game.apply(event);
    }

    logger.debug("[{}] Finished applying events. New game state: {}", game.getGameId(), game.getGameState());

    return events;
  }

  public void removeGame(String gameId) {
    // Set MDC to log the final message to the correct game file
    MDC.put("gameId", gameId);
    Game removedGame = activeGames.remove(gameId);
    if (removedGame != null) {
      logger.info("Game {} has ended and is now removed from active games. This log file is now complete.", gameId);
    } else {
      logger.warn("Attempted to remove non-existent game: {}", gameId);
    }
    MDC.remove("gameId"); // Clean up
  }
}