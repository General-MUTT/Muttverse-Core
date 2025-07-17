/******************************************************************************
 * Multiverse 2 Copyright (c) the Multiverse Team 2011.                       *
 * Multiverse 2 is licensed under the BSD License.                            *
 * For more information please check the README.md file included              *
 * with this project.                                                         *
 ******************************************************************************/

package org.mvplugins.multiverse.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.dumptruckman.minecraft.util.Logging;
import io.vavr.control.Try;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;
import java.util.stream.Collectors;

import org.mvplugins.multiverse.core.anchor.AnchorManager;
import org.mvplugins.multiverse.core.destination.Destination;
import org.mvplugins.multiverse.core.destination.DestinationsProvider;
import org.mvplugins.multiverse.core.commands.CoreCommand;
import org.mvplugins.multiverse.core.config.CoreConfig;
import org.mvplugins.multiverse.core.economy.MVEconomist;
import org.mvplugins.multiverse.core.listeners.CoreListener;
import org.mvplugins.multiverse.core.inject.PluginServiceLocatorFactory;
import org.mvplugins.multiverse.core.module.MultiverseModule;
import org.mvplugins.multiverse.core.utils.StringFormatter;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.location.NullSpawnLocation;
import org.mvplugins.multiverse.core.world.location.SpawnLocation;
import org.mvplugins.multiverse.core.world.location.UnloadedWorldLocation;

/**
 * The start of the Multiverse-Core plugin
 */
@Service
public class MultiverseCore extends MultiverseModule {

    @Inject
    private Provider<CoreConfig> configProvider;
    @Inject
    private Provider<WorldManager> worldManagerProvider;
    @Inject
    private Provider<AnchorManager> anchorManagerProvider;
    @Inject
    private Provider<DestinationsProvider> destinationsProviderProvider;
    @Inject
    private Provider<BstatsMetricsConfigurator> metricsConfiguratorProvider;
    @Inject
    private Provider<MVEconomist> economistProvider;

    /**
     * This is the constructor for the MultiverseCore.
     */
    public MultiverseCore() {
        super();
    }

