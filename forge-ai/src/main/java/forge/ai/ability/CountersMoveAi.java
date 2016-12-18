package forge.ai.ability;

import java.util.List;

import com.google.common.base.Predicate;

import forge.ai.ComputerUtil;
import forge.ai.ComputerUtilCard;
import forge.ai.SpellAbilityAi;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.card.CardUtil;
import forge.game.card.CounterType;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.util.collect.FCollection;

public class CountersMoveAi extends SpellAbilityAi {
    @Override
    protected boolean checkApiLogic(final Player ai, final SpellAbility sa) {

        if (sa.usesTargeting()) {
            sa.resetTargets();
            if (!moveTgtAI(ai, sa)) {
                return false;
            }
        }

        if (!SpellAbilityAi.playReusable(ai, sa)) {
            return false;
        }

        return MyRandom.getRandom().nextFloat() < .8f; // random success
    }

    @Override
    protected boolean checkPhaseRestrictions(final Player ai, final SpellAbility sa, final PhaseHandler ph) {
        final Card host = sa.getHostCard();
        final String type = sa.getParam("CounterType");
        final CounterType cType = "Any".equals(type) ? null : CounterType.valueOf(type);

        // Don't tap creatures that may be able to block
        if (ComputerUtil.waitForBlocking(sa)) {
            return false;
        }

        if (CounterType.P1P1.equals(cType) && sa.hasParam("Source")) {
            int amount = calcAmount(sa, cType);
            final List<Card> srcCards = AbilityUtils.getDefinedCards(host, sa.getParam("Source"), sa);
            if (ph.getPlayerTurn().isOpponentOf(ai)) {
                // opponent Creature with +1/+1 counter does attack
                // try to steal counter from it to kill it
                if (ph.inCombat() && ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_BLOCKERS)) {
                    for (final Card c : srcCards) {
                        // source is not controlled by current player
                        if (!ph.isPlayerTurn(c.getController())) {
                            continue;
                        }

                        int a = c.getCounters(cType);
                        if (a < amount) {
                            continue;
                        }
                        if (ph.getCombat().isAttacking(c)) {
                            // get copy of creature with removed Counter
                            final Card cpy = CardUtil.getLKICopy(c);
                            // cant use substract on Copy
                            cpy.setCounters(cType, a - amount);

                            // a removed counter would kill it
                            if (cpy.getNetToughness() <= cpy.getDamage()) {
                                return true;
                            }

                            // something you can't block, try to reduce its
                            // attack
                            if (!ComputerUtilCard.canBeBlockedProfitably(ai, cpy)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }

            }

            // for Simic Fluxmage and other
            if (!ph.getNextTurn().equals(ai) || ph.getPhase().isBefore(PhaseType.END_OF_TURN)) {
                return false;
            }

        } else if (CounterType.P1P1.equals(cType) && sa.hasParam("Defined")) {
            // something like Cyptoplast Root-kin
            if (ph.getPlayerTurn().isOpponentOf(ai)) {
                if (ph.inCombat() && ph.getPhase().isBefore(PhaseType.COMBAT_DECLARE_BLOCKERS)) {

                }
            }
            // for Simic Fluxmage and other
            if (!ph.getNextTurn().equals(ai) || ph.getPhase().isBefore(PhaseType.END_OF_TURN)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean doTriggerAINoCost(final Player ai, SpellAbility sa, boolean mandatory) {

        if (sa.usesTargeting()) {

            if (!moveTgtAI(ai, sa) && !mandatory) {
                return false;
            }

            if (!sa.isTargetNumberValid() && mandatory) {
                final Game game = ai.getGame();
                List<Card> tgtCards = CardLists.getTargetableCards(game.getCardsIn(ZoneType.Battlefield), sa);

                if (tgtCards.isEmpty()) {
                    return false;
                }

                final Card card = ComputerUtilCard.getWorstAI(tgtCards);
                sa.getTargets().add(card);
            }
            return true;
        } else {
            // no target Probably something like Graft

            if (mandatory) {
                return true;
            }

            final Card host = sa.getHostCard();

            final String type = sa.getParam("CounterType");
            final CounterType cType = "Any".equals(type) ? null : CounterType.valueOf(type);

            final List<Card> srcCards = AbilityUtils.getDefinedCards(host, sa.getParam("Source"), sa);
            final List<Card> destCards = AbilityUtils.getDefinedCards(host, sa.getParam("Defined"), sa);

            if (srcCards.isEmpty() || destCards.isEmpty()) {
                return false;
            }

            final Card src = srcCards.get(0);
            final Card dest = destCards.get(0);

            // for such Trigger, do not move counter to another players creature
            if (!dest.getController().equals(ai)) {
                return false;
            } else if (isUselessCreature(ai, dest)) {
                return false;
            } else if (dest.hasSVar("EndOfTurnLeavePlay")) {
                return false;
            }

            if (cType != null) {
                if (!dest.canReceiveCounters(cType)) {
                    return false;
                }
                final int amount = calcAmount(sa, cType);
                int a = src.getCounters(cType);
                if (a < amount) {
                    return false;
                }

                final Card srcCopy = CardUtil.getLKICopy(src);
                // cant use substract on Copy
                srcCopy.setCounters(cType, a - amount);

                final Card destCopy = CardUtil.getLKICopy(dest);
                destCopy.setCounters(cType, dest.getCounters(cType) + amount);

                int oldEval = ComputerUtilCard.evaluateCreature(src) + ComputerUtilCard.evaluateCreature(dest);
                int newEval = ComputerUtilCard.evaluateCreature(srcCopy) + ComputerUtilCard.evaluateCreature(destCopy);

                if (newEval < oldEval) {
                    return false;
                }
            }
            // no target
            return true;
        }
    }

    @Override
    public boolean chkAIDrawback(SpellAbility sa, Player ai) {
        if (sa.usesTargeting()) {
            sa.resetTargets();
            if (!moveTgtAI(ai, sa)) {
                return false;
            }
        }

        return true;
    }

    private static int calcAmount(final SpellAbility sa, final CounterType cType) {
        final Card host = sa.getHostCard();

        final String amountStr = sa.getParam("CounterNum");

        // TODO handle proper calculation of X values based on Cost
        int amount = 0;

        if (amountStr.equals("All")) {
            // sa has Source, otherwise Source is the Target
            if (sa.hasParam("Source")) {
                final List<Card> srcCards = AbilityUtils.getDefinedCards(host, sa.getParam("Source"), sa);
                for (final Card c : srcCards) {
                    amount += c.getCounters(cType);
                }
            }
        } else {
            amount = AbilityUtils.calculateAmount(host, amountStr, sa);
        }
        return amount;
    }

    private boolean moveTgtAI(final Player ai, final SpellAbility sa) {

        final Card host = sa.getHostCard();
        final Game game = ai.getGame();
        final String type = sa.getParam("CounterType");
        final CounterType cType = "Any".equals(type) ? null : CounterType.valueOf(type);

        List<Card> tgtCards = CardLists.getTargetableCards(game.getCardsIn(ZoneType.Battlefield), sa);

        if (sa.hasParam("Defined")) {
            final int amount = calcAmount(sa, cType);
            tgtCards = CardLists.filter(tgtCards, CardPredicates.hasCounter(cType));

            // SA uses target for Source
            // Target => Defined
            final List<Card> destCards = AbilityUtils.getDefinedCards(host, sa.getParam("Defined"), sa);

            if (destCards.isEmpty()) {
                // something went wrong
                return false;
            }

            final Card dest = destCards.get(0);

            // remove dest from targets, because move doesn't work that way
            tgtCards.remove(dest);

            if (cType != null && !dest.canReceiveCounters(cType)) {
                return false;
            }

            // prefered logic for this: try to steal counter
            List<Card> oppList = CardLists.filterControlledBy(tgtCards, ai.getOpponents());
            if (!oppList.isEmpty()) {
                List<Card> best = CardLists.filter(oppList, new Predicate<Card>() {

                    @Override
                    public boolean apply(Card card) {
                        // do not weak a useless creature if able
                        if (isUselessCreature(ai, card)) {
                            return false;
                        }

                        final Card srcCardCpy = CardUtil.getLKICopy(card);
                        // cant use substract on Copy
                        srcCardCpy.setCounters(cType, srcCardCpy.getCounters(cType) - amount);

                        // do not steal a P1P1 from Undying if it would die
                        // this way
                        if (CounterType.P1P1.equals(cType) && srcCardCpy.getNetToughness() <= 0) {
                            if (srcCardCpy.getCounters(cType) > 0 || !card.hasKeyword("Undying") || card.isToken()) {
                                return true;
                            }
                            return false;
                        }
                        return true;
                    }

                });

                // if no Prefered found, try normal list
                if (best.isEmpty()) {
                    best = oppList;
                }

                Card card = ComputerUtilCard.getBestCreatureAI(best);

                if (card != null) {
                    sa.getTargets().add(card);
                    return true;
                }

            }

            // from your creature, try to take from the weakest
            FCollection<Player> ally = ai.getAllies();
            ally.add(ai);

            List<Card> aiList = CardLists.filterControlledBy(tgtCards, ally);
            if (!aiList.isEmpty()) {
                List<Card> best = CardLists.filter(aiList, new Predicate<Card>() {

                    @Override
                    public boolean apply(Card card) {
                        // gain from useless
                        if (isUselessCreature(ai, card)) {
                            return true;
                        }

                        // source would leave the game
                        if (card.hasSVar("EndOfTurnLeavePlay")) {
                            return true;
                        }

                        // try to remove P1P1 from undying or evolve
                        if (CounterType.P1P1.equals(cType)) {
                            if (card.hasKeyword("Undying") || card.hasKeyword("Evolve")) {
                                return true;
                            }
                        }
                        if (CounterType.M1M1.equals(cType) && card.hasKeyword("Persist")) {
                            return true;
                        }

                        return false;
                    }
                });

                if (best.isEmpty()) {
                    best = aiList;
                }

                Card card = ComputerUtilCard.getWorstCreatureAI(best);

                if (card != null) {
                    sa.getTargets().add(card);
                    return true;
                }
            }

            return false;
        } else {
            // SA uses target for Defined
            // Source => Targeted
            final List<Card> srcCards = AbilityUtils.getDefinedCards(host, sa.getParam("Source"), sa);

            if (srcCards.isEmpty()) {
                // something went wrong
                return false;
            }

            final Card src = srcCards.get(0);
            if (cType != null) {
                if (src.getCounters(cType) <= 0) {
                    return false;
                }
            }

            List<Card> aiList = CardLists.filterControlledBy(tgtCards, ai);
            if (!aiList.isEmpty()) {
                List<Card> best = CardLists.filter(aiList, new Predicate<Card>() {

                    @Override
                    public boolean apply(Card card) {
                        // gain from useless
                        if (isUselessCreature(ai, card)) {
                            return false;
                        }

                        // source would leave the game
                        if (card.hasSVar("EndOfTurnLeavePlay")) {
                            return false;
                        }

                        if (cType != null) {
                            if (CounterType.P1P1.equals(cType) && card.hasKeyword("Undying")) {
                                return false;
                            }
                            if (CounterType.M1M1.equals(cType) && card.hasKeyword("Persist")) {
                                return false;
                            }

                            if (!card.canReceiveCounters(cType)) {
                                return false;
                            }
                        }
                        return false;
                    }
                });

                if (best.isEmpty()) {
                    best = aiList;
                }

                Card card = ComputerUtilCard.getBestCreatureAI(best);

                if (card != null) {
                    sa.getTargets().add(card);
                    return true;
                }
            }

            // move counter to opponents creature but only if you can not steal
            // them
            // try to move to something useless or something that would leave
            // play
            List<Card> oppList = CardLists.filterControlledBy(tgtCards, ai.getOpponents());
            if (!oppList.isEmpty()) {
                List<Card> best = CardLists.filter(oppList, new Predicate<Card>() {

                    @Override
                    public boolean apply(Card card) {
                        // gain from useless
                        if (!isUselessCreature(ai, card)) {
                            return true;
                        }

                        // source would leave the game
                        if (!card.hasSVar("EndOfTurnLeavePlay")) {
                            return true;
                        }

                        return false;
                    }
                });

                if (best.isEmpty()) {
                    best = aiList;
                }

                Card card = ComputerUtilCard.getBestCreatureAI(best);

                if (card != null) {
                    sa.getTargets().add(card);
                    return true;
                }
            }
            return false;
        }
    }
}
