package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Names the game changed from one version to another.
 *
 * A suggestion is only produced when the target is already in the list the running game
 * accepts at that point of the command, so a wrong row here never invents a command.
 */
public final class CommandAliases {
    // /gamemode and /difficulty stopped taking numbers in 1.13. Only the numbers are listed:
    // the old letters are a prefix of the word they mean, so the game already offers those.
    // Each table only applies once the option list says which command it is, or one
    // command's 1 would become the other's.
    private static final String[] GAMEMODE_MARKS = {"survival", "creative", "adventure", "spectator"};
    private static final String[][] GAMEMODE = {
            {"0", "survival"}, {"1", "creative"}, {"2", "adventure"}, {"3", "spectator"},
    };

    private static final String[] DIFFICULTY_MARKS = {"peaceful", "easy", "normal", "hard"};
    private static final String[][] DIFFICULTY = {
            {"0", "peaceful"}, {"1", "easy"}, {"2", "normal"}, {"3", "hard"},
    };

    // Type the left one, get the right one when this version has it. The command search
    // only exists from 1.16.5 up, so the old spelling is only ever typed, never offered:
    // a row pointing back at a pre-1.16.5 name could never fire and is not written.
    private static final String[][] PAIRS = {
            // commands
            {"toggledownfall", "weather"}, {"achievement", "advancement"}, {"blockdata", "data"},
            {"entitydata", "data"}, {"testfor", "execute"}, {"testforblock", "execute"},
            {"testforblocks", "execute"}, {"replaceitem", "item"}, {"item", "replaceitem"},
            {"xp", "experience"}, {"experience", "xp"}, {"tp", "teleport"}, {"teleport", "tp"},

            // entities
            {"zombie_pigman", "zombified_piglin"}, {"evocation_illager", "evoker"},
            {"vindication_illager", "vindicator"}, {"illusion_illager", "illusioner"},
            {"snowman", "snow_golem"}, {"villager_golem", "iron_golem"}, {"ender_crystal", "end_crystal"},
            {"xp_orb", "experience_orb"}, {"xp_bottle", "experience_bottle"},
            {"eye_of_ender_signal", "eye_of_ender"}, {"fireworks_rocket", "firework_rocket"},
            {"commandblock_minecart", "command_block_minecart"}, {"lava_slime", "magma_cube"},
            {"mushroom_cow", "mooshroom"}, {"ozelot", "ocelot"}, {"wither_boss", "wither"},
            {"primed_tnt", "tnt"},

            // blocks and items
            {"grass", "short_grass"}, {"short_grass", "grass"}, {"web", "cobweb"}, {"mob_spawner", "spawner"},
            {"noteblock", "note_block"}, {"melon_block", "melon"}, {"speckled_melon", "glistering_melon_slice"},
            {"reeds", "sugar_cane"}, {"waterlily", "lily_pad"}, {"snow_layer", "snow"},
            {"lit_pumpkin", "jack_o_lantern"}, {"fence", "oak_fence"}, {"wooden_door", "oak_door"},
            {"wooden_button", "oak_button"}, {"wooden_pressure_plate", "oak_pressure_plate"},
            {"trapdoor", "oak_trapdoor"}, {"sign", "oak_sign"}, {"boat", "oak_boat"}, {"oak_boat", "boat"},
            {"record_13", "music_disc_13"}, {"record_cat", "music_disc_cat"},
            {"record_blocks", "music_disc_blocks"}, {"record_chirp", "music_disc_chirp"},
            {"record_far", "music_disc_far"}, {"record_mall", "music_disc_mall"},
            {"record_mellohi", "music_disc_mellohi"}, {"record_stal", "music_disc_stal"},
            {"record_strad", "music_disc_strad"}, {"record_ward", "music_disc_ward"},
            {"record_11", "music_disc_11"}, {"record_wait", "music_disc_wait"},
    };