    @Override
    public void onLoad() {
        // Setup our Logging
        Logging.init(this);

        // Create our DataFolder
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            Logging.severe("Failed to create data folder!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register our config classes
        ConfigurationSerialization.registerClass(NullSpawnLocation.class);
        ConfigurationSerialization.registerClass(SpawnLocation.class);
        ConfigurationSerialization.registerClass(UnloadedWorldLocation.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEnable() {
        super.onEnable();
        initializeDependencyInjection(new MultiverseCorePluginBinder(this));

        // Load our configs first as we need them for everything else.
        var config = configProvider.get();
        var loadSuccess = config.load().andThenTry(config::save).isSuccess();
        if (!loadSuccess || !config.isLoaded()) {
            Logging.severe("Your configs were not loaded.");
            Logging.severe("Please check your configs and restart the server.");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Logging.setShowingConfig(shouldShowConfig());

        // Initialize the worlds
        worldManagerProvider.get().initAllWorlds().andThenTry(() -> {
            loadEconomist(); // Setup economy here so vault is loaded
            loadAnchors();
            registerDynamicListeners(CoreListener.class);
            setUpLocales();
            registerCommands(CoreCommand.class);


                        //Tab Completion with comma support
            var commandManager = commandManagerProvider.get();

            commandManager.getCommandCompletions().registerAsyncCompletion("purgeTypes", context -> {
                String input = context.getInput().toLowerCase();

                Set<String> baseTypes = new HashSet<>(List.of("all", "monsters", "animals"));
                for (EntityType type : EntityType.values()) {
                    if (type.isAlive() && type.isSpawnable()) {
                        baseTypes.add(type.name().toLowerCase());
                    }
                }

                if (!input.contains(",")) {
                    return baseTypes.stream()
                            .filter(s -> s.startsWith(input))
                            .sorted()
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                }

                String[] parts = input.split(",");
                String last = parts[parts.length - 1];
                Set<String> alreadyTyped = new HashSet<>(Arrays.asList(parts));
                alreadyTyped.remove(last);

                return baseTypes.stream()
                        .filter(type -> type.startsWith(last) && !alreadyTyped.contains(type))
                        .sorted()
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            });

            registerDestinations();
            setupMetrics();
            loadPlaceholderApiIntegration();
            loadApiService();
            saveAllConfigs();
            logEnableMessage();
        }).onFailure(e -> {
            Logging.severe("Failed to multiverse core! Disabling...");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDisable() {
        super.onDisable();
        MultiverseCoreApi.shutdown();
        saveAllConfigs();
        shutdownDependencyInjection();
        PluginServiceLocatorFactory.get().shutdown();
        Logging.shutdown();
    }

    private boolean shouldShowConfig() {
        return !configProvider.get().getSilentStart();
    }

    private void loadEconomist() {
        Try.run(() -> economistProvider.get())
                .onFailure(e -> {
                    Logging.severe("Failed to load economy integration");
                    e.printStackTrace();
                });
    }

    private void loadAnchors() {
        Try.of(() -> anchorManagerProvider.get())
                .flatMap(AnchorManager::loadAnchors)
                .onFailure(e -> {
                    Logging.severe("Failed to load anchors");
                    e.printStackTrace();
                });
    }

    /**
     * Register all the destinations.
     */
    private void registerDestinations() {
        Try.of(() -> destinationsProviderProvider.get())
                .andThenTry(destinationsProvider -> {
                    serviceLocator.getAllServices(Destination.class)
                            .forEach(destinationsProvider::registerDestination);
                })
                .onFailure(e -> {
                    Logging.severe("Failed to register destinations");
                    e.printStackTrace();
                });
    }

    /**
     * Setup bstats Metrics.
     */
    private void setupMetrics() {
        if (TestingMode.isDisabled()) {
            // Load metrics
            Try.of(() -> metricsConfiguratorProvider.get())
                    .onFailure(e -> {
                        Logging.severe("Failed to setup metrics");
                        e.printStackTrace();
                    });
        } else {
            Logging.info("Metrics are disabled in testing mode.");
        }
    }

    /**
     * Setup placeholder api hook
     */
    private void loadPlaceholderApiIntegration() {
        if (configProvider.get().isRegisterPapiHook()
                && getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Try.run(() -> serviceLocator.getService(PlaceholderExpansionHook.class))
                    .onFailure(e -> {
                        Logging.severe("Failed to load PlaceholderAPI integration.");
                        e.printStackTrace();
                    });
        }
    }

    /**
     * Setup the api service for {@link MultiverseCoreApi}
     */
    private void loadApiService() {
        Try.run(() -> MultiverseCoreApi.init(this))
                .onSuccess(ignore -> Logging.info("API service loaded!"))
                .onFailure(e -> {
                    Logging.severe("Failed to load API service!");
                    e.printStackTrace();
                });
    }

    /**
     * Save config.yml, worlds.yml, and anchors.yml.
     *
     * @return {@link Try#isSuccess()} true if all configs were successfully saved
     */
    private Try<Void> saveAllConfigs() {
        return configProvider.get().save()
                .flatMap(ignore -> worldManagerProvider.get().saveWorldsConfig())
                .flatMap(ignore ->anchorManagerProvider.get().saveAllAnchors())
                .onFailure(e -> {
                    Logging.severe("Failed to save configs, things may not work as expected.");
                });
    }

    /**
     * Logs the enable message.
     */
    private void logEnableMessage() {
        Logging.config("\u001B[32mVersion %s (API v%s) Enabled - By %s\u001B[39m",
                this.getDescription().getVersion(), getVersionAsNumber(), StringFormatter.joinAnd(getDescription().getAuthors()));

        if (configProvider.get().isShowingDonateMessage()) {
            Logging.config("\u001B[32mLoving Multiverse-Core? Please consider supporting the project with a small donation: https://github.com/sponsors/Multiverse\u001B[39m");
        }
    }

    /**
     * Gets the MultiverseCoreApi
     *
     * @return The MultiverseCoreApi
     *
     * @deprecated Use {@link MultiverseCoreApi#get()} directly.
     */
    @Deprecated(since = "5.1", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "6.0")
    public MultiverseCoreApi getApi() {
        return MultiverseCoreApi.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getTargetCoreVersion() {
        return getVersionAsNumber();
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Override
    public Logger getLogger() {
        return Logging.getLogger();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NotNull FileConfiguration getConfig() {
        CoreConfig coreConfig = this.configProvider.get();
        var config = coreConfig.getConfig();
        if (config != null && coreConfig.isLoaded()) {
            return config;
        }

        var loadSuccess = coreConfig.load().isSuccess();
        if (!loadSuccess || !coreConfig.isLoaded()) {
            throw new RuntimeException("Failed to load configs");
        }
        return coreConfig.getConfig();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reloadConfig() {
        this.configProvider.get().load();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveConfig() {
        this.configProvider.get().save();
    }
}
