package com.jamestiago.capycards.service;

import com.jamestiago.capycards.game.Game;
import com.jamestiago.capycards.game.Player;
import com.jamestiago.capycards.game.dto.ActionResultDTO;
import com.jamestiago.capycards.game.dto.GameActionRequest;
import com.jamestiago.capycards.model.Card;
import com.jamestiago.capycards.model.Rarity;
import com.jamestiago.capycards.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;

@Service
public class GameService {
        private static final Logger logger = LoggerFactory.getLogger(GameService.class);
        private final Map<String, Game> activeGames = new ConcurrentHashMap<>();
        private List<Card> allCardDefinitions;
        private final CardRepository cardRepository;
        private final Random random = new Random();
        private static final int MAX_DECK_SIZE = 20;
        private static final int MAX_RARE_CARDS = 2;
        private static final int COMMON_SELECTION_WEIGHT = 3;
        private static final int UNCOMMON_SELECTION_WEIGHT = 1;

        @Autowired
        public GameService(CardRepository cardRepository) {
                this.cardRepository = cardRepository;
                logger.info("GameService initialized.");
        }

        @PostConstruct
        public void initializeCardDefinitions() {
                loadAllCardDefinitions();
        }

        private void loadAllCardDefinitions() {
                this.allCardDefinitions = cardRepository.findAll();
                if (this.allCardDefinitions.isEmpty()) {
                        logger.warn("No card definitions found in the database! Attempting to seed some placeholder cards.");
                        seedPlaceholderCards();
                        this.allCardDefinitions = cardRepository.findAll();
                }
                if (this.allCardDefinitions.isEmpty()) {
                        logger.error("CRITICAL: Still no card definitions after attempting to seed. Game creation will fail.");
                } else {
                        logger.info("Loaded {} card definitions from database.", this.allCardDefinitions.size());
                        Map<Rarity, Long> counts = this.allCardDefinitions.stream()
                                        .collect(Collectors.groupingBy(Card::getRarity, Collectors.counting()));
                        logger.info("Card counts by rarity: {}", counts);
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
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"KAHINA\"}}]",
                                Rarity.SUPER_RARE, "kahina.png",
                                "The earth remembers."));
                cardsToSeed.add(new Card("CAP002", "TrainofSkeleton", "Undead", 8, 1, 0,
                                "On entry and death, deals 4 AoE damage (including self). If dies to own effect, returns next turn. Gains +10 life if damages an American (once).",
                                """
                                                [
                                                  {"trigger":"ON_PLAY","action":"CUSTOM_LOGIC","params":{"logicKey":"TRAIN_SKELETON_ON_PLAY_DEATH"}},
                                                  {"trigger":"ON_DEATH","action":"CUSTOM_LOGIC","params":{"logicKey":"TRAIN_SKELETON_ON_PLAY_DEATH"}},
                                                  {"trigger":"ON_DEATH","action":"CUSTOM_LOGIC","params":{"logicKey":"TRAIN_SKELETON_ON_DEATH_REVIVE"}},
                                                  {"trigger":"ON_DAMAGE_DEALT","action":"CUSTOM_LOGIC","params":{"logicKey":"TRAIN_SKELETON_ON_DAMAGE_DEALT"}}
                                                ]
                                                """,
                                Rarity.SUPER_RARE, "train.png", "The ride never ends."));
                cardsToSeed.add(new Card("CAP003", "Leonico", "Femboy", 3, 0, 20,
                                "Immune to all damage. If a European card is played, becomes ATK 1 / DEF 5 / LIFE 5. Gains +2 damage vs Indigenous and Capybara. Dies instantly if attacked by European.",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_DEFENSIVE","action":"MODIFY_INCOMING_DAMAGE","params":{"amount":0,"mode":"SET_ABSOLUTE"}},
                                                  {"trigger":"ON_SUMMON","condition":{"type":"TARGET_HAS_TYPE","params":{"typeName":"European"}},"flags":{"oncePerInstanceId":"leonico_transformed"},"action":"TRANSFORM_CARD","params":{"targets":"SELF","baseAttack":1,"baseDefense":5,"baseLife":5}}
                                                ]
                                                """,
                                Rarity.UNCOMMON, "leonico.png",
                                "Don't judge a book..."));
                cardsToSeed.add(new Card("CAP004", "Floppy", "European", 1, 12, 0,
                                "If he kills a card, can attack again (except against European/Capybara). If he kills 2 in a row, gains +4 DEF, +8 LIFE, ATK drops to 8.",
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"FLOPPY\"}}]", Rarity.RARE,
                                "floppy.png",
                                "A whirlwind of... something."));
                cardsToSeed.add(new Card("CAP005", "Swettie", "European", 18, 3, 3,
                                "Has two sides. Killing one doesn’t kill the other. Can use 'Scottish Von' once: revives the other side instead of attacking, but loses half her life.",
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"SWETTIE\"}}]",
                                Rarity.SUPER_RARE,
                                "swettie.png", "Two faces of fortitude."));
                cardsToSeed.add(new Card("CAP006", "Aop", "American,Capybara", 1, 1, 1,
                                "Immune to attacks over 7 damage. Can roll a die each turn instead of attacking: roll 1 or 6 = +4 DEF, +1 ATK.",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_DEFENSIVE","action":"MODIFY_INCOMING_DAMAGE","condition":{"type":"VALUE_COMPARISON","params":{"sourceValue":{"source":"EVENT_DATA","key":"damageAmount"},"operator":"GREATER_THAN","targetValue":7}},"params":{"amount":0,"mode":"SET_ABSOLUTE"}},
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":0,"condition":{"type":"SELF_HAS_FLAG","params":{"flagName":"aop_diceroll_usedThisTurn","mustBeAbsent":true}},"action":"CUSTOM_LOGIC","params":{"logicKey":"AOP_DICE_ROLL"},"flags":{"setInstanceFlags":[{"name":"aop_diceroll_used","value":true,"duration":"TURN"}]}}
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
                                                  {"trigger":"ON_DAMAGE_TAKEN","action":"HEAL","condition":{"type":"LOGIC_OPERATOR","params":{"operator":"OR","conditions":[{"type":"SOURCE_HAS_TYPE","params":{"typeName":"Indigenous"}},{"type":"SOURCE_HAS_TYPE","params":{"typeName":"European"}}]}},"params":{"targets":"SELF","amount":1}},
                                                  {"trigger":"CONTINUOUS_AURA","action":"APPLY_AURA_BUFF","params":{"synergyCardId":"CAP011","flagName":"jamestiagoMeninoBuffActive","buffs":[{"stat":"MAX_LIFE","amount":5},{"stat":"ATK","amount":1}]}},
                                                  {"trigger":"CONTINUOUS_AURA","action":"APPLY_AURA_BUFF","params":{"synergyCardId":"CAP009","flagName":"jamestiagoTotemBuffActive","buffs":[{"stat":"MAX_LIFE","amount":10},{"stat":"ATK","amount":1}]}}
                                                ]
                                                """,
                                Rarity.RARE, "jamestiago.png", "The storyteller."));
                cardsToSeed.add(new Card("CAP009", "Totem", "Indigenous", 6, 2, 1,
                                "If Jamestiago is in play, both get +10 LIFE and +1 ATK.",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_AURA","action":"APPLY_AURA_BUFF","params":{"synergyCardId":"CAP008","flagName":"totemJamestiagoBuffActive","buffs":[{"stat":"MAX_LIFE","amount":10},{"stat":"ATK","amount":1}]}}
                                                ]
                                                """,
                                Rarity.COMMON, "totem.png",
                                "Ancient power awakens."));
                cardsToSeed.add(new Card("CAP010", "Rossome", "American,Capybara", 2, 2, 3,
                                "+2 damage from Kahina or Swettie. +5 DEF from American/Capybara damage.", "[]",
                                Rarity.UNCOMMON,
                                "rossome.png", "Takes one for the team."));
                cardsToSeed.add(new Card("CAP011", "Menino-Veneno", "Capybara", 9, 5, 5,
                                "Can’t attack unless Caioba is in play. If attacked while Caioba is NOT in play, dies instantly. With Caioba, gets double DEF.",
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"MENINO_VENENO\"}}]",
                                Rarity.UNCOMMON,
                                "menino_veneno.png", "Small but fierce... with friends."));
                cardsToSeed.add(new Card("CAP012", "Crazysoup", "European", 8, 6, 0,
                                "50/50 before attack: Heads = double damage; Tails = self-damage. If Floppy is on field, can redirect damage to him.",
                                """
                                                [
                                                  {"trigger":"ON_ATTACK_DECLARE","action":"CHOOSE_RANDOM_EFFECT","params":{"choices":[{"action":"flags","params":{},"flags":{"setInstanceFlags":[{"name":"double_damage_activeThisAttack","value":true}]}},{"action":"DEAL_DAMAGE","params":{"targets":"SELF","amount":6}}]}}
                                                ]
                                                """,
                                Rarity.RARE, "crazysoup.png",
                                "What's in this stuff?!"));
                cardsToSeed.add(new Card("CAP013", "AppleofEcho", "Undead", 1, 1, 15,
                                "Cannot be targeted unless all other cards are dead. After 5 turns, vanishes and returns 3 turns later with double ATK. Ignores DEF vs Capybaras.",
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"APPLEOFECHO\"}}]",
                                Rarity.RARE,
                                "appleofecho.png", "Now you see me..."));
                cardsToSeed.add(new Card("CAP014", "Hoffman", "European", 15, 5, 0,
                                "Heals when damaged. Attacks cause 2 self-damage. Immune to Train's AoE.",
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"HOFFMAN\"}}]", Rarity.COMMON,
                                "hoffman.png",
                                "Pain is gain?"));
                cardsToSeed.add(new Card("CAP015", "PH", "Capybara", 10, 1, 0,
                                "If Jamestiago or Menino-Veneno are in play, heals them +1 instead of attacking. If Olivio is on the field, PH gains +15 LIFE & +5 ATK, but Olivio dies.",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_AURA","condition":{"type":"FRIENDLY_CARD_IN_PLAY","params":{"cardId":"CAP019"}},"flags":{"oncePerInstanceId":"ph_olivio_effect"},"action":"CHAINED_EFFECTS","params":{"effects":[{"action":"MODIFY_STAT","params":{"targets":"SELF","stat":"ATK","amount":5,"isPermanent":true}},{"action":"MODIFY_STAT","params":{"targets":"SELF","stat":"MAX_LIFE","amount":15,"isPermanent":true}},{"action":"HEAL","params":{"targets":"SELF","amount":15}},{"action":"DESTROY_CARD","params":{"targets":"FRIENDLY_CARD_WITH_ID"},"context":{"cardId":"CAP019"}}]}},
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":0,"condition":{"type":"LOGIC_OPERATOR","params":{"operator":"OR","conditions":[{"type":"TARGET_HAS_CARD_ID","params":{"cardId":"CAP008"}},{"type":"TARGET_HAS_CARD_ID","params":{"cardId":"CAP011"}}]}},"action":"HEAL","params":{"targets":"ACTIVATION_CONTEXT_TARGET","amount":1}}
                                                ]
                                                """,
                                Rarity.RARE,
                                "ph.png",
                                "A noble sacrifice."));
                cardsToSeed.add(new Card("CAP016", "Caioba", "American", 1, 1, 10,
                                "Can swap in for Menino when attacked. Gets +10 LIFE and +3 ATK with Menino. If Menino dies, becomes Undead with -3 ATK, -10 damage.",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_AURA","action":"APPLY_AURA_BUFF","params":{"synergyCardId":"CAP011","flagName":"caiobaMeninoBuffActive","buffs":[{"stat":"MAX_LIFE","amount":10},{"stat":"ATK","amount":3}]}},
                                                  {"trigger":"ON_DEATH_OF_ANY","condition":{"type":"EVENT_TARGET_HAS_CARD_ID","params":{"cardId":"CAP011"}},"action":"TRANSFORM_CARD","params":{"targets":"SELF","baseAttack":0,"baseLife":0}}
                                                ]
                                                """,
                                Rarity.RARE,
                                "caioba.png",
                                "The protector."));
                cardsToSeed.add(new Card("CAP017", "Tomate", "American", 5, 1, 5,
                                "Cannot be played directly. Appears when only 3 cards remain in deck. Grants +5 DEF to all 3. After 4 turns, duplicates a card.",
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"TOMATE\"}}]",
                                Rarity.LEGENDARY, "tomate.png",
                                "The late game surprise."));
                cardsToSeed.add(new Card("CAP018", "Gloire", "American", 6, 1, 0,
                                "If damaged by European, stuns attacker 1 turn. Flip: heal +5 to any card or -3 damage. If dies by flip, revives as Undead (no effect). (VARIABLE LIFE: copy the highest life in field when joining. max 6)",
                                """
                                                [
                                                  {"trigger":"ON_DAMAGE_TAKEN","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"European"}},"action":"APPLY_STATUS","params":{"targets":"EVENT_SOURCE","statusName":"silenced"}},
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":0,"action":"CHOOSE_RANDOM_EFFECT","params":{"choices":[{"action":"HEAL","params":{"targets":"RANDOM_FRIENDLY_CARD_ON_FIELD","amount":5}},{"action":"DEAL_DAMAGE","params":{"targets":"SELF","amount":3}}]}},
                                                  {"action":"CUSTOM_LOGIC","params":{"logicKey":"GLOIRE_REVIVE_AND_LIFE_COPY"}}
                                                ]
                                                """,
                                Rarity.RARE,
                                "gloire.png",
                                "Everchanging fate."));
                cardsToSeed.add(new Card("CAP019", "Olivio", "Capybara", 20, 3, 6,
                                "Gains +5 LIFE and +2 ATK when another Capybara dies. If PH is in play, Olivio dies instantly and PH gains +15 LIFE and +5 ATK. If Kahina is on field, she dies on turn 3. Once per game: deal 3 damage to all non-Capybara cards.",
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"OLIVIO\"}}]", Rarity.RARE,
                                "olivio.png",
                                "The Capybara King."));
                cardsToSeed.add(new Card("CAP020", "Makachu", "Capybara,Undead", 10, 2, 1,
                                "Gains +1 ATK if Menino is in play. Dies instantly if Menino attacks it. Can flip to 'Makachuva': once per game Rain = -2 damage to all non-Capybaras.",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_AURA","action":"APPLY_AURA_BUFF","params":{"synergyCardId":"CAP011","flagName":"makachuMeninoBuffActive","buffs":[{"stat":"ATK","amount":1}]}},
                                                  {"trigger":"ON_DEFEND","condition":{"type":"SOURCE_HAS_CARD_ID","params":{"cardId":"CAP011"}},"action":"DESTROY_CARD","params":{"targets":"SELF"}},
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":0,"flags":{"oncePerInstanceId":"makachu_rain_used"},"action":"DEAL_DAMAGE","params":{"targets":"ALL_NON_CAPYBARA_CARDS_ON_FIELD","amount":2}}
                                                ]
                                                """,
                                Rarity.UNCOMMON, "makachu.png",
                                "Pika-bara?"));
                cardsToSeed.add(new Card("CAP021", "Chemadam", "European,Undead", 11, 2, 0,
                                "Each turn 50%: +5 to enemy or -3 to self. If Floppy is in play, copies his skill at 50%. If dies to own effect: 50% chance to become Floppy or Crazysoup.",
                                """
                                                [
                                                  {"trigger":"START_OF_TURN_SELF","action":"CHOOSE_RANDOM_EFFECT","params":{"choices":[{"action":"MODIFY_STAT","params":{"targets":"ALL_ENEMY_CARDS_ON_FIELD","stat":"ATK","amount":5,"isPermanent":false}},{"action":"DEAL_DAMAGE","params":{"targets":"SELF","amount":3}}]}},
                                                  {"action":"CUSTOM_LOGIC","params":{"logicKey":"CHEMADAM_SPECIALS"}}
                                                ]
                                                """,
                                Rarity.SUPER_RARE,
                                "chemadam.png", "Unpredictable concoction."));
                cardsToSeed.add(new Card("CAP022", "Apolo", "Undead", 8, 0, 0,
                                "Ignores enemy DEF. When hit: flip coin. Heads = heal +3, tails = gain +3 ATK. Max 4 triggers.",
                                """
                                                [
                                                  {"trigger":"ON_ATTACK_DECLARE","action":"MODIFY_STAT","params":{"targets":"EVENT_TARGET","stat":"DEF","amount":-999,"isPermanent":false}},
                                                  {"trigger":"ON_DAMAGE_TAKEN","condition":{"type":"VALUE_COMPARISON","params":{"sourceValue":{"source":"FLAG_VALUE","flagName":"apolo_triggers","cardContext":"SELF"},"operator":"LESS_THAN","targetValue":4}},"action":"CHAINED_EFFECTS","params":{"effects":[{"action":"MODIFY_FLAG","params":{"targets":"SELF","flagName":"apolo_triggers","operation":"INCREMENT"}},{"action":"CHOOSE_RANDOM_EFFECT","params":{"choices":[{"action":"HEAL","params":{"targets":"SELF","amount":3}},{"action":"MODIFY_STAT","params":{"targets":"SELF","stat":"ATK","amount":3,"isPermanent":true}}]}}]}}
                                                ]
                                                """,
                                Rarity.UNCOMMON,
                                "apolo.png",
                                "Ghostly strikes."));
                cardsToSeed.add(new Card("CAP023", "Realistjames", "Undead,Femboy", 4, 1, 0,
                                "Each attack: +2 DEF. Can survive negative LIFE equal to DEF. Dies if DEF = 0 and LIFE < 0. Instantly dies if hit by American.",
                                """
                                                [
                                                  {"trigger":"ON_ATTACK_DECLARE","action":"MODIFY_STAT","params":{"targets":"SELF","stat":"DEF","amount":2,"isPermanent":true}},
                                                  {"trigger":"ON_DAMAGE_TAKEN","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"American"}},"action":"DESTROY_CARD","params":{"targets":"SELF"}}
                                                ]
                                                """,
                                Rarity.UNCOMMON,
                                "realistjames.png", "Defiantly undead."));
                cardsToSeed.add(new Card("CAP024", "Kizer", "American,Femboy", 6, 6, 1,
                                "+1 ATK if another American in play. Ignores -1 damage from Capybaras. Can heal +1 to Kahina, Swettie, Ariel, Makachuva or Femboys instead of attacking.",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_AURA","action":"APPLY_AURA_BUFF","params":{"synergyType":"American","flagName":"kizer_american_buff","buffs":[{"stat":"ATK","amount":1}]}},
                                                  {"trigger":"CONTINUOUS_DEFENSIVE","action":"MODIFY_INCOMING_DAMAGE","condition":{"type":"SOURCE_HAS_TYPE","params":{"typeName":"Capybara"}},"params":{"amount":1,"mode":"REDUCE_BY"}},
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":0,"condition":{"type":"LOGIC_OPERATOR","params":{"operator":"OR","conditions":[{"type":"TARGET_HAS_CARD_ID","params":{"cardId":"CAP001"}},{"type":"TARGET_HAS_CARD_ID","params":{"cardId":"CAP005"}},{"type":"TARGET_HAS_CARD_ID","params":{"cardId":"CAP025"}},{"type":"TARGET_HAS_CARD_ID","params":{"cardId":"CAP020"}},{"type":"TARGET_HAS_TYPE","params":{"typeName":"Femboy"}}]}},"action":"HEAL","params":{"targets":"ACTIVATION_CONTEXT_TARGET","amount":1}}
                                                ]
                                                """,
                                Rarity.RARE,
                                "kizer.png",
                                "Stylish and supportive."));
                cardsToSeed.add(new Card("CAP025", "Ariel", "Indigenous,Femboy", 9, 2, 4,
                                "“Dual Affinity”: Gains +4 DEF for each other Indigenous card on the field. If killed by a card, that attacker is silenced for 1 turn. Can heal another card +1 instead of attacking.",
                                """
                                                [
                                                  {"trigger":"ON_DEATH", "action":"APPLY_STATUS", "params": {"targets":"EVENT_SOURCE", "statusName":"silenced"}}
                                                ]
                                                """,
                                Rarity.RARE, "ariel.png",
                                "Spirit of the forest."));
                cardsToSeed.add(new Card("CAP026", "Famousbuilder", "European", 8, 2, 2,
                                "“Pretentious Interference”: When Famousbuilder enters the field, choose one: (a) Reduce 1 enemy card’s DEF to 0 until end of opponent’s next turn, or (b) prevent one enemy from using its effect this turn. Cannot attack if there is another European card in your field.",
                                "[{\"action\":\"CUSTOM_LOGIC\",\"params\":{\"logicKey\":\"FAMOUSBUILDER\"}}]",
                                Rarity.UNCOMMON,
                                "famous.png", "Master of obstruction."));
                cardsToSeed.add(new Card("CAP027", "GGaego", "Undead,European", 1, 9, 0,
                                "Once per turn: reduce -2 DEF of any card (min 0), OR give +2 ATK to friendly and +1 ATK to enemy. Can heal self +3 instead of attacking.",
                                """
                                                [
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":0,"condition":{"type":"SELF_HAS_FLAG","params":{"flagName":"ggaego_activated_usedThisTurn","mustBeAbsent":true}},"action":"MODIFY_STAT","params":{"targets":"ACTIVATION_CONTEXT_TARGET","stat":"DEF","amount":-2,"isPermanent":true},"flags":{"setInstanceFlags":[{"name":"ggaego_activated_used","value":true,"duration":"TURN"}]}},
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":1,"condition":{"type":"SELF_HAS_FLAG","params":{"flagName":"ggaego_activated_usedThisTurn","mustBeAbsent":true}},"action":"CUSTOM_LOGIC","params":{"logicKey":"GGAEGO_ATK_BUFF"},"flags":{"setInstanceFlags":[{"name":"ggaego_activated_used","value":true,"duration":"TURN"}]}},
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":2,"condition":{"type":"SELF_HAS_FLAG","params":{"flagName":"ggaego_activated_usedThisTurn","mustBeAbsent":true}},"action":"CUSTOM_LOGIC","params":{"logicKey":"GGAEGO_HEAL"},"flags":{"setInstanceFlags":[{"name":"ggaego_activated_used","value":true,"duration":"TURN"}]}}
                                                ]
                                                """,
                                Rarity.RARE, "ggaego.png", "A chaotic force."));
                cardsToSeed.add(new Card("CAP028", "Nox", "Capybara,European", 1, 8, 0,
                                "All cards in same field gain +1 ATK while alive. Can mark others with 'scargot' (must have it to attack). Can use 'wine' to self-heal +3 next turn.",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_AURA","action":"CUSTOM_LOGIC","params":{"logicKey":"NOX_AURA"}},
                                                  {"trigger":"ACTIVATED","abilityOptionIndex":0,"condition":{"type":"SELF_HAS_FLAG","params":{"flagName":"nox_wine_usedThisTurn","mustBeAbsent":true}},"action":"flags","params":{},"flags":{"setInstanceFlags":[{"name":"nox_wine_heal_pending","value":true},{"name":"nox_wine_used","value":true,"duration":"TURN"}]}},
                                                  {"trigger":"START_OF_TURN_SELF","condition":{"type":"SELF_HAS_FLAG","params":{"flagName":"nox_wine_heal_pending"}},"action":"CHAINED_EFFECTS","params":{"effects":[{"action":"HEAL","params":{"targets":"SELF","amount":3}},{"action":"REMOVE_FLAG","params":{"targets":"SELF","flagName":"nox_wine_heal_pending"}}]}}
                                                ]
                                                """,
                                Rarity.RARE,
                                "nox.png",
                                "The sophisticated strategist."));
                cardsToSeed.add(new Card("CAP029", "Hungrey", "American", 6, 10, 0,
                                "When attacked, gains +2 ATK permanently. Then, deals damage to its attacker equal to its new ATK.",
                                """
                                                [
                                                  {"trigger":"ON_DAMAGE_TAKEN","flags":{"oncePerInstanceId":"hungrey_effect_used"},"action":"CHAINED_EFFECTS","params":{"effects":[{"action":"MODIFY_STAT","params":{"targets":"SELF","stat":"ATK","amount":2,"isPermanent":true}},{"action":"DEAL_DAMAGE","params":{"targets":"EVENT_SOURCE","amount":{"source":"STAT","statName":"ATK","cardContext":"SELF"}}}]}}
                                                ]
                                                """,
                                Rarity.UNCOMMON, "hungrey.png", "Always ready for a fight."));
                cardsToSeed.add(new Card("CAP030", "Andgames", "Capybara", 7, 8, 0,
                                "If Kizer is in same field: +3 LIFE and +2 ATK (once).",
                                """
                                                [
                                                  {"trigger":"CONTINUOUS_AURA","condition":{"type":"FRIENDLY_CARD_IN_PLAY","params":{"cardId":"CAP024"}},"flags":{"oncePerInstanceId":"andgames_kizer_buff_applied"},"action":"CHAINED_EFFECTS","params":{"effects":[{"action":"MODIFY_STAT","params":{"targets":"SELF","stat":"ATK","amount":2,"isPermanent":true}},{"action":"MODIFY_STAT","params":{"targets":"SELF","stat":"MAX_LIFE","amount":3,"isPermanent":true}},{"action":"HEAL","params":{"targets":"SELF","amount":3}}]}}
                                                ]
                                                """,
                                Rarity.UNCOMMON,
                                "andgames.png", "Stronger together."));
                cardsToSeed.add(new Card("CAP031", "98pm", "Undead", 1, 11, 0,
                                "Only dies one turn after reaching 0 LIFE.",
                                "[]", Rarity.RARE, "98pm.png", "Not quite dead yet."));

                cardRepository.saveAll(cardsToSeed);
                logger.info("Seeded {} placeholder cards.", cardsToSeed.size());
        }

        // ... The rest of GameService.java remains the same ...
        private List<Card> generatePlayerDeck() {
                if (allCardDefinitions == null || allCardDefinitions.size() < 10) {
                        throw new IllegalStateException(
                                        "Card definitions not loaded or insufficient for deck generation.");
                }

                List<Card> deckInProgress = new ArrayList<>();
                Set<String> pickedCardIdsInDeck = new HashSet<>();

                List<Card> tier1Candidates = this.allCardDefinitions.stream()
                                .filter(c -> c.getRarity() == Rarity.LEGENDARY || c.getRarity() == Rarity.SUPER_RARE)
                                .collect(Collectors.toList());
                Collections.shuffle(tier1Candidates, random);

                if (!tier1Candidates.isEmpty()) {
                        Card chosenTier1 = tier1Candidates.get(0);
                        deckInProgress.add(chosenTier1);
                        pickedCardIdsInDeck.add(chosenTier1.getCardId());
                }

                List<Card> rareCandidates = this.allCardDefinitions.stream()
                                .filter(c -> c.getRarity() == Rarity.RARE)
                                .collect(Collectors.toList());
                Collections.shuffle(rareCandidates, random);

                int raresAdded = 0;
                for (Card rareCard : rareCandidates) {
                        if (deckInProgress.size() >= MAX_DECK_SIZE || raresAdded >= MAX_RARE_CARDS)
                                break;
                        if (!pickedCardIdsInDeck.contains(rareCard.getCardId())) {
                                deckInProgress.add(rareCard);
                                pickedCardIdsInDeck.add(rareCard.getCardId());
                                raresAdded++;
                        }
                }

                List<Card> availableCommons = this.allCardDefinitions.stream()
                                .filter(c -> c.getRarity() == Rarity.COMMON
                                                && !pickedCardIdsInDeck.contains(c.getCardId()))
                                .collect(Collectors.toList());
                List<Card> availableUncommons = this.allCardDefinitions.stream()
                                .filter(c -> c.getRarity() == Rarity.UNCOMMON
                                                && !pickedCardIdsInDeck.contains(c.getCardId()))
                                .collect(Collectors.toList());

                Collections.shuffle(availableCommons, random);
                Collections.shuffle(availableUncommons, random);

                while (deckInProgress.size() < MAX_DECK_SIZE) {
                        if (availableCommons.isEmpty() && availableUncommons.isEmpty())
                                break;

                        Card cardToAdd = null;
                        if (!availableCommons.isEmpty() && !availableUncommons.isEmpty()) {
                                int roll = random.nextInt(COMMON_SELECTION_WEIGHT + UNCOMMON_SELECTION_WEIGHT);
                                cardToAdd = roll < COMMON_SELECTION_WEIGHT ? availableCommons.remove(0)
                                                : availableUncommons.remove(0);
                        } else if (!availableCommons.isEmpty()) {
                                cardToAdd = availableCommons.remove(0);
                        } else {
                                cardToAdd = availableUncommons.remove(0);
                        }
                        if (cardToAdd != null)
                                deckInProgress.add(cardToAdd);
                }

                if (deckInProgress.size() < MAX_DECK_SIZE) {
                        logger.warn("Deck for a player has {} cards. Filling with duplicates from C/UC pool.",
                                        deckInProgress.size());
                        List<Card> emergencyPool = new ArrayList<>();
                        this.allCardDefinitions.stream()
                                        .filter(c -> c.getRarity() == Rarity.COMMON || c.getRarity() == Rarity.UNCOMMON)
                                        .forEach(emergencyPool::add);
                        Collections.shuffle(emergencyPool, random);
                        int emergencyIndex = 0;
                        while (deckInProgress.size() < MAX_DECK_SIZE && !emergencyPool.isEmpty()) {
                                deckInProgress.add(emergencyPool.get(emergencyIndex % emergencyPool.size()));
                                emergencyIndex++;
                        }
                }

                if (deckInProgress.size() != MAX_DECK_SIZE) {
                        throw new IllegalStateException(
                                        "Failed to generate a full deck of " + MAX_DECK_SIZE + " cards.");
                }

                Collections.shuffle(deckInProgress, random);
                return deckInProgress;
        }

        public Game createNewGame(String player1DisplayName, String player2DisplayName) {
                if (allCardDefinitions == null || allCardDefinitions.isEmpty()) {
                        throw new IllegalStateException("Card definitions not loaded. Cannot create game.");
                }

                List<Card> p1DeckCards = generatePlayerDeck();
                List<Card> p2DeckCards = generatePlayerDeck();

                Player player1 = new Player(player1DisplayName, p1DeckCards);
                Player player2 = new Player(player2DisplayName, p2DeckCards);

                Game newGame = new Game(player1, player2);
                activeGames.put(newGame.getGameId(), newGame);
                newGame.startGame();
                logger.info("New game created with ID: {}. P1: {}, P2: {}", newGame.getGameId(), player1DisplayName,
                                player2DisplayName);

                return newGame;
        }

        public Game getGame(String gameId) {
                return activeGames.get(gameId);
        }

        public ActionResultDTO handleGameAction(GameActionRequest actionRequest) {
                String gameId = actionRequest.getGameId();
                String playerId = actionRequest.getPlayerId();
                Game game = getGame(gameId);

                if (game == null) {
                        return ActionResultDTO.failure("Game not found.", gameId);
                }

                Player actingPlayer = game.getPlayer1().getPlayerId().equals(playerId) ? game.getPlayer1()
                                : game.getPlayer2();
                if (!actingPlayer.getPlayerId().equals(playerId)) {
                        return ActionResultDTO.failure("Player ID mismatch error.", gameId, game);
                }

                String actionOutcomeMessage = null;
                String successMessage = "Action processed.";

                switch (actionRequest.getActionType()) {
                        case PLAY_CARD:
                                actionOutcomeMessage = game.playerPlaysCard(actingPlayer,
                                                actionRequest.getHandCardIndex(),
                                                actionRequest.getTargetFieldSlot());
                                if (actionOutcomeMessage == null)
                                        successMessage = actingPlayer.getDisplayName() + " played a card.";
                                break;
                        case ATTACK:
                                Player opponent = game.getOpponent(actingPlayer);
                                actionOutcomeMessage = game.playerAttacks(actingPlayer,
                                                actionRequest.getAttackerFieldIndex(), opponent,
                                                actionRequest.getDefenderFieldIndex());
                                if (actionOutcomeMessage == null)
                                        successMessage = actingPlayer.getDisplayName() + " attacked.";
                                break;
                        case ACTIVATE_ABILITY:
                                actionOutcomeMessage = game.playerActivatesAbility(actingPlayer,
                                                actionRequest.getSourceCardInstanceId(),
                                                actionRequest.getTargetCardInstanceId(),
                                                actionRequest.getAbilityOptionIndex());
                                if (actionOutcomeMessage != null
                                                && actionOutcomeMessage.toLowerCase()
                                                                .contains("attempted to activate")) {
                                        successMessage = actionOutcomeMessage;
                                        actionOutcomeMessage = null;
                                }
                                break;
                        case END_TURN:
                                if (game.getCurrentPlayer() != actingPlayer) {
                                        actionOutcomeMessage = "Not your turn.";
                                } else {
                                        game.endTurn();
                                        successMessage = actingPlayer.getDisplayName() + " ended their turn.";
                                }
                                break;
                        default:
                                actionOutcomeMessage = "Unknown action type: " + actionRequest.getActionType();
                }

                if (actionOutcomeMessage != null) {
                        return ActionResultDTO.failure(actionOutcomeMessage, gameId, game);
                }

                if (game.getGameState().name().contains("GAME_OVER")) {
                        if (game.getGameState() == Game.GameState.GAME_OVER_PLAYER_1_WINS)
                                successMessage = game.getPlayer1().getDisplayName() + " wins!";
                        else if (game.getGameState() == Game.GameState.GAME_OVER_PLAYER_2_WINS)
                                successMessage = game.getPlayer2().getDisplayName() + " wins!";
                        else
                                successMessage = "The game is a draw!";
                }
                return ActionResultDTO.success(successMessage, game);
        }

        public void removeGame(String gameId) {
                activeGames.remove(gameId);
                logger.info("Game {} removed.", gameId);
        }
}