    // 1.21.11 renamed every gamerule at once; read off the real 1.21.9 and 1.21.11 sources.
    // disableRaids, disableElytraMovementCheck and disablePlayerMovementCheck also flipped
    // meaning: the new name is right, the value you want is the opposite.
    // doFireTick and allowFireTicksAwayFromPlayer merged into one radius, where -1 is off.
    private static final String[][] GAMERULES = {
            {"allowenteringnetherusingportals", "allow_entering_nether_using_portals"},
            {"allow_entering_nether_using_portals", "allowenteringnetherusingportals"},
            {"allowfireticksawayfromplayer", "fire_spread_radius_around_player"},
            {"fire_spread_radius_around_player", "allowfireticksawayfromplayer"},
            {"announceadvancements", "show_advancement_messages"},
            {"show_advancement_messages", "announceadvancements"},
            {"blockexplosiondropdecay", "block_explosion_drop_decay"},
            {"block_explosion_drop_decay", "blockexplosiondropdecay"},
            {"commandblockoutput", "command_block_output"}, {"command_block_output", "commandblockoutput"},
            {"commandblocksenabled", "command_blocks_work"}, {"command_blocks_work", "commandblocksenabled"},
            {"commandmodificationblocklimit", "max_block_modifications"},
            {"max_block_modifications", "commandmodificationblocklimit"},
            {"disableelytramovementcheck", "elytra_movement_check"},
            {"elytra_movement_check", "disableelytramovementcheck"},
            {"disableplayermovementcheck", "player_movement_check"},
            {"player_movement_check", "disableplayermovementcheck"}, {"disableraids", "raids"},
            {"raids", "disableraids"}, {"dodaylightcycle", "advance_time"}, {"advance_time", "dodaylightcycle"},
            {"doentitydrops", "entity_drops"}, {"entity_drops", "doentitydrops"},
            {"dofiretick", "fire_spread_radius_around_player"},
            {"fire_spread_radius_around_player", "dofiretick"}, {"doimmediaterespawn", "immediate_respawn"},
            {"immediate_respawn", "doimmediaterespawn"}, {"doinsomnia", "spawn_phantoms"},
            {"spawn_phantoms", "doinsomnia"}, {"dolimitedcrafting", "limited_crafting"},
            {"limited_crafting", "dolimitedcrafting"}, {"domobloot", "mob_drops"}, {"mob_drops", "domobloot"},
            {"domobspawning", "spawn_mobs"}, {"spawn_mobs", "domobspawning"},
            {"dopatrolspawning", "spawn_patrols"}, {"spawn_patrols", "dopatrolspawning"},
            {"dotiledrops", "block_drops"}, {"block_drops", "dotiledrops"},
            {"dotraderspawning", "spawn_wandering_traders"}, {"spawn_wandering_traders", "dotraderspawning"},
            {"dovinesspread", "spread_vines"}, {"spread_vines", "dovinesspread"},
            {"dowardenspawning", "spawn_wardens"}, {"spawn_wardens", "dowardenspawning"},
            {"doweathercycle", "advance_weather"}, {"advance_weather", "doweathercycle"},
            {"drowningdamage", "drowning_damage"}, {"drowning_damage", "drowningdamage"},
            {"enderpearlsvanishondeath", "ender_pearls_vanish_on_death"},
            {"ender_pearls_vanish_on_death", "enderpearlsvanishondeath"}, {"falldamage", "fall_damage"},
            {"fall_damage", "falldamage"}, {"firedamage", "fire_damage"}, {"fire_damage", "firedamage"},
            {"forgivedeadplayers", "forgive_dead_players"}, {"forgive_dead_players", "forgivedeadplayers"},
            {"freezedamage", "freeze_damage"}, {"freeze_damage", "freezedamage"},
            {"globalsoundevents", "global_sound_events"}, {"global_sound_events", "globalsoundevents"},
            {"keepinventory", "keep_inventory"}, {"keep_inventory", "keepinventory"},
            {"lavasourceconversion", "lava_source_conversion"},
            {"lava_source_conversion", "lavasourceconversion"}, {"locatorbar", "locator_bar"},
            {"locator_bar", "locatorbar"}, {"logadmincommands", "log_admin_commands"},
            {"log_admin_commands", "logadmincommands"},
            {"maxcommandchainlength", "max_command_sequence_length"},
            {"max_command_sequence_length", "maxcommandchainlength"},
            {"maxcommandforkcount", "max_command_forks"}, {"max_command_forks", "maxcommandforkcount"},
            {"maxentitycramming", "max_entity_cramming"}, {"max_entity_cramming", "maxentitycramming"},
            {"minecartmaxspeed", "max_minecart_speed"}, {"max_minecart_speed", "minecartmaxspeed"},
            {"mobexplosiondropdecay", "mob_explosion_drop_decay"},
            {"mob_explosion_drop_decay", "mobexplosiondropdecay"}, {"mobgriefing", "mob_griefing"},
            {"mob_griefing", "mobgriefing"}, {"naturalregeneration", "natural_health_regeneration"},
            {"natural_health_regeneration", "naturalregeneration"},
            {"playersnetherportalcreativedelay", "players_nether_portal_creative_delay"},
            {"players_nether_portal_creative_delay", "playersnetherportalcreativedelay"},
            {"playersnetherportaldefaultdelay", "players_nether_portal_default_delay"},
            {"players_nether_portal_default_delay", "playersnetherportaldefaultdelay"},
            {"playerssleepingpercentage", "players_sleeping_percentage"},
            {"players_sleeping_percentage", "playerssleepingpercentage"},
            {"projectilescanbreakblocks", "projectiles_can_break_blocks"},
            {"projectiles_can_break_blocks", "projectilescanbreakblocks"},
            {"randomtickspeed", "random_tick_speed"}, {"random_tick_speed", "randomtickspeed"},
            {"reduceddebuginfo", "reduced_debug_info"}, {"reduced_debug_info", "reduceddebuginfo"},
            {"sendcommandfeedback", "send_command_feedback"}, {"send_command_feedback", "sendcommandfeedback"},
            {"showdeathmessages", "show_death_messages"}, {"show_death_messages", "showdeathmessages"},
            {"snowaccumulationheight", "max_snow_accumulation_height"},
            {"max_snow_accumulation_height", "snowaccumulationheight"},
            {"spawnerblocksenabled", "spawner_blocks_work"}, {"spawner_blocks_work", "spawnerblocksenabled"},
            {"spawnmonsters", "spawn_monsters"}, {"spawn_monsters", "spawnmonsters"},
            {"spawnradius", "respawn_radius"}, {"respawn_radius", "spawnradius"},
            {"spectatorsgeneratechunks", "spectators_generate_chunks"},
            {"spectators_generate_chunks", "spectatorsgeneratechunks"}, {"tntexplodes", "tnt_explodes"},
            {"tnt_explodes", "tntexplodes"}, {"tntexplosiondropdecay", "tnt_explosion_drop_decay"},
            {"tnt_explosion_drop_decay", "tntexplosiondropdecay"}, {"universalanger", "universal_anger"},
            {"universal_anger", "universalanger"}, {"watersourceconversion", "water_source_conversion"},
            {"water_source_conversion", "watersourceconversion"},
    };

