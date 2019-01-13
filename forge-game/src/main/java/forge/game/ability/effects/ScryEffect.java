package forge.game.ability.effects;

import forge.game.ability.AbilityUtils;
import forge.game.ability.SpellAbilityEffect;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import java.util.List;
import java.util.ArrayList;

public class ScryEffect extends SpellAbilityEffect {
    @Override
    protected String getStackDescription(SpellAbility sa) {
        final StringBuilder sb = new StringBuilder();

        final List<Player> tgtPlayers = getTargetPlayers(sa);

        for (final Player p : tgtPlayers) {
            sb.append(p.toString()).append(" ");
        }

        int num = 1;
        if (sa.hasParam("ScryNum")) {
            num = AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("ScryNum"), sa);
        }

        sb.append("scrys (").append(num).append(").");
        return sb.toString();
    }

    @Override
    public void resolve(SpellAbility sa) {
        int num = 1;
        if (sa.hasParam("ScryNum")) {
            num = AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("ScryNum"), sa);
        }

        boolean isOptional = sa.hasParam("Optional");

        final TargetRestrictions tgt = sa.getTargetRestrictions();
        final List<Player> tgtPlayers = getTargetPlayers(sa);
	final ArrayList<Player> players = new ArrayList<Player>(); // players really affected

	// Optional here for spells that have optional multi-player scrying
        for (final Player p : tgtPlayers) {
	    if ( ((tgt == null) || p.canBeTargetedBy(sa)) &&
		 (!isOptional || p.getController().confirmAction(sa, null, "Do you want to scry?")) ) {
		players.add(p);
	    }
	}
	sa.getActivatingPlayer().getGame().getAction().scry(players,num,false,sa);
    }

}
