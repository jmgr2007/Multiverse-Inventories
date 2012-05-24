package com.onarandombox.multiverseinventories.util.data;

import com.feildmaster.lib.configuration.EnhancedConfiguration;
import com.onarandombox.multiverseinventories.ProfileTypes;
import com.onarandombox.multiverseinventories.api.Inventories;
import com.onarandombox.multiverseinventories.api.profile.ContainerType;
import com.onarandombox.multiverseinventories.api.profile.GlobalProfile;
import com.onarandombox.multiverseinventories.api.profile.PlayerData;
import com.onarandombox.multiverseinventories.api.profile.PlayerProfile;
import com.onarandombox.multiverseinventories.api.profile.ProfileType;
import com.onarandombox.multiverseinventories.util.Logging;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of PlayerData.
 */
public class FlatFilePlayerData implements PlayerData {

    private static final String YML = ".yml";
    private File worldFolder = null;
    private File groupFolder = null;
    private File playerFolder = null;
    private Inventories inventories;

    public FlatFilePlayerData(Inventories plugin) throws IOException {
        this.inventories = plugin;
        // Make the data folders
        plugin.getDataFolder().mkdirs();

        // Check if the data file exists.  If not, create it.
        this.worldFolder = new File(plugin.getDataFolder(), "worlds");
        if (!this.worldFolder.exists()) {
            if (!this.worldFolder.mkdirs()) {
                throw new IOException("Could not create world folder!");
            }
        }
        this.groupFolder = new File(plugin.getDataFolder(), "groups");
        if (!this.groupFolder.exists()) {
            if (!this.groupFolder.mkdirs()) {
                throw new IOException("Could not create group folder!");
            }
        }
        this.playerFolder = new File(plugin.getDataFolder(), "players");
        if (!this.playerFolder.exists()) {
            if (!this.playerFolder.mkdirs()) {
                throw new IOException("Could not create player folder!");
            }
        }
    }

    private FileConfiguration getConfigHandle(File file) {
        return EnhancedConfiguration.loadConfiguration(file);
    }

    private File getFolder(ContainerType type, String folderName) {
        File folder;
        switch (type) {
            case GROUP:
                folder = new File(this.groupFolder, folderName);
                break;
            case WORLD:
                folder = new File(this.worldFolder, folderName);
                break;
            default:
                folder = new File(this.worldFolder, folderName);
                break;
        }

        if (!folder.exists()) {
            folder.mkdirs();
        }
        Logging.finer("got data folder: " + folder.getPath() + " from type: " + type);
        return folder;
    }

    /**
     * Retrieves the yaml data file for a player based on a given world/group name.
     *
     * @param type       Indicates whether data is for group or world.
     * @param dataName   The name of the group or world.
     * @param playerName The name of the player.
     * @return The yaml data file for a player.
     */
    File getPlayerFile(ContainerType type, String dataName, String playerName) {
        File playerFile = new File(this.getFolder(type, dataName), playerName + YML);
        if (!playerFile.exists()) {
            try {
                playerFile.createNewFile();
            } catch (IOException e) {
                Logging.severe("Could not create necessary player file: " + playerName + YML);
                Logging.severe("Your data may not be saved!");
                Logging.severe(e.getMessage());
            }
        }
        return playerFile;
    }

    /**
     * Retrieves the yaml data file for a player for their global data.
     *
     * @param playerName The name of the player.
     * @return The yaml data file for a player.
     */
    File getGlobalFile(String playerName) {
        File playerFile = new File(playerFolder, playerName + YML);
        if (!playerFile.exists()) {
            try {
                playerFile.createNewFile();
            } catch (IOException e) {
                Logging.severe("Could not create necessary player file: " + playerName + YML);
                Logging.severe("Your data may not be saved!");
                Logging.severe(e.getMessage());
            }
        }
        return playerFile;
    }

    private String getPlayerName(File playerFile) {
        if (playerFile.getName().endsWith(YML)) {
            String fileName = playerFile.getName();
            return fileName.substring(0, fileName.length() - YML.length());
        } else {
            return null;
        }
    }