    private CommandAliases() {
    }

    /**
     * Returns the options that mean the same as the typed word. Empty when the word is
     * already valid here, or when nothing equivalent exists in this version.
     */
    public static List<String> matches(String word, Collection<String> pool) {
        if (word == null || pool == null || pool.isEmpty()) {
            return Collections.emptyList();
        }
        String typed = path(word);
        if (typed.isEmpty()) {
            return Collections.emptyList();
        }
        // the tables are short and the option list is not, so nothing is walked or allocated
        // until a row matches - which is almost never, since almost every keystroke is a name
        boolean numbered = hasKey(GAMEMODE, typed) || hasKey(DIFFICULTY, typed);
        if (!numbered && !hasKey(PAIRS, typed) && !hasKey(GAMERULES, typed)) {
            return Collections.emptyList();
        }
        Set<String> targets = new LinkedHashSet<>(4);
        collect(PAIRS, typed, targets);
        collect(GAMERULES, typed, targets);
        if (numbered) {
            collectNumbered(pool, typed, targets);
        }
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }
        String[] wanted = targets.toArray(new String[0]);

        List<String> out = new ArrayList<>(2);
        for (String option : pool) {
            if (option == null) {
                continue;
            }
            // already valid: nothing to correct, and suggesting anything else would confuse
            if (isNamed(option, typed)) {
                return Collections.emptyList();
            }
            for (String target : wanted) {
                if (isNamed(option, target)) {
                    if (!out.contains(option)) {
                        out.add(option);
                    }
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Same as {@link #matches}, but the typed word only has to be the start of the old name.
     * Never used to rewrite what the player typed: half a word is not a decision.
     */
    public static List<String> starting(String word, Collection<String> pool) {
        if (word == null || pool == null || pool.isEmpty()) {
            return Collections.emptyList();
        }
        String typed = path(word);
        // two letters start too many rows to be worth offering
        if (typed.length() < 3) {
            return Collections.emptyList();
        }
        Set<String> targets = new LinkedHashSet<>(4);
        collectPrefix(PAIRS, typed, targets);
        collectPrefix(GAMERULES, typed, targets);
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }
        String[] wanted = targets.toArray(new String[0]);

        List<String> out = new ArrayList<>(2);
        for (String option : pool) {
            if (option == null) {
                continue;
            }
            if (isNamed(option, typed)) {
                return Collections.emptyList();
            }
            for (String target : wanted) {
                if (isNamed(option, target)) {
                    if (!out.contains(option)) {
                        out.add(option);
                    }
                    break;
                }
            }
        }
        return out;
    }

    private static void collectPrefix(String[][] table, String typed, Set<String> targets) {
        for (String[] pair : table) {
            if (pair[0].length() > typed.length() && pair[0].startsWith(typed)) {
                targets.add(pair[1]);
            }
        }
    }

    private static void collect(String[][] table, String typed, Set<String> targets) {
        for (String[] pair : table) {
            if (pair[0].equals(typed)) {
                targets.add(pair[1]);
            }
        }
    }

    private static boolean hasKey(String[][] table, String typed) {
        for (String[] pair : table) {
            if (pair[0].equals(typed)) {
                return true;
            }
        }
        return false;
    }

    // 0 to 3 are keys in both tables, so one walk answers both: the list itself says which
    // command it belongs to, and a list that is neither leaves the number alone
    private static void collectNumbered(Collection<String> pool, String typed, Set<String> targets) {
        boolean[] gamemode = new boolean[GAMEMODE_MARKS.length];
        boolean[] difficulty = new boolean[DIFFICULTY_MARKS.length];
        for (String option : pool) {
            if (option == null) {
                continue;
            }
            mark(option, GAMEMODE_MARKS, gamemode);
            mark(option, DIFFICULTY_MARKS, difficulty);
        }
        if (all(gamemode)) {
            collect(GAMEMODE, typed, targets);
        }
        if (all(difficulty)) {
            collect(DIFFICULTY, typed, targets);
        }
    }

    private static void mark(String option, String[] marks, boolean[] seen) {
        for (int i = 0; i < marks.length; i++) {
            if (!seen[i] && isNamed(option, marks[i])) {
                seen[i] = true;
                return;
            }
        }
    }

    private static boolean all(boolean[] seen) {
        for (boolean one : seen) {
            if (!one) {
                return false;
            }
        }
        return true;
    }

    // compares without cutting the string: this runs once per option on every keystroke
    private static boolean isNamed(String option, String target) {
        int from = option.indexOf(':') + 1;
        return option.length() - from == target.length()
                && option.regionMatches(true, from, target, 0, target.length());
    }

    private static String path(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }
}
