package gameserver.entity;

public enum TitanType {
    GUARDIAN, WARRIOR, RANGER, SLASHER, MARKSMAN, STEALTH, /*RECON,*/
    SUPPORT, ARTISAN, POST, MAGE, BUILDER,
    ANY, NOT_GUARDIAN, ANY_ENTITY
}

//Controls will be point and click (left pass, right shoot/longpass), WASD + QER/ERT/123
//longpass is only 80% recovery chance

//sprite needs: marksman, recon, support, artisan, shieldmaiden

//abilities brainstorming:

//Power means throw power unless otherwise mentioned

//Guardian is still gonna be NPC. Nothing special needed there. Just use the eye monster sprite
//One player will have special privileges (2-3 separate keys) to override it down and up between the goals

//Threats
//Warrior has the sword and a gap closer? (med+ speed, hi hp, lowest power)
//Ranger has the bow and can kick people away (med speed, med hp, lowest power)
//Shieldmaiden jump and shield ally, spear poke (med- speed, med+ hp, lowest power)
//Burstmage has enough to assassinate a smaller target at 2/3 health by landing his combo

//Scoring
//TODO burn
//Slasher has the mega speed boost (recolor) and a burn effect he can apply to someone that counters stealth (low speed, low hp, med+ power)
//Marksman has a shooting steroid and a targeted slow (med speed, low hp, highest power, laser accurate throwing)
//Stealth has the stealth mechanic and a mild slow CC (med speed, low hp, med+ power)
//Post has a scatter and a invuln (lowest speed, highest hp, med power)

//Support
//Recon has a ward placeable and a trap that slows, (fastest speed, lowest hp, med- power)
//Support has a long range heal and a shortrange stun (med+ speed, low+ hp, low+ power)
//Artisan can curve-throw the ball when has the ball on offense (each button is a curve to one way).
//          On defense, can gravity-suck in a loose ball (short range), or (higher cd) ball portals (high speed, med hp, med+ power)
//Builder: can spawn a trap for damage and a wall for complete blocking

//Ultimates that can be unlocked by commanders:
//Slasher: burn actually deals significant damage (blue fire)
//Support: AOE speed steal
//Artisan: gravity push everyone janna style
//Post: Dash after using the bounce
//Stealth: backstab for execute damage
//Marksman: either snipe someone or maybe just a warp
//Warrior: AOE sword swipe
//Ranger: giant flaming piercing arrow
//Shieldbearer: protective bubble
//Recon: Global vision 10s