    /*
    private File[] getWorldFolders() {
        return this.worldFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(YML);
            }
        });
    }
    */

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updatePlayerData(PlayerProfile playerProfile) {
        File playerFile = this.getPlayerFile(playerProfile.getContainerType(),
                playerProfile.getContainerName(), playerProfile.getPlayer().getName());
        FileConfiguration playerData = this.getConfigHandle(playerFile);
        playerData.createSection(playerProfile.getProfileType().getName(), playerProfile.serialize());
        try {
            playerData.save(playerFile);
        } catch (IOException e) {
            Logging.severe("Could not save data for player: " + playerProfile.getPlayer().getName()
                    + " for " + playerProfile.getContainerType().toString() + ": " + playerProfile.getContainerName());
            Logging.severe(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlayerProfile getPlayerData(ContainerType containerType, String dataName, ProfileType profileType, String playerName) {
        File playerFile = this.getPlayerFile(containerType, dataName, playerName);
        FileConfiguration playerData = this.getConfigHandle(playerFile);
        convertConfig(playerData);
        ConfigurationSection section = playerData.getConfigurationSection(profileType.getName());
        if (section == null) {
            section = playerData.createSection(profileType.getName());
        }
        return new DefaultPlayerProfile(containerType, dataName, profileType, playerName, convertSection(section));
    }

    private void convertConfig(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("playerData");
        if (section != null) {
            config.set(ProfileTypes.DEFAULT.getName(), section);
            config.set("playerData", null);
            Logging.finer("Migrated old player data to new multi-profile format");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removePlayerData(ContainerType containerType, String dataName, ProfileType profileType, String playerName) {
        if (profileType == null) {
            File playerFile = this.getPlayerFile(containerType, dataName, playerName);
            return playerFile.delete();
        } else {
            File playerFile = this.getPlayerFile(containerType, dataName, playerName);
            FileConfiguration playerData = this.getConfigHandle(playerFile);
            playerData.set(profileType.getName(), null);
            try {
                playerData.save(playerFile);
            } catch (IOException e) {
                Logging.severe("Could not delete data for player: " + playerName
                        + " for " + containerType.toString() + ": " + dataName);
                Logging.severe(e.getMessage());
                return false;
            }
            return true;
        }
    }

    private Map<String, Object> convertSection(ConfigurationSection section) {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        for (String key : section.getKeys(false)) {
            Object obj = section.get(key);
            if (obj instanceof ConfigurationSection) {
                resultMap.put(key, convertSection((ConfigurationSection) obj));
            } else {
                resultMap.put(key, obj);
            }
        }
        return resultMap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GlobalProfile getGlobalProfile(String playerName) {
        File playerFile = this.getGlobalFile(playerName);
        FileConfiguration playerData = this.getConfigHandle(playerFile);
        ConfigurationSection section = playerData.getConfigurationSection("playerData");
        if (section == null) {
            section = playerData.createSection("playerData");
        }
        return new DefaultGlobalProfile(playerName, convertSection(section));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateGlobalProfile(String playerName, GlobalProfile globalProfile) {
        File playerFile = this.getGlobalFile(playerName);
        FileConfiguration playerData = this.getConfigHandle(playerFile);
        playerData.createSection("playerData", globalProfile.serialize());
        try {
            playerData.save(playerFile);
        } catch (IOException e) {
            Logging.severe("Could not save global data for player: " + globalProfile.getName());
            Logging.severe(e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public void updateWorld(String playerName, String worldName) {
        GlobalProfile globalProfile = getGlobalProfile(playerName);
        globalProfile.setWorld(worldName);
        updateGlobalProfile(playerName, globalProfile);
    }

    @Override
    public void updateProfileType(String playerName, ProfileType profileType) {
        GlobalProfile globalProfile = getGlobalProfile(playerName);
        globalProfile.setProfileType(profileType);
        updateGlobalProfile(playerName, globalProfile);
    }
}

