package com.bgsoftware.superiorskyblock.api.service.portals;

import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Location;

/**
 * Service for handling island lobby portals (nether portals that send players to lobby server)
 */
public interface IslandLobbyPortalService {

    /**
     * Send a player through the island lobby portal to the configured lobby server
     * @param player the player to send
     */
    void sendPlayerToLobby(SuperiorPlayer player);

    /**
     * Check if island lobby portals are enabled
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Get the destination server for lobby portals
     * @return the destination server name (e.g., "lobby")
     */
    String getDestinationServer();

    /**
     * Check if at least one schematic has a protected region configured.
     * Used as a fast early-exit guard before doing per-island checks.
     * @return true if any region is saved, false if nothing has been set up yet
     */
    boolean hasProtectedRegion();

    /**
     * Check whether a block location falls inside the protected portal region for a given island schematic.
     * Coordinates are compared as offsets from the island center, so the same region applies to every
     * island of the same schematic type.
     * Returns false if no region has been configured for this schematic name.
     * @param schematicName the schematic name of the island (from island.getSchematicName())
     * @param islandCenter  the center location of the island
     * @param blockLocation the block location to test
     * @return true if the block is inside the protected region for this schematic
     */
    boolean isInProtectedRegion(String schematicName, Location islandCenter, Location blockLocation);

    /**
     * Save a new protected region (relative offsets from island center) for a specific schematic
     * and persist it to disk. Called by /is admin setportalregion after the admin makes a wand selection.
     * @param schematicName the schematic name to associate this region with
     */
    void setProtectedRegion(String schematicName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
